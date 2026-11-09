-- Hisaab AI — Phase 1 schema: families, members, categories, expenses, budgets.
-- Money is bigint paise. IDs are UUIDs made on the phone (offline create).
-- updated_at is always set by the server (last-write-wins sync, ARCHITECTURE.md §5).
-- Deletes are soft (deleted = true) so other phones learn about them on pull.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.families (
  id                  uuid primary key,
  name                text not null check (char_length(name) between 1 and 60),
  invite_code         text not null unique check (invite_code ~ '^[A-Z2-9]{6}$'),
  monthly_budget_paise bigint check (monthly_budget_paise is null or monthly_budget_paise >= 0),
  created_by          uuid not null default auth.uid() references auth.users (id),
  updated_at          timestamptz not null default now(),
  deleted             boolean not null default false
);

create table public.members (
  id          uuid primary key,
  family_id   uuid not null references public.families (id),
  user_id     uuid not null references auth.users (id) on delete cascade,
  name        text not null check (char_length(name) between 1 and 40),
  role        text not null default 'MEMBER' check (role in ('ADMIN', 'MEMBER')),
  updated_at  timestamptz not null default now(),
  deleted     boolean not null default false,
  unique (family_id, user_id)
);

-- family_id null = built-in default category shared by everyone.
create table public.categories (
  id          uuid primary key,
  family_id   uuid references public.families (id),
  name        text not null check (char_length(name) between 1 and 30),
  name_hi     text,
  icon        text not null,
  color       bigint not null,
  is_default  boolean not null default false,
  updated_at  timestamptz not null default now(),
  deleted     boolean not null default false
);

create table public.expenses (
  id            uuid primary key,
  family_id     uuid not null references public.families (id),
  paid_by       uuid not null references public.members (id),
  -- Who created the row. PRIVATE rows are visible only to this user.
  owner_user    uuid not null default auth.uid() references auth.users (id),
  amount_paise  bigint not null check (amount_paise > 0),
  category_id   uuid not null references public.categories (id),
  date          date not null,
  note          text check (note is null or char_length(note) <= 200),
  mode          text not null check (mode in ('CASH', 'UPI', 'CARD', 'OTHER')),
  visibility    text not null default 'FAMILY' check (visibility in ('FAMILY', 'PRIVATE')),
  source        text not null default 'MANUAL' check (source in ('MANUAL', 'VOICE', 'SCAN', 'RECURRING')),
  recurring_id  uuid,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- month = first day of the month. per_category: {"<category uuid>": paise}
create table public.budgets (
  family_id     uuid not null references public.families (id),
  month         date not null check (extract(day from month) = 1),
  total_paise   bigint not null check (total_paise >= 0),
  per_category  jsonb not null default '{}'::jsonb,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false,
  primary key (family_id, month)
);

create index members_user_idx on public.members (user_id) where not deleted;
create index expenses_family_date_idx on public.expenses (family_id, date);
create index expenses_category_idx on public.expenses (category_id);
create index expenses_updated_idx on public.expenses (family_id, updated_at);
create index categories_family_idx on public.categories (family_id);

-- ---------------------------------------------------------------------------
-- Server-owned updated_at (clients can't fake a newer timestamp)
-- ---------------------------------------------------------------------------

create or replace function public.touch_updated_at() returns trigger
language plpgsql set search_path = public as $$
begin
  new.updated_at := now();
  return new;
end $$;

create trigger families_touch before insert or update on public.families
  for each row execute function public.touch_updated_at();
create trigger members_touch before insert or update on public.members
  for each row execute function public.touch_updated_at();
create trigger categories_touch before insert or update on public.categories
  for each row execute function public.touch_updated_at();
create trigger expenses_touch before insert or update on public.expenses
  for each row execute function public.touch_updated_at();
create trigger budgets_touch before insert or update on public.budgets
  for each row execute function public.touch_updated_at();

-- owner_user and created_by can't be changed after insert.
create or replace function public.keep_owner() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_table_name = 'expenses' then
    new.owner_user := old.owner_user;
  elsif tg_table_name = 'families' then
    new.created_by := old.created_by;
  end if;
  return new;
end $$;

create trigger expenses_keep_owner before update on public.expenses
  for each row execute function public.keep_owner();
create trigger families_keep_owner before update on public.families
  for each row execute function public.keep_owner();

-- ---------------------------------------------------------------------------
-- Membership helpers. security definer so policies on members don't recurse.
-- ---------------------------------------------------------------------------

create or replace function public.is_family_member(fid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from members
    where family_id = fid and user_id = auth.uid() and not deleted
  );
$$;

-- Current stored role of a member row; lets a member edit their name but not promote themselves.
create or replace function public.member_role(mid uuid) returns text
language sql stable security definer set search_path = public as $$
  select role from members where id = mid;
$$;

create or replace function public.is_family_admin(fid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from members
    where family_id = fid and user_id = auth.uid() and role = 'ADMIN' and not deleted
  );
$$;

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------

alter table public.families   enable row level security;
alter table public.members    enable row level security;
alter table public.categories enable row level security;
alter table public.expenses   enable row level security;
alter table public.budgets    enable row level security;

-- families: members read; admins edit. Creating goes through create_family().
create policy families_select on public.families for select to authenticated
  using (public.is_family_member(id));
create policy families_update on public.families for update to authenticated
  using (public.is_family_admin(id)) with check (public.is_family_admin(id));

-- members: see your family; edit your own name; admins manage roles/removal.
-- Joining goes through join_family().
create policy members_select on public.members for select to authenticated
  using (public.is_family_member(family_id));
create policy members_update_self on public.members for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid() and role = public.member_role(id));
create policy members_update_admin on public.members for update to authenticated
  using (public.is_family_admin(family_id)) with check (public.is_family_admin(family_id));

-- categories: defaults readable by all; family categories by members.
create policy categories_select on public.categories for select to authenticated
  using (family_id is null or public.is_family_member(family_id));
create policy categories_insert on public.categories for insert to authenticated
  with check (family_id is not null and not is_default and public.is_family_member(family_id));
create policy categories_update on public.categories for update to authenticated
  using (family_id is not null and public.is_family_member(family_id))
  with check (family_id is not null and not is_default and public.is_family_member(family_id));

-- expenses: FAMILY rows shared with the family; PRIVATE rows only for owner (CLAUDE.md rule 7).
create policy expenses_select on public.expenses for select to authenticated
  using (public.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = auth.uid()));
