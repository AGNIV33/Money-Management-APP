-- Move RLS helper functions out of the API-exposed public schema, so nobody can call
-- them via /rest/v1/rpc. Policies reference functions by OID, so they keep working.
-- create_family / join_family stay public on purpose: the app calls them.

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

alter function public.is_family_member(uuid) set schema private;
alter function public.is_family_admin(uuid) set schema private;
alter function public.member_role(uuid) set schema private;
alter function public.new_invite_code() set schema private;

revoke all on function private.is_family_member(uuid) from public, anon;
revoke all on function private.is_family_admin(uuid) from public, anon;
revoke all on function private.member_role(uuid) from public, anon;
revoke all on function private.new_invite_code() from public, anon, authenticated;
grant execute on function private.is_family_member(uuid) to authenticated;
grant execute on function private.is_family_admin(uuid) to authenticated;
grant execute on function private.member_role(uuid) to authenticated;

-- create_family calls new_invite_code() by name, so point it at the new schema.
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
  values (p_family_id, p_name, private.new_invite_code(), p_budget_paise, auth.uid())
  returning * into fam;
  insert into members (id, family_id, user_id, name, role)
  values (p_member_id, fam.id, auth.uid(), p_member_name, 'ADMIN');
  return fam;
end $$;
