BEGIN;
CREATE SCHEMA kanta_diagnostics;
REVOKE ALL ON SCHEMA kanta_diagnostics FROM PUBLIC;

CREATE TABLE kanta_diagnostics.installations (
  id text PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
  credential text NOT NULL CHECK (credential ~ '^[0-9a-f]{64}$'),
  last_receipt timestamptz NOT NULL,
  removed boolean NOT NULL DEFAULT false
);
CREATE INDEX installations_expiry ON kanta_diagnostics.installations (last_receipt);
CREATE TABLE kanta_diagnostics.activity (
  installation text NOT NULL REFERENCES kanta_diagnostics.installations (id) ON DELETE CASCADE,
  utc_day date NOT NULL,
  receipt timestamptz NOT NULL,
  PRIMARY KEY (installation, utc_day)
);
CREATE INDEX activity_expiry ON kanta_diagnostics.activity (receipt);
CREATE TABLE kanta_diagnostics.crashes (
  installation text NOT NULL REFERENCES kanta_diagnostics.installations (id) ON DELETE CASCADE,
  marker text NOT NULL CHECK (marker ~ '^[0-9a-f]{64}$'),
  receipt timestamptz NOT NULL,
  PRIMARY KEY (installation, marker)
);
CREATE INDEX crashes_expiry ON kanta_diagnostics.crashes (receipt);
CREATE TABLE kanta_diagnostics.throttle (
  scope text NOT NULL CHECK (scope = 'global' OR scope ~ '^[isn]:[0-9a-f]{64}$'),
  bucket timestamptz NOT NULL,
  hits integer NOT NULL CHECK (hits > 0),
  PRIMARY KEY (scope, bucket)
);
CREATE INDEX throttle_expiry ON kanta_diagnostics.throttle (bucket);
CREATE TABLE kanta_diagnostics.maintenance (
  singleton boolean PRIMARY KEY CHECK (singleton),
  last_purge timestamptz NOT NULL
);

-- One transaction-level lock deliberately serializes this small launch deployment.
-- No instance-local counters, no partial mutation, no rate reset on cold starts.
CREATE FUNCTION kanta_diagnostics.purge() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE t timestamptz;
BEGIN
  PERFORM pg_advisory_xact_lock(19903031, 34);
  t := clock_timestamp();
  -- Leave 15 minutes for the external scheduler before the 30-day ceiling.
  DELETE FROM kanta_diagnostics.installations WHERE last_receipt <= t - interval '719 hours 45 minutes';
  DELETE FROM kanta_diagnostics.activity WHERE receipt <= t - interval '719 hours 45 minutes';
  DELETE FROM kanta_diagnostics.crashes WHERE receipt <= t - interval '719 hours 45 minutes';
  DELETE FROM kanta_diagnostics.throttle WHERE bucket <= t - interval '15 minutes';
  INSERT INTO kanta_diagnostics.maintenance VALUES (true, clock_timestamp())
    ON CONFLICT (singleton) DO UPDATE SET last_purge = excluded.last_purge;
END;
$$;

-- Counters never exceed their cap. The global scope is always checked before
-- creating keyed rows, bounding cardinality even when callers churn identities.
CREATE FUNCTION kanta_diagnostics.allow_hit(s text, b timestamptz, cap integer) RETURNS boolean
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
  INSERT INTO kanta_diagnostics.throttle AS h VALUES (s, b, 1)
    ON CONFLICT (scope, bucket) DO UPDATE SET hits = h.hits + 1 WHERE h.hits < cap;
  RETURN FOUND;
END;
$$;

-- 0 accepted; -1 wrong credential; -2 deleted; positive seconds to retry.
CREATE FUNCTION kanta_diagnostics.mutate(do_remove boolean, i text, cred text, src text, pulse boolean, event_key text)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
  t timestamptz;
  cutoff timestamptz;
  b timestamptz;
  wait_seconds integer;
  current_row kanta_diagnostics.installations%ROWTYPE;
  changed boolean := false;