create policy expenses_insert on public.expenses for insert to authenticated
  with check (
    public.is_family_member(family_id)
    and owner_user = auth.uid()
    and exists (select 1 from public.members m where m.id = paid_by and m.family_id = expenses.family_id)
  );
create policy expenses_update on public.expenses for update to authenticated
  using (public.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = auth.uid()))
  with check (
    public.is_family_member(family_id)
    and (visibility = 'FAMILY' or owner_user = auth.uid())
    and exists (select 1 from public.members m where m.id = paid_by and m.family_id = expenses.family_id)
  );
-- No delete policy: rows are soft-deleted with an update.

-- budgets: members read; admins write.
create policy budgets_select on public.budgets for select to authenticated
  using (public.is_family_member(family_id));
create policy budgets_insert on public.budgets for insert to authenticated
  with check (public.is_family_admin(family_id));
create policy budgets_update on public.budgets for update to authenticated
  using (public.is_family_admin(family_id)) with check (public.is_family_admin(family_id));

-- ---------------------------------------------------------------------------
-- RPCs: create and join a family atomically
-- ---------------------------------------------------------------------------

-- Without O/0/I/1 so codes are easy to read out over the phone.
create or replace function public.new_invite_code() returns text
language plpgsql volatile set search_path = public as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text;
begin
  loop
    code := '';
    for i in 1..6 loop
      code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    exit when not exists (select 1 from public.families where invite_code = code);
  end loop;
  return code;
end $$;

create or replace function public.create_family(
  p_family_id uuid, p_member_id uuid, p_name text, p_member_name text, p_budget_paise bigint default null
) returns public.families
language plpgsql security definer set search_path = public as $$
declare
  fam public.families;
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  insert into families (id, name, invite_code, monthly_budget_paise, created_by)
  values (p_family_id, p_name, new_invite_code(), p_budget_paise, auth.uid())
  returning * into fam;
  insert into members (id, family_id, user_id, name, role)
  values (p_member_id, fam.id, auth.uid(), p_member_name, 'ADMIN');
  return fam;
end $$;

-- Free plan: up to 4 members per family (PRD monetisation).
create or replace function public.join_family(p_code text, p_member_id uuid, p_member_name text)
returns public.families
language plpgsql security definer set search_path = public as $$
declare
  fam public.families;
  member_count int;
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  select * into fam from families where invite_code = upper(trim(p_code)) and not deleted;
  if fam.id is null then
    raise exception 'invalid invite code' using errcode = 'P0002';
  end if;
  if exists (select 1 from members where family_id = fam.id and user_id = auth.uid() and not deleted) then
    return fam;
  end if;
  select count(*) into member_count from members where family_id = fam.id and not deleted;
  if member_count >= 4 then
    raise exception 'family is full' using errcode = 'P0001';
  end if;
  insert into members (id, family_id, user_id, name, role)
  values (p_member_id, fam.id, auth.uid(), p_member_name, 'MEMBER')
  on conflict (family_id, user_id) do update set deleted = false, name = excluded.name;
  return fam;
end $$;

revoke all on function public.create_family(uuid, uuid, text, text, bigint) from public, anon;
revoke all on function public.join_family(text, uuid, text) from public, anon;
revoke all on function public.new_invite_code() from public, anon, authenticated;
grant execute on function public.create_family(uuid, uuid, text, text, bigint) to authenticated;
grant execute on function public.join_family(text, uuid, text) to authenticated;
