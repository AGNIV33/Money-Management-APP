-- Daily cap on AI calls per signed-in user. The counter is changed only through bump_ai_usage(),
-- which runs as the table owner, so a user cannot reset their own count.
create table public.ai_usage (
  user_id  uuid not null references auth.users (id) on delete cascade,
  day      date not null,
  kind     text not null,
  count    integer not null default 0,
  primary key (user_id, day, kind)
);

alter table public.ai_usage enable row level security;
-- No policies: nobody reads or writes this table directly.

create or replace function public.bump_ai_usage(p_kind text, p_max integer) returns boolean
language plpgsql security definer set search_path = public as $$
declare
  used integer;
begin
  if auth.uid() is null then
    return false;
  end if;
  insert into ai_usage (user_id, day, kind, count) values (auth.uid(), current_date, p_kind, 1)
  on conflict (user_id, day, kind) do update set count = ai_usage.count + 1
  returning count into used;
  return used <= p_max;
end $$;

revoke all on function public.bump_ai_usage(text, integer) from public, anon;
grant execute on function public.bump_ai_usage(text, integer) to authenticated;
