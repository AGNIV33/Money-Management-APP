-- Kinly 5.6: delete my account and my data (PRD "delete account + data", Play account-deletion rule).
--
-- What is deleted, per family the caller belongs to:
--   * Caller is the only member left: the whole family and everything in it.
--   * Others are still in the family:
--       - every "Only me" (PRIVATE) row the caller owns, with its payments / contributions;
--       - the caller's member row is kept only as an anonymous, removed "Former member" so the family's
--         shared history (paid_by) still adds up; it no longer points at any login;
--       - shared (FAMILY) rows the caller added stay with the family and are handed to a family admin,
--         because they are the family's records too (same as a removed member's entries today).
--     The last admin cannot leave: they must make someone else admin first (same rule as 011).
-- Then the login itself (auth.users) is deleted; ai_usage goes with it (on delete cascade).
--
-- Runs as the function owner (security definer) so it can delete across tables and auth.users,
-- but only ever for auth.uid(): a caller can never delete someone else.

-- A removed member's login may now be gone.
alter table public.members alter column user_id drop not null;

-- keep_owner() normally freezes owner_user / created_by. Allow a change only inside
-- delete_my_account(), which sets this transaction-local flag.
create or replace function public.keep_owner() returns trigger
language plpgsql set search_path = public as $$
begin
  if coalesce(current_setting('kinly.owner_transfer', true), '') = 'on' then
    return new;
  end if;
  if tg_table_name in ('expenses', 'loans', 'loan_payments', 'recurring', 'udhaar', 'udhaar_payments', 'goals', 'goal_contributions') then
    new.owner_user := old.owner_user;
  elsif tg_table_name = 'families' then
    new.created_by := old.created_by;
  end if;
  return new;
end $$;

create or replace function public.delete_my_account() returns void
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  fam record;
  heir uuid;
begin
  if uid is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;

  -- First check every family, so nothing is deleted when one of them blocks.
  for fam in
    select m.family_id, m.role from members m where m.user_id = uid and not m.deleted
  loop
    if fam.role = 'ADMIN'
       and exists (select 1 from members o where o.family_id = fam.family_id and o.user_id is distinct from uid and not o.deleted)
       and not exists (
         select 1 from members o
         where o.family_id = fam.family_id and o.user_id is distinct from uid and o.role = 'ADMIN' and not o.deleted
       ) then
      raise exception 'a family needs at least one admin' using errcode = 'P0001';
    end if;
  end loop;

  perform set_config('kinly.owner_transfer', 'on', true);

  for fam in
    select distinct m.family_id from members m where m.user_id = uid
  loop
    if not exists (select 1 from members o where o.family_id = fam.family_id and o.user_id is distinct from uid and not o.deleted) then
      -- Nobody else left: the whole family goes. Children before parents.
      delete from goal_contributions where family_id = fam.family_id;
      delete from goals where family_id = fam.family_id;
      delete from udhaar_payments where family_id = fam.family_id;
      delete from udhaar where family_id = fam.family_id;
      delete from loan_payments where family_id = fam.family_id;
      delete from loans where family_id = fam.family_id;
      delete from expenses where family_id = fam.family_id;
      delete from recurring where family_id = fam.family_id;
      delete from budgets where family_id = fam.family_id;
      delete from ai_reports where family_id = fam.family_id;
      delete from categories where family_id = fam.family_id;
      delete from members where family_id = fam.family_id;
      delete from families where id = fam.family_id;
    else
      select o.user_id into heir from members o
      where o.family_id = fam.family_id and o.user_id is distinct from uid and o.role = 'ADMIN' and not o.deleted
      order by o.updated_at limit 1;
      if heir is null then
        -- Caller is a plain member and the family has no admin left (should not happen after 011): any member.
        select o.user_id into heir from members o
        where o.family_id = fam.family_id and o.user_id is distinct from uid and not o.deleted
        order by o.updated_at limit 1;
      end if;

      -- "Only me" rows: deleted for good, children first.
      delete from goal_contributions where family_id = fam.family_id
        and goal_id in (select id from goals where owner_user = uid and visibility = 'PRIVATE');
      delete from goals where family_id = fam.family_id and owner_user = uid and visibility = 'PRIVATE';
      delete from udhaar_payments where family_id = fam.family_id
        and udhaar_id in (select id from udhaar where owner_user = uid and visibility = 'PRIVATE');
      delete from udhaar where family_id = fam.family_id and owner_user = uid and visibility = 'PRIVATE';
      delete from loan_payments where family_id = fam.family_id
        and loan_id in (select id from loans where owner_user = uid and visibility = 'PRIVATE');
      delete from loans where family_id = fam.family_id and owner_user = uid and visibility = 'PRIVATE';
      delete from expenses where family_id = fam.family_id and owner_user = uid and visibility = 'PRIVATE';
      delete from recurring where family_id = fam.family_id and owner_user = uid and visibility = 'PRIVATE';

      -- Shared rows stay with the family, owned by an admin from now on.
      update goal_contributions set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update goals set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update udhaar_payments set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update udhaar set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update loan_payments set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update loans set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update expenses set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update recurring set owner_user = heir where family_id = fam.family_id and owner_user = uid;
      update ai_reports set created_by = heir where family_id = fam.family_id and created_by = uid;
      update families set created_by = heir where id = fam.family_id and created_by = uid;

      -- No name, no login: just "a former member" for the family's old entries.
      -- (Role becomes MEMBER first so the keep-one-admin trigger is satisfied: another admin exists.)
      update members set role = 'MEMBER' where family_id = fam.family_id and user_id = uid and role = 'ADMIN';
      update members set name = 'Former member', user_id = null, deleted = true
        where family_id = fam.family_id and user_id = uid;
    end if;
  end loop;

  -- The loop above also covered families the caller had already left (their member row was removed but
  -- still pointed at this login), so nothing references auth.users for this id any more.
  delete from auth.users where id = uid;
end $$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
