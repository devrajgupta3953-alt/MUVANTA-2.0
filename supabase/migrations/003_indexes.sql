-- =============================================================================
-- MUVANTA — Migration 003: Query & Synchronisation Indexes
-- =============================================================================

-- Profiles: case-insensitive unique username lookup
create unique index if not exists idx_user_profiles_username_lower
    on public.user_profiles (lower(username))
    where username is not null;

-- Shared Catalogue: discovery filters & sorting
create index if not exists idx_titles_media_rank
    on public.titles (media_type, archive_rank nulls last);

create index if not exists idx_titles_release_year
    on public.titles (release_year desc nulls last);

create index if not exists idx_titles_tier
    on public.titles (tier_id)
    where tier_id is not null;

create index if not exists idx_titles_anime_doc
    on public.titles (is_anime, is_documentary);

create index if not exists idx_title_genres_genre
    on public.title_genres (genre_id, title_id);

create index if not exists idx_title_moods_mood
    on public.title_moods (mood_id, title_id);

create index if not exists idx_episodes_title_season
    on public.episodes (title_id, season_number, episode_number);

-- Personal Library: fast delta sync, status filtering, favourites & ratings
create index if not exists idx_user_library_sync
    on public.user_library_entries (user_id, updated_at desc);

create index if not exists idx_user_library_status_active
    on public.user_library_entries (user_id, status)
    where is_deleted = false;

create index if not exists idx_user_library_favorites
    on public.user_library_entries (user_id, updated_at desc)
    where is_favorite = true and is_deleted = false;

-- Viewing History: per-title and recent chronological timelines
create index if not exists idx_viewing_logs_user_recent
    on public.viewing_logs (user_id, logged_at desc);

create index if not exists idx_viewing_logs_user_title
    on public.viewing_logs (user_id, title_id, logged_at desc);

-- Watched Episodes: fast per-series progress counts
create index if not exists idx_watched_episodes_user_title
    on public.user_watched_episodes (user_id, title_id);

-- Personal Tags: case-insensitive uniqueness per user + fast lookup by tag
create unique index if not exists idx_user_tags_user_name_lower
    on public.user_tags (user_id, lower(trim(name)));

create index if not exists idx_user_title_tags_tag
    on public.user_title_tags (tag_id, title_id);

-- Reviews: public community ratings aggregation & per-user lookup
create index if not exists idx_reviews_public_title
    on public.reviews (title_id, updated_at desc)
    where visibility = 'public' and is_deleted = false;

create index if not exists idx_reviews_user_updated
    on public.reviews (user_id, updated_at desc);

-- Collections: user listing, public discovery, and ordered items
create index if not exists idx_collections_user_active
    on public.collections (user_id, updated_at desc)
    where is_deleted = false;

create index if not exists idx_collections_public
    on public.collections (updated_at desc)
    where visibility = 'public' and is_deleted = false;

create index if not exists idx_collection_items_order
    on public.collection_items (collection_id, position asc, added_at desc);

-- Sync Op Ledger: cleanup / recent op queries
create index if not exists idx_sync_op_ledger_processed
    on public.sync_op_ledger (user_id, processed_at desc);
