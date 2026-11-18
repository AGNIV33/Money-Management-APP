-- Family Members screen shows each member's sign-in email. Emails live in auth.users, which the app
-- cannot read, so this returns them only for members of a family the caller belongs to.
create or replace function public.family_member_emails(p_family_id uuid)
returns table (member_id uuid, email text)
language sql stable security definer set search_path = public as $$
  select m.id, u.email::text
  from members m
  join auth.users u on u.id = m.user_id
  where m.family_id = p_family_id
    and m.deleted = false
    and private.is_family_member(p_family_id)
$$;

revoke all on function public.family_member_emails(uuid) from public, anon;
grant execute on function public.family_member_emails(uuid) to authenticated;
