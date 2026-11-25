-- =============================================================================
-- MUVANTA — Migration 001: Initial Relational Schema
-- Safe to run sequentially in Supabase PostgreSQL (non-destructive).
-- =============================================================================

create extension if not exists pgcrypto;

-- -----------------------------------------------------------------------------
-- A. User Accounts & Preferences (identity backed by auth.users)
-- -----------------------------------------------------------------------------
create table if not exists public.user_profiles (
    user_id               uuid primary key,
    username              text,
    display_name          text not null default 'Cinema Archivist',
    tagline               text not null default '',
    avatar_url            text not null default '',
    default_discover_mode text not null default 'grid',
    is_profile_public     boolean not null default false,
    role                  text not null default 'user',
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- B. Shared Entertainment Catalogue (globally readable, admin/service writable)
-- -----------------------------------------------------------------------------
create table if not exists public.titles (
    id                text primary key,
    media_type        text not null,
    archive_rank      integer,
    tier_id           text,
    title             text not null,
    release_year      smallint,
    is_anime          boolean not null default false,
    is_documentary    boolean not null default false,
    director          text not null default '',
    country           text not null default '',
    original_language text not null default '',
    runtime_label     text not null default '',
    runtime_minutes   integer,
    network           text not null default '',
    synopsis          text not null default '',
    poster_url        text not null default '',
    wiki_url          text not null default '',
    tvmaze_show_id    integer,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create table if not exists public.genres (
    id   smallserial primary key,
    slug text not null unique,
    name text not null unique
);

create table if not exists public.moods (
    id       smallserial primary key,
    slug     text not null unique,
    name     text not null unique,
    subtitle text not null default ''
);

create table if not exists public.title_genres (
    title_id text not null,
    genre_id smallint not null,
    primary key (title_id, genre_id)
);

create table if not exists public.title_moods (
    title_id text not null,
    mood_id  smallint not null,
    primary key (title_id, mood_id)
);

create table if not exists public.episodes (
    id              uuid primary key default gen_random_uuid(),
    title_id        text not null,
    season_number   smallint not null,
    episode_number  smallint not null,
    episode_title   text not null default '',
    air_date        date,
    runtime_minutes smallint,
    summary         text not null default '',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- C. Personal Entertainment Library (strictly user-owned & private)
-- -----------------------------------------------------------------------------
create table if not exists public.user_library_entries (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null,
    title_id          text not null,
    status            text not null default 'unwatched',
    personal_rating   smallint,
    private_notes     text not null default '',
    is_favorite       boolean not null default false,
    rewatch_count     integer not null default 0,
    custom_poster_url text not null default '',
    sync_version      integer not null default 1,
    is_deleted        boolean not null default false,
    deleted_at        timestamptz,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create table if not exists public.viewing_logs (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null,
    title_id       text not null,
    client_log_key text not null,
    logged_at      timestamptz not null default now(),
    action_label   text not null,
    rating_at_log  smallint,
    is_rewatch     boolean not null default false,
    note           text not null default '',
    created_at     timestamptz not null default now()
);

create table if not exists public.user_watched_episodes (
    user_id        uuid not null,
    title_id       text not null,
    season_number  smallint not null,
    episode_number smallint not null,
    watched_at     timestamptz not null default now(),
    primary key (user_id, title_id, season_number, episode_number)
);

create table if not exists public.user_tags (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null,
    name       text not null,
    created_at timestamptz not null default now()
);

create table if not exists public.user_title_tags (
    user_id    uuid not null,
    title_id   text not null,
    tag_id     uuid not null,
    created_at timestamptz not null default now(),
    primary key (user_id, title_id, tag_id)
);

-- -----------------------------------------------------------------------------
-- D. Reviews (separated from private notes to support public/private visibility)
-- -----------------------------------------------------------------------------
create table if not exists public.reviews (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null,
    title_id          text not null,
    rating            smallint,
    body              text not null default '',
    contains_spoilers boolean not null default false,
    visibility        text not null default 'private',
    sync_version      integer not null default 1,
    is_deleted        boolean not null default false,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- E. Custom Collections & Ordered Collection Items
-- -----------------------------------------------------------------------------
create table if not exists public.collections (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null,
    client_id        text not null,
    name             text not null,
    description      text not null default '',
    cover_poster_url text not null default '',
    visibility       text not null default 'private',
    sync_version     integer not null default 1,
    is_deleted       boolean not null default false,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create table if not exists public.collection_items (
    collection_id uuid not null,
    title_id      text not null,
    position      integer not null default 0,
    added_at      timestamptz not null default now(),
    primary key (collection_id, title_id)
);

-- -----------------------------------------------------------------------------
-- F. Offline-First Idempotent Sync Ledger
-- -----------------------------------------------------------------------------
create table if not exists public.sync_op_ledger (
    user_id      uuid not null,
    op_id        text not null,
    entity_type  text not null default '',
    processed_at timestamptz not null default now(),
    primary key (user_id, op_id)
);