BEGIN
  IF do_remove IS NULL OR pulse IS NULL OR i IS NULL OR cred IS NULL OR src IS NULL OR
      i !~ '^[0-9a-f]{64}$' OR cred !~ '^[0-9a-f]{64}$' OR src !~ '^[0-9a-f]{64}$' OR
      (event_key IS NOT NULL AND event_key !~ '^[0-9a-f]{64}$') OR
      (do_remove AND (pulse OR event_key IS NOT NULL)) OR
      (NOT do_remove AND NOT pulse AND event_key IS NULL) THEN RAISE EXCEPTION 'Invalid diagnostics mutation'; END IF;
  PERFORM pg_advisory_xact_lock(19903031, 34);
  t := clock_timestamp();
  IF NOT EXISTS (SELECT 1 FROM kanta_diagnostics.maintenance WHERE last_purge >= t - interval '12 minutes') THEN
    RAISE EXCEPTION 'Diagnostics maintenance unavailable';
  END IF;
  cutoff := t - interval '719 hours 45 minutes';
  b := date_bin(interval '15 minutes', t, timestamptz '2000-01-01 00:00:00+00');
  wait_seconds := ceil(extract(epoch FROM (b + interval '15 minutes' - t)));
  -- Check the hard global write budget first. Scope source and installation
  -- together across POST and DELETE; additionally cap new identities per source.
  IF NOT kanta_diagnostics.allow_hit('global', b, 300) THEN RETURN wait_seconds; END IF;
  IF NOT kanta_diagnostics.allow_hit('s:' || src, b, 40) THEN RETURN wait_seconds; END IF;
  IF NOT kanta_diagnostics.allow_hit('i:' || i, b, 8) THEN RETURN wait_seconds; END IF;

  SELECT * INTO current_row FROM kanta_diagnostics.installations WHERE id = i;
  IF FOUND AND current_row.last_receipt <= cutoff THEN
    DELETE FROM kanta_diagnostics.installations WHERE id = i;
    current_row := NULL;
  END IF;
  IF current_row.id IS NOT NULL AND current_row.credential <> cred THEN RETURN -1; END IF;
  IF do_remove THEN
    IF current_row.id IS NOT NULL AND NOT current_row.removed THEN
      DELETE FROM kanta_diagnostics.activity WHERE installation = i;
      DELETE FROM kanta_diagnostics.crashes WHERE installation = i;
      UPDATE kanta_diagnostics.installations SET removed = true, last_receipt = t WHERE id = i;
    END IF;
    RETURN 0;
  END IF;
  IF current_row.removed THEN RETURN -2; END IF;
  IF current_row.id IS NULL THEN
    IF NOT kanta_diagnostics.allow_hit('n:' || src, b, 8) THEN RETURN wait_seconds; END IF;
    INSERT INTO kanta_diagnostics.installations VALUES (i, cred, t, false);
  END IF;
  IF pulse THEN
    INSERT INTO kanta_diagnostics.activity VALUES (i, (t AT TIME ZONE 'UTC')::date, t) ON CONFLICT DO NOTHING;
    changed := FOUND;
  END IF;
  IF event_key IS NOT NULL THEN
    INSERT INTO kanta_diagnostics.crashes AS c VALUES (i, event_key, t)
      ON CONFLICT (installation, marker) DO UPDATE SET receipt = excluded.receipt WHERE c.receipt <= cutoff;
    changed := changed OR FOUND;
  END IF;
  IF changed THEN UPDATE kanta_diagnostics.installations SET last_receipt = t WHERE id = i; END IF;
  RETURN 0;
END;
$$;

CREATE FUNCTION kanta_diagnostics.metrics()
RETURNS TABLE (daily_active bigint, monthly_active bigint, daily_crashes bigint, monthly_crashes bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $$
  WITH bounds AS (
    SELECT statement_timestamp() AS t,
           date_trunc('day', statement_timestamp(), 'UTC') AS today,
           ((statement_timestamp() AT TIME ZONE 'UTC')::date - 29)::timestamp AT TIME ZONE 'UTC' AS first_day
  ), active_counts AS (
    SELECT count(DISTINCT installation) FILTER (WHERE receipt >= today) AS daily_active,
           count(DISTINCT installation) AS monthly_active
    FROM kanta_diagnostics.activity, bounds WHERE receipt >= first_day AND receipt <= t
  ), crash_counts AS (
    SELECT count(*) FILTER (WHERE receipt >= today) AS daily_crashes, count(*) AS monthly_crashes
    FROM kanta_diagnostics.crashes, bounds WHERE receipt >= first_day AND receipt <= t
  )
  SELECT daily_active, monthly_active, daily_crashes, monthly_crashes
  FROM active_counts, crash_counts, bounds
  WHERE EXISTS (SELECT 1 FROM kanta_diagnostics.maintenance WHERE last_purge >= t - interval '12 minutes');
$$;

REVOKE ALL ON ALL TABLES IN SCHEMA kanta_diagnostics FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA kanta_diagnostics FROM PUBLIC;
COMMIT;
