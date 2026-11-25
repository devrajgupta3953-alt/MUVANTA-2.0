-- =============================================================================
-- MUVANTA — Migration 002: Foreign Keys, Unique Constraints & Check Constraints
-- =============================================================================

-- A. user_profiles
alter table public.user_profiles
    add constraint fk_user_profiles_auth_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint chk_user_profiles_discover_mode
        check (default_discover_mode in ('grid', 'shelf', 'orb')),
    add constraint chk_user_profiles_role
        check (role in ('user', 'moderator', 'admin')),
    add constraint chk_user_profiles_username_format
        check (username is null or username ~ '^[a-z0-9_]{3,30}$'),
    add constraint chk_user_profiles_display_name_len
        check (char_length(display_name) between 1 and 60),
    add constraint chk_user_profiles_tagline_len
        check (char_length(tagline) <= 160);

-- B. Shared Catalogue
alter table public.titles
    add constraint chk_titles_media_type
        check (media_type in ('movie', 'series')),
    add constraint chk_titles_release_year
        check (release_year is null or release_year between 1888 and 2100),
    add constraint chk_titles_runtime_minutes
        check (runtime_minutes is null or runtime_minutes > 0);

alter table public.title_genres
    add constraint fk_title_genres_title
        foreign key (title_id) references public.titles(id) on delete cascade,
    add constraint fk_title_genres_genre
        foreign key (genre_id) references public.genres(id) on delete cascade;

alter table public.title_moods
    add constraint fk_title_moods_title
        foreign key (title_id) references public.titles(id) on delete cascade,
    add constraint fk_title_moods_mood
        foreign key (mood_id) references public.moods(id) on delete cascade;

alter table public.episodes
    add constraint fk_episodes_title
        foreign key (title_id) references public.titles(id) on delete cascade,
    add constraint uq_episodes_title_season_episode
        unique (title_id, season_number, episode_number),
    add constraint chk_episodes_season_positive
        check (season_number > 0),
    add constraint chk_episodes_number_positive
        check (episode_number > 0);

-- C. Personal Entertainment Library
alter table public.user_library_entries
    add constraint fk_user_library_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint fk_user_library_title
        foreign key (title_id) references public.titles(id) on delete restrict,
    add constraint uq_user_library_user_title
        unique (user_id, title_id),
    add constraint chk_user_library_status
        check (status in ('unwatched', 'bookmarked', 'watching', 'watched', 'rewatching', 'on_hold', 'dropped')),
    add constraint chk_user_library_rating
        check (personal_rating is null or personal_rating between 1 and 10),
    add constraint chk_user_library_rewatch_count
        check (rewatch_count >= 0),
    add constraint chk_user_library_sync_version
        check (sync_version >= 0),
    add constraint chk_user_library_notes_len
        check (char_length(private_notes) <= 2000),
    add constraint chk_user_library_poster_https
        check (custom_poster_url = '' or custom_poster_url ~* '^https://');

alter table public.viewing_logs
    add constraint fk_viewing_logs_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint fk_viewing_logs_title
        foreign key (title_id) references public.titles(id) on delete restrict,
    add constraint uq_viewing_logs_user_client_key
        unique (user_id, client_log_key),
    add constraint chk_viewing_logs_rating
        check (rating_at_log is null or rating_at_log between 1 and 10),
    add constraint chk_viewing_logs_note_len
        check (char_length(note) <= 500);

alter table public.user_watched_episodes
    add constraint fk_watched_episodes_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint fk_watched_episodes_title
        foreign key (title_id) references public.titles(id) on delete restrict,
    add constraint chk_watched_episodes_season_positive
        check (season_number > 0),
    add constraint chk_watched_episodes_number_positive
        check (episode_number > 0);

alter table public.user_tags
    add constraint fk_user_tags_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint chk_user_tags_name_len
        check (char_length(trim(name)) between 1 and 40);

alter table public.user_title_tags
    add constraint fk_user_title_tags_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint fk_user_title_tags_title
        foreign key (title_id) references public.titles(id) on delete restrict,
    add constraint fk_user_title_tags_tag
        foreign key (tag_id) references public.user_tags(id) on delete cascade;

-- D. Reviews
alter table public.reviews
    add constraint fk_reviews_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint fk_reviews_title
        foreign key (title_id) references public.titles(id) on delete restrict,
    add constraint uq_reviews_user_title
        unique (user_id, title_id),
    add constraint chk_reviews_rating
        check (rating is null or rating between 1 and 10),
    add constraint chk_reviews_visibility
        check (visibility in ('private', 'public')),
    add constraint chk_reviews_body_len
        check (char_length(body) <= 4000),
    add constraint chk_reviews_sync_version
        check (sync_version >= 0);

-- E. Collections
alter table public.collections
    add constraint fk_collections_user
        foreign key (user_id) references auth.users(id) on delete cascade,
    add constraint uq_collections_user_client_id
        unique (user_id, client_id),
    add constraint chk_collections_name_len
        check (char_length(trim(name)) between 1 and 80),
    add constraint chk_collections_description_len
        check (char_length(description) <= 300),
    add constraint chk_collections_visibility
        check (visibility in ('private', 'unlisted', 'public')),
    add constraint chk_collections_cover_https
        check (cover_poster_url = '' or cover_poster_url ~* '^https://');

alter table public.collection_items
    add constraint fk_collection_items_collection
        foreign key (collection_id) references public.collections(id) on delete cascade,
    add constraint fk_collection_items_title
        foreign key (title_id) references public.titles(id) on delete cascade,
    add constraint chk_collection_items_position
        check (position >= 0);

-- F. Sync Ledger
alter table public.sync_op_ledger
    add constraint fk_sync_op_ledger_user
        foreign key (user_id) references auth.users(id) on delete cascade;
