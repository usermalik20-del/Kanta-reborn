-- KANTA Karaoke — songbook index schema (SQLite, on-device, single user).
-- Companion to replica/architecture.md §3.1.
-- The Meg source files (idx/kantidx, idx/kantablob) are the source of truth;
-- this database is a rebuildable cache. All writes happen on the serial IO executor.

create table if not exists volumes (
  id            integer primary key autoincrement,
  root_path     text not null unique,
  index_path    text not null,
  blob_path     text not null,
  index_bytes   integer not null,
  blob_bytes    integer not null,
  song_count    integer not null default 0,
  scanned_at    integer not null
);

create table if not exists songs (
  id            text primary key,
  volume_id     integer not null references volumes(id) on delete cascade,
  ordinal       integer not null,
  code          text not null default '',
  title         text not null,
  artist        text not null default '',
  singer        text not null default '',
  duration_ms   integer not null default 0,
  song_offset   integer not null default 0,
  song_bytes    integer not null default 0,
  lyric_offset  integer not null default 0,
  lyric_bytes   integer not null default 0,
  has_lyrics    integer not null default 0,
  has_video     integer not null default 0,
  source        text not null default 'megmid'
                  check (source in ('megmid','midi','mp3','kar')),
  updated_at    integer not null,
  unique (volume_id, ordinal)
);
create index if not exists songs_volume_ordinal on songs (volume_id, ordinal);
create index if not exists songs_artist on songs (artist);
create index if not exists songs_code on songs (code);

create table if not exists artists (
  name        text primary key,
  song_count  integer not null default 0
);

create virtual table if not exists song_search using fts4(
  title, artist, code, content='songs', content_rowid='rowid'
);

create table if not exists index_meta (
  key    text primary key,
  value  text not null
);
