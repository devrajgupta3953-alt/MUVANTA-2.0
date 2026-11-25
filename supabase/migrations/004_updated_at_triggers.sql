-- =============================================================================
-- MUVANTA — Migration 004: Timestamp Maintenance & Privilege-Guard Triggers
-- =============================================================================

-- 1. Automatic updated_at maintenance
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_user_profiles_updated_at on public.user_profiles;
create trigger trg_user_profiles_updated_at
    before update on public.user_profiles
    for each row execute function public.set_updated_at();

drop trigger if exists trg_titles_updated_at on public.titles;
create trigger trg_titles_updated_at
    before update on public.titles
    for each row execute function public.set_updated_at();

drop trigger if exists trg_episodes_updated_at on public.episodes;
create trigger trg_episodes_updated_at
    before update on public.episodes
    for each row execute function public.set_updated_at();

drop trigger if exists trg_user_library_entries_updated_at on public.user_library_entries;
create trigger trg_user_library_entries_updated_at
    before update on public.user_library_entries
    for each row execute function public.set_updated_at();

drop trigger if exists trg_reviews_updated_at on public.reviews;
create trigger trg_reviews_updated_at
    before update on public.reviews
    for each row execute function public.set_updated_at();

drop trigger if exists trg_collections_updated_at on public.collections;
create trigger trg_collections_updated_at
    before update on public.collections
    for each row execute function public.set_updated_at();

-- 2. Prevent ordinary users from escalating their own role in user_profiles
create or replace function public.prevent_role_self_escalation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    caller_role text;
begin
    if tg_op = 'INSERT' then
        if new.role <> 'user' and current_user not in ('postgres', 'service_role') then
            raise exception 'Only administrators may assign privileged roles.';
        end if;
    elsif tg_op = 'UPDATE' then
        if new.role is distinct from old.role and current_user not in ('postgres', 'service_role') then
            select p.role into caller_role
            from public.user_profiles p
            where p.user_id = auth.uid();

            if coalesce(caller_role, 'user') <> 'admin' then
                raise exception 'Permission denied: cannot modify account role.';
            end if;
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_user_profiles_role_guard on public.user_profiles;
create trigger trg_user_profiles_role_guard
    before insert or update on public.user_profiles
    for each row execute function public.prevent_role_self_escalation();

-- 3. Automatically provision a user_profiles record when a new auth.users row is created
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.user_profiles (user_id, display_name)
    values (
        new.id,
        left(
            coalesce(
                nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
                'Cinema Archivist'
            ),
            60
        )
    )
    on conflict (user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_auth_user();
