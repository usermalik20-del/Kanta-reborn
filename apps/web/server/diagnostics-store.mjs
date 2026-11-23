import pg from 'pg';
import { configFromEnv, createHandlers } from './diagnostics.mjs';

export function postgresStore(db) {
  return {
    async mutate(remove, { installation, credential, source, active, event }) {
      const { rows } = await db.query('SELECT kanta_diagnostics.mutate($1,$2,$3,$4,$5,$6) AS result',
        [remove, installation, credential, source, active, event]);
      return rows[0]?.result;
    },
    async metrics() {
      const { rows } = await db.query('SELECT * FROM kanta_diagnostics.metrics()');
      return rows[0] ?? null;
    },
  };
}

let pool;
export const handlers = createHandlers(() => configFromEnv(process.env), ({ dbUrl }) => {
  if (!pool) {
    pool = new pg.Pool({
      connectionString: dbUrl,
      ssl: { rejectUnauthorized: true },
      max: 2,
      connectionTimeoutMillis: 2500,
      idleTimeoutMillis: 5000,
      statement_timeout: 3000,
      query_timeout: 4000,
      allowExitOnIdle: true,
    });
    // Database/provider errors can include connection details. Never log them.
    pool.on('error', () => {});
  }
  return postgresStore(pool);
});
