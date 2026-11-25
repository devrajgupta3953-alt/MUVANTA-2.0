-- =============================================================================
-- MUVANTA — Migration 005: Row Level Security (RLS) Policies
-- Zero-trust access control enforced at the PostgreSQL layer.
-- =============================================================================

-- Helper function: checks whether the current authenticated caller is an admin
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.user_profiles
        where user_id = auth.uid()
          and role = 'admin'
    );
$$;

-- Enable RLS on every public table
alter table public.user_profiles          enable row level security;
alter table public.titles                 enable row level security;
alter table public.genres                 enable row level security;
alter table public.moods                  enable row level security;
alter table public.title_genres           enable row level security;
alter table public.title_moods            enable row level security;
alter table public.episodes               enable row level security;
alter table public.user_library_entries   enable row level security;
alter table public.viewing_logs           enable row level security;
alter table public.user_watched_episodes  enable row level security;
alter table public.user_tags              enable row level security;
alter table public.user_title_tags        enable row level security;
alter table public.reviews                enable row level security;
alter table public.collections            enable row level security;
alter table public.collection_items       enable row level security;
alter table public.sync_op_ledger         enable row level security;

-- -----------------------------------------------------------------------------
-- A. user_profiles (owner full access; public profiles readable by others)
-- -----------------------------------------------------------------------------
create policy "Profiles: select own or public"
    on public.user_profiles for select
    using (auth.uid() = user_id or is_profile_public = true);

create policy "Profiles: insert own"
    on public.user_profiles for insert
    with check (auth.uid() = user_id);

create policy "Profiles: update own"
    on public.user_profiles for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- -----------------------------------------------------------------------------
-- B. Shared Catalogue (read-only for everyone; write restricted to admins)
-- -----------------------------------------------------------------------------
create policy "Titles: public read"
    on public.titles for select using (true);
create policy "Titles: admin write"
    on public.titles for all
    using (public.is_admin())
    with check (public.is_admin());

create policy "Genres: public read"
    on public.genres for select using (true);
create policy "Genres: admin write"
    on public.genres for all
    using (public.is_admin())
    with check (public.is_admin());

create policy "Moods: public read"
    on public.moods for select using (true);
create policy "Moods: admin write"
    on public.moods for all
    using (public.is_admin())
    with check (public.is_admin());

create policy "Title genres: public read"
    on public.title_genres for select using (true);
create policy "Title genres: admin write"
    on public.title_genres for all
    using (public.is_admin())
    with check (public.is_admin());

create policy "Title moods: public read"
    on public.title_moods for select using (true);
create policy "Title moods: admin write"
    on public.title_moods for all
    using (public.is_admin())
    with check (public.is_admin());

create policy "Episodes: public read"
    on public.episodes for select using (true);
create policy "Episodes: admin write"
    on public.episodes for all
    using (public.is_admin())
    with check (public.is_admin());

-- -----------------------------------------------------------------------------
-- C. Strictly Private User Library, Viewing Logs, Watched Episodes & Tags
-- -----------------------------------------------------------------------------
create policy "Library: owner only"
    on public.user_library_entries for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Viewing logs: owner only"
    on public.viewing_logs for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Watched episodes: owner only"
    on public.user_watched_episodes for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "User tags: owner only"
    on public.user_tags for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "User title tags: owner only with owned tag"
    on public.user_title_tags for all
    using (auth.uid() = user_id)
    with check (
        auth.uid() = user_id
        and exists (
            select 1 from public.user_tags t
            where t.id = tag_id and t.user_id = auth.uid()
        )
    );

-- -----------------------------------------------------------------------------
-- D. Reviews (private reviews owner-only; public active reviews readable)
-- -----------------------------------------------------------------------------
create policy "Reviews: select own or public active"
    on public.reviews for select
    using (
        auth.uid() = user_id
        or (visibility = 'public' and is_deleted = false)
    );

create policy "Reviews: insert own"
    on public.reviews for insert
    with check (auth.uid() = user_id);

create policy "Reviews: update own"
    on public.reviews for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Reviews: delete own"
    on public.reviews for delete
    using (auth.uid() = user_id);

-- -----------------------------------------------------------------------------
-- E. Collections & Collection Items (respect collection ownership & visibility)
-- -----------------------------------------------------------------------------
create policy "Collections: select own or public active"
    on public.collections for select
    using (
        auth.uid() = user_id
        or (visibility = 'public' and is_deleted = false)
    );

create policy "Collections: insert own"
    on public.collections for insert
    with check (auth.uid() = user_id);

create policy "Collections: update own"
    on public.collections for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Collections: delete own"
    on public.collections for delete
    using (auth.uid() = user_id);

create policy "Collection items: select if collection visible"
    on public.collection_items for select
    using (
        exists (
            select 1 from public.collections c
            where c.id = collection_id
              and (c.user_id = auth.uid() or (c.visibility = 'public' and c.is_deleted = false))
        )
    );

create policy "Collection items: write own collection only"
    on public.collection_items for insert
    with check (
        exists (
            select 1 from public.collections c
            where c.id = collection_id and c.user_id = auth.uid()
        )
    );

create policy "Collection items: update own collection only"
    on public.collection_items for update
    using (
        exists (
            select 1 from public.collections c
            where c.id = collection_id and c.user_id = auth.uid()
        )
    )
    with check (
        exists (
            select 1 from public.collections c
            where c.id = collection_id and c.user_id = auth.uid()
        )
    );

create policy "Collection items: delete own collection only"
    on public.collection_items for delete
    using (
        exists (
            select 1 from public.collections c
            where c.id = collection_id and c.user_id = auth.uid()
        )
    );

-- -----------------------------------------------------------------------------
-- F. Sync Op Ledger (strictly owner-only)
-- -----------------------------------------------------------------------------
create policy "Sync op ledger: owner only"
    on public.sync_op_ledger for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- -----------------------------------------------------------------------------
-- G. Defense-in-Depth Privilege Revocation for Unauthenticated (anon) Callers
-- Even if the public/publishable client key is extracted from the APK, the anon
-- role has zero write privileges and zero access to private user tables.
-- -----------------------------------------------------------------------------
revoke insert, update, delete, truncate on all tables in schema public from anon;

revoke all on table
    public.user_library_entries,
    public.viewing_logs,
    public.user_watched_episodes,
    public.user_tags,
    public.user_title_tags,
    public.sync_op_ledger
from anon;

grant select on table
    public.titles,
    public.genres,
    public.moods,
    public.title_genres,
    public.title_moods,
    public.episodes
to anon, authenticated;

grant select, insert, update, delete on table
    public.user_profiles,
    public.user_library_entries,
    public.viewing_logs,
    public.user_watched_episodes,
    public.user_tags,
    public.user_title_tags,
    public.reviews,
    public.collections,
    public.collection_items,
    public.sync_op_ledger
to authenticated;

