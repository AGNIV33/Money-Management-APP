-- Kinly — Phase 2: loans and EMI payments (IMPLEMENTATION_PLAN 2.2).
-- Same rules as expenses: phone-made UUIDs, server-owned updated_at, soft deletes,
-- PRIVATE rows only for the user who created them (owner_user).

create table public.loans (
  id               uuid primary key,
  family_id        uuid not null references public.families (id),
  owner_user       uuid not null default auth.uid() references auth.users (id),
  type             text not null check (type in ('HOME', 'CAR', 'BIKE', 'PERSONAL', 'EDUCATION', 'GOLD', 'CONSUMER', 'CREDIT_CARD', 'OTHER')),
  lender           text not null check (char_length(lender) between 1 and 40),
  principal_paise  bigint not null check (principal_paise between 100 and 100000000000),
  -- 8.5% = 850. Basis points avoid floating point.
  annual_rate_bps  integer not null check (annual_rate_bps between 0 and 4200),
  tenure_months    integer not null check (tenure_months between 1 and 480),
  emi_paise        bigint not null check (emi_paise > 0),
  first_emi_date   date not null,
  due_day          integer not null check (due_day between 1 and 31),
  -- EMIs due before this date are treated as already paid.
  added_on         date not null,
  visibility       text not null default 'FAMILY' check (visibility in ('FAMILY', 'PRIVATE')),
  updated_at       timestamptz not null default now(),
  deleted          boolean not null default false
);

-- One row per paid EMI. The phone makes the id from loan + month, so marking the same month
-- on two phones writes the same row. Un-marking is a soft delete.
create table public.loan_payments (
  id            uuid primary key,
  loan_id       uuid not null references public.loans (id),
  family_id     uuid not null references public.families (id),
  owner_user    uuid not null default auth.uid() references auth.users (id),
  month         date not null check (extract(day from month) = 1),
  amount_paise  bigint not null check (amount_paise > 0),
  paid_on       date not null,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

create index loans_family_idx on public.loans (family_id);
create index loans_updated_idx on public.loans (family_id, updated_at);
create index loan_payments_loan_idx on public.loan_payments (loan_id);
create index loan_payments_updated_idx on public.loan_payments (family_id, updated_at);

create trigger loans_touch before insert or update on public.loans
  for each row execute function public.touch_updated_at();
create trigger loan_payments_touch before insert or update on public.loan_payments
  for each row execute function public.touch_updated_at();

-- owner_user can't be changed after insert (extends the 001 function to the new tables).
create or replace function public.keep_owner() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_table_name in ('expenses', 'loans', 'loan_payments') then
    new.owner_user := old.owner_user;
  elsif tg_table_name = 'families' then
    new.created_by := old.created_by;
  end if;
  return new;
end $$;

create trigger loans_keep_owner before update on public.loans
  for each row execute function public.keep_owner();
create trigger loan_payments_keep_owner before update on public.loan_payments
  for each row execute function public.keep_owner();

-- Upserts check the INSERT policy even when the row exists, so an edit by another family member
-- (who keeps the original owner_user) would be refused. These helpers let a write through when it
-- carries the owner already stored. security definer avoids loans policies querying loans.
create or replace function private.loan_owner(lid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from loans where id = lid;
$$;
create or replace function private.loan_payment_owner(pid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from loan_payments where id = pid;
$$;
revoke all on function private.loan_owner(uuid) from public, anon;
revoke all on function private.loan_payment_owner(uuid) from public, anon;
grant execute on function private.loan_owner(uuid) to authenticated;
grant execute on function private.loan_payment_owner(uuid) to authenticated;

alter table public.loans         enable row level security;
alter table public.loan_payments enable row level security;

-- loans: FAMILY rows shared with the family; PRIVATE rows only for the owner (CLAUDE.md rule 7).
create policy loans_select on public.loans for select to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));
create policy loans_insert on public.loans for insert to authenticated
  with check (
    private.is_family_member(family_id)
    and (owner_user = (select auth.uid()) or (visibility = 'FAMILY' and owner_user = private.loan_owner(id)))
  );
create policy loans_update on public.loans for update to authenticated
  using (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())))
  with check (private.is_family_member(family_id) and (visibility = 'FAMILY' or owner_user = (select auth.uid())));

-- loan_payments follow their loan: the subquery runs under the loans policies above,
-- so a payment of someone else's private loan is invisible and can't be written.
create policy loan_payments_select on public.loan_payments for select to authenticated
  using (exists (select 1 from public.loans l where l.id = loan_id and l.family_id = loan_payments.family_id));
create policy loan_payments_insert on public.loan_payments for insert to authenticated
  with check (
    (owner_user = (select auth.uid()) or owner_user = private.loan_payment_owner(id))
    and exists (select 1 from public.loans l where l.id = loan_id and l.family_id = loan_payments.family_id)
  );
create policy loan_payments_update on public.loan_payments for update to authenticated
  using (exists (select 1 from public.loans l where l.id = loan_id and l.family_id = loan_payments.family_id))
  with check (exists (select 1 from public.loans l where l.id = loan_id and l.family_id = loan_payments.family_id));
-- No delete policies: rows are soft-deleted with an update.
