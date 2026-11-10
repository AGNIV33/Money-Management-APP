-- Kinly: udhaar, money others owe the family or the family owes (IMPLEMENTATION_PLAN 3.6).
-- Same rules as loans: phone-made UUIDs, server-owned updated_at, soft deletes, PRIVATE only for the owner,
-- only the owner or a family admin may delete an entry, payments follow their entry.

create table public.udhaar (
  id            uuid primary key,
  family_id     uuid not null references public.families (id),
  owner_user    uuid not null default auth.uid() references auth.users (id),
  direction     text not null check (direction in ('THEY_OWE_ME', 'I_OWE')),
  person        text not null check (char_length(person) between 1 and 40),
  amount_paise  bigint not null check (amount_paise > 0),
  date          date not null,
  due_date      date,
  note          text check (note is null or char_length(note) <= 200),
  -- Set when the entry came from "bought on credit" in Add expense.
  expense_id    uuid,
  visibility    text not null default 'FAMILY' check (visibility in ('FAMILY', 'PRIVATE')),
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- Part payments. Un-doing one is a soft delete.
create table public.udhaar_payments (
  id            uuid primary key,
  udhaar_id     uuid not null references public.udhaar (id),
  family_id     uuid not null references public.families (id),
  owner_user    uuid not null default auth.uid() references auth.users (id),
  amount_paise  bigint not null check (amount_paise > 0),
  paid_on       date not null,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

create index udhaar_family_idx on public.udhaar (family_id);
create index udhaar_updated_idx on public.udhaar (family_id, updated_at);
create index udhaar_payments_entry_idx on public.udhaar_payments (udhaar_id);
create index udhaar_payments_updated_idx on public.udhaar_payments (family_id, updated_at);

create trigger udhaar_touch before insert or update on public.udhaar
  for each row execute function public.touch_updated_at();
create trigger udhaar_payments_touch before insert or update on public.udhaar_payments
  for each row execute function public.touch_updated_at();

create or replace function public.keep_owner() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_table_name in ('expenses', 'loans', 'loan_payments', 'recurring', 'udhaar', 'udhaar_payments') then
    new.owner_user := old.owner_user;
  elsif tg_table_name = 'families' then
    new.created_by := old.created_by;
  end if;
  return new;
end $$;

create trigger udhaar_keep_owner before update on public.udhaar
  for each row execute function public.keep_owner();
create trigger udhaar_payments_keep_owner before update on public.udhaar_payments
  for each row execute function public.keep_owner();
create trigger udhaar_delete_rule before update on public.udhaar
  for each row execute function private.only_owner_or_admin_deletes();

create or replace function private.udhaar_owner(uid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from udhaar where id = uid;
$$;
create or replace function private.udhaar_payment_owner(pid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from udhaar_payments where id = pid;
$$;
revoke all on function private.udhaar_owner(uuid) from public, anon;
revoke all on function private.udhaar_payment_owner(uuid) from public, anon;
grant execute on function private.udhaar_owner(uuid) to authenticated;
grant execute on function private.udhaar_payment_owner(uuid) to authenticated;

alter table public.udhaar          enable row level security;
alter table public.udhaar_payments enable row level security;

create policy udhaar_select on public.udhaar for select to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));
create policy udhaar_insert on public.udhaar for insert to authenticated
  with check (
    private.is_family_member(family_id)
    and (owner_user = (select auth.uid()) or (visibility = 'FAMILY' and owner_user = private.udhaar_owner(id)))
  );
create policy udhaar_update on public.udhaar for update to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())))
  with check (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));

-- Payments follow their entry: the subquery runs under the udhaar policies, so a payment on someone
-- else's private entry is invisible and cannot be written.
create policy udhaar_payments_select on public.udhaar_payments for select to authenticated
  using (exists (select 1 from public.udhaar u where u.id = udhaar_id and u.family_id = udhaar_payments.family_id));
create policy udhaar_payments_insert on public.udhaar_payments for insert to authenticated
  with check (
    (owner_user = (select auth.uid()) or owner_user = private.udhaar_payment_owner(id))
    and exists (select 1 from public.udhaar u where u.id = udhaar_id and u.family_id = udhaar_payments.family_id)
  );
create policy udhaar_payments_update on public.udhaar_payments for update to authenticated
  using (exists (select 1 from public.udhaar u where u.id = udhaar_id and u.family_id = udhaar_payments.family_id))
  with check (exists (select 1 from public.udhaar u where u.id = udhaar_id and u.family_id = udhaar_payments.family_id));
-- No delete policies: rows are soft-deleted with an update.
