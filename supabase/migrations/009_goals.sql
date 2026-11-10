-- Kinly: savings goals (IMPLEMENTATION_PLAN 4.9). Same rules as udhaar: phone-made UUIDs, server-owned
-- updated_at, soft deletes, PRIVATE only for the owner, only the owner or a family admin may delete a goal,
-- savings entries follow their goal.

create table public.goals (
  id            uuid primary key,
  family_id     uuid not null references public.families (id),
  owner_user    uuid not null default auth.uid() references auth.users (id),
  name          text not null check (char_length(name) between 1 and 40),
  target_paise  bigint not null check (target_paise > 0),
  deadline      date,
  visibility    text not null default 'FAMILY' check (visibility in ('FAMILY', 'PRIVATE')),
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- Money put towards a goal. Taking one back is a soft delete.
create table public.goal_contributions (
  id            uuid primary key,
  goal_id       uuid not null references public.goals (id),
  family_id     uuid not null references public.families (id),
  owner_user    uuid not null default auth.uid() references auth.users (id),
  amount_paise  bigint not null check (amount_paise > 0),
  date          date not null,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

create index goals_family_idx on public.goals (family_id);
create index goals_updated_idx on public.goals (family_id, updated_at);
create index goal_contributions_goal_idx on public.goal_contributions (goal_id);
create index goal_contributions_updated_idx on public.goal_contributions (family_id, updated_at);

create trigger goals_touch before insert or update on public.goals
  for each row execute function public.touch_updated_at();
create trigger goal_contributions_touch before insert or update on public.goal_contributions
  for each row execute function public.touch_updated_at();

create or replace function public.keep_owner() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_table_name in ('expenses', 'loans', 'loan_payments', 'recurring', 'udhaar', 'udhaar_payments', 'goals', 'goal_contributions') then
    new.owner_user := old.owner_user;
  elsif tg_table_name = 'families' then
    new.created_by := old.created_by;
  end if;
  return new;
end $$;

create trigger goals_keep_owner before update on public.goals
  for each row execute function public.keep_owner();
create trigger goal_contributions_keep_owner before update on public.goal_contributions
  for each row execute function public.keep_owner();
create trigger goals_delete_rule before update on public.goals
  for each row execute function private.only_owner_or_admin_deletes();

create or replace function private.goal_owner(gid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from goals where id = gid;
$$;
create or replace function private.goal_contribution_owner(cid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from goal_contributions where id = cid;
$$;
revoke all on function private.goal_owner(uuid) from public, anon;
revoke all on function private.goal_contribution_owner(uuid) from public, anon;
grant execute on function private.goal_owner(uuid) to authenticated;
grant execute on function private.goal_contribution_owner(uuid) to authenticated;

alter table public.goals              enable row level security;
alter table public.goal_contributions enable row level security;

create policy goals_select on public.goals for select to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));
create policy goals_insert on public.goals for insert to authenticated
  with check (
    private.is_family_member(family_id)
    and (owner_user = (select auth.uid()) or (visibility = 'FAMILY' and owner_user = private.goal_owner(id)))
  );
create policy goals_update on public.goals for update to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())))
  with check (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));

create policy goal_contributions_select on public.goal_contributions for select to authenticated
  using (exists (select 1 from public.goals g where g.id = goal_id and g.family_id = goal_contributions.family_id));
create policy goal_contributions_insert on public.goal_contributions for insert to authenticated
  with check (
    (owner_user = (select auth.uid()) or owner_user = private.goal_contribution_owner(id))
    and exists (select 1 from public.goals g where g.id = goal_id and g.family_id = goal_contributions.family_id)
  );
create policy goal_contributions_update on public.goal_contributions for update to authenticated
  using (exists (select 1 from public.goals g where g.id = goal_id and g.family_id = goal_contributions.family_id))
  with check (exists (select 1 from public.goals g where g.id = goal_id and g.family_id = goal_contributions.family_id));
-- No delete policies: rows are soft-deleted with an update.
