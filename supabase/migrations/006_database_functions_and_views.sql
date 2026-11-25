-- =============================================================================
-- MUVANTA — Migration 006: Derived Statistics Views & Community Rating Summary
-- Dynamically calculated from authoritative records (no redundant counters).
-- =============================================================================

-- 1. Personal Library Statistics View (security_invoker = true enforces caller RLS)
create or replace view public.v_user_library_summary
with (security_invoker = true) as
select
    e.user_id,
    count(*) filter (where e.status = 'watched' and t.media_type = 'movie')      as watched_movies,
    count(*) filter (where e.status = 'watched' and t.media_type = 'series')     as watched_series,
    count(*) filter (where e.status = 'watched' and t.is_anime = true)           as watched_anime,
    count(*) filter (where e.status = 'watched' and t.is_documentary = true)     as watched_docs,
    count(*) filter (where e.status = 'watching')                                as watching_count,
    count(*) filter (where e.status = 'rewatching')                              as rewatching_count,
    count(*) filter (where e.status = 'bookmarked')                              as bookmarked_count,
    count(*) filter (where e.status = 'on_hold')                                 as on_hold_count,
    count(*) filter (where e.status = 'dropped')                                 as dropped_count,
    count(*) filter (where e.is_favorite = true)                                 as favorites_count,
    coalesce(sum(e.rewatch_count), 0)                                            as total_rewatches,
    count(*) filter (where e.personal_rating is not null)                        as rated_count,
    round(avg(e.personal_rating)::numeric, 2)                                    as average_rating
from public.user_library_entries e
join public.titles t on t.id = e.title_id
where e.is_deleted = false
group by e.user_id;

-- 2. Public Community Ratings View (derived strictly from public active reviews;
--    never exposes private personal ratings or private notes)
create or replace view public.v_title_community_ratings
with (security_invoker = true) as
select
    r.title_id,
    count(*) filter (where r.rating is not null)      as community_rating_count,
    round(avg(r.rating)::numeric, 2)                  as community_average_rating,
    count(*) filter (where char_length(trim(r.body)) > 0) as public_review_count
from public.reviews r
where r.visibility = 'public'
  and r.is_deleted = false
group by r.title_id;

-- 3. Seed standard MUVANTA moods & core genres idempotently
insert into public.moods (slug, name, subtitle) values
    ('feel_good',         'Feel-Good',         'Warm, joyful stories'),
    ('emotional',         'Emotional',         'Deeply moving cinema'),
    ('heartbreaking',     'Heartbreaking',     'Unforgettable tragedies'),
    ('motivational',      'Motivational',      'Inspiring human grit'),
    ('mind_bending',      'Mind-Bending',      'Complex twists & puzzles'),
    ('relaxing',          'Relaxing',          'Calm & atmospheric'),
    ('dark',              'Dark',              'Gritty & intense'),
    ('romantic',          'Romantic',          'Love & longing'),
    ('thrilling',         'Thrilling',         'High-stakes suspense'),
    ('thought_provoking', 'Thought-Provoking', 'Philosophical & bold')
on conflict (slug) do nothing;

insert into public.genres (slug, name) values
    ('drama',       'Drama'),
    ('crime',       'Crime'),
    ('sci_fi',      'Sci-Fi'),
    ('thriller',    'Thriller'),
    ('comedy',      'Comedy'),
    ('action',      'Action'),
    ('adventure',   'Adventure'),
    ('animation',   'Animation'),
    ('romance',     'Romance'),
    ('horror',      'Horror'),
    ('war',         'War'),
    ('documentary', 'Documentary'),
    ('mystery',     'Mystery'),
    ('biography',   'Biography'),
    ('fantasy',     'Fantasy')
on conflict (slug) do nothing;
