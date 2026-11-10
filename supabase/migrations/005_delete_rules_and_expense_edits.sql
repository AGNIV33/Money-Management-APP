-- Kinly: who may delete, and fix BUG-002 (TESTING.md).
--
-- 1) Deleting is a soft delete (deleted = true). Only the person who added the row, or a family
--    admin, may flip it. Other members can still edit shared rows, just not delete them.
-- 2) BUG-002: the app saves with an upsert, and Postgres checks the INSERT policy even when the row
--    already exists. expenses_insert required owner_user = auth.uid(), so a member editing someone
--    else's FAMILY expense was refused. Same fix loans already have: allow the write when it carries
--    the owner already stored. (keep_owner() makes sure the stored owner never changes.)

create or replace function private.expense_owner(eid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_user from expenses where id = eid;
$$;
revoke all on function private.expense_owner(uuid) from public, anon;
grant execute on function private.expense_owner(uuid) to authenticated;

drop policy expenses_insert on public.expenses;
create policy expenses_insert on public.expenses for insert to authenticated
  with check (
    private.is_family_member(family_id)
    and (
      owner_user = (select auth.uid())
      or (visibility = 'FAMILY' and owner_user = private.expense_owner(id))
    )
    and exists (select 1 from public.members m where m.id = paid_by and m.family_id = expenses.family_id)
  );

create or replace function private.only_owner_or_admin_deletes() returns trigger
language plpgsql set search_path = public as $$
begin
  if new.deleted and not old.deleted
     and old.owner_user is distinct from auth.uid()
     and not private.is_family_admin(old.family_id) then
    raise exception 'only the person who added this, or a family admin, can delete it'
      using errcode = '42501';
  end if;
  return new;
end $$;

create trigger expenses_delete_rule before update on public.expenses
  for each row execute function private.only_owner_or_admin_deletes();
create trigger loans_delete_rule before update on public.loans
  for each row execute function private.only_owner_or_admin_deletes();
-- Un-marking an EMI is a soft delete of the payment row; anyone who can see the loan may do that
-- (two people share one loan, either may fix a wrong tick), so loan_payments has no delete rule.
