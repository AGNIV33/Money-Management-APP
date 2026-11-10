-- Kinly: repeating expenses (IMPLEMENTATION_PLAN 2.8). Same rules as expenses and loans:
-- phone-made UUIDs, server-owned updated_at, soft deletes, PRIVATE only for the owner,
-- only the owner or a family admin may delete (trigger from 005).

create table public.recurring (
  id              uuid primary key,
  family_id       uuid not null references public.families (id),
  owner_user      uuid not null default auth.uid() references auth.users (id),
  paid_by         uuid not null references public.members (id),
  amount_paise    bigint not null check (amount_paise > 0),
  category_id     uuid not null references public.categories (id),
  mode            text not null check (mode in ('CASH', 'UPI', 'CARD', 'OTHER')),
  note            text check (note is null or char_length(note) <= 200),
  visibility      text not null default 'FAMILY' check (visibility in ('FAMILY', 'PRIVATE')),
  rule            text not null check (rule in ('DAILY', 'WEEKLY', 'MONTHLY')),
  start_date      date not null,
  -- Newest date an expense was already made for.
  last_generated  date not null,
  updated_at      timestamptz not null default now(),
  deleted         boolean not null default false
);

create index recurring_family_idx on public.recurring (family_id);
create index recurring_updated_idx on public.recurring (family_id, updated_at);

create trigger recurring_touch before insert or update on public.recurring
  for each row execute function public.touch_updated_at();

create or replace function public.keep_owner() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_table_name in ('expenses', 'loans', 'loan_payments', 'recurring') then
    new.owner_user := old.owner_user;
  elsif tg_table_name = 'families' then
    new.created_by := old.created_by;
  end if;
  return new;
end $$;

create trigger recurring_keep_owner before update on public.recurring
  for each row execute function public.keep_owner();
create trigger recurring_delete_rule before update on public.recurring
  for each row execute function private.only_owner_or_admin_deletes();

create or replace function private.recurring_owner(rid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from recurring where id = rid;
$$;
revoke all on function private.recurring_owner(uuid) from public, anon;
grant execute on function private.recurring_owner(uuid) to authenticated;

alter table public.recurring enable row level security;

create policy recurring_select on public.recurring for select to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));
create policy recurring_insert on public.recurring for insert to authenticated
  with check (
    private.is_family_member(family_id)
    and (owner_user = (select auth.uid()) or (visibility = 'FAMILY' and owner_user = private.recurring_owner(id)))
    and exists (select 1 from public.members m where m.id = paid_by and m.family_id = recurring.family_id)
  );
create policy recurring_update on public.recurring for update to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())))
  with check (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));
-- No delete policy: rows are soft-deleted with an update.
