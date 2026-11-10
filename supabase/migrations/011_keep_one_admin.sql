-- A family always keeps at least one admin: the last admin cannot be demoted or removed.
create or replace function private.keep_one_admin() returns trigger
language plpgsql set search_path = public as $$
begin
  if old.role = 'ADMIN' and not old.deleted and (new.role <> 'ADMIN' or new.deleted) then
    if not exists (
      select 1 from members m
      where m.family_id = old.family_id and m.id <> old.id and m.role = 'ADMIN' and not m.deleted
    ) then
      raise exception 'a family needs at least one admin' using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

create trigger members_keep_one_admin before update on public.members
  for each row execute function private.keep_one_admin();
