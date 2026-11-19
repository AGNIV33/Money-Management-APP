-- The AI minute limits used to come from the caller, and any signed-in user can call reserve_ai_minutes directly.
-- So a user could pass a negative amount (reset their own counter) or huge limits (fill the shared daily counter,
-- leaving everyone with "busy today"). The real limits now live in this table, which only the server can change.
create table if not exists public.ai_limits (
  kind        text primary key,
  slice_max   integer not null check (slice_max > 0),
  user_max    integer not null check (user_max > 0),
  global_max  integer not null check (global_max > 0)
);
alter table public.ai_limits enable row level security;
-- No policies: nobody reads or writes this table through the API.

-- Same values as the ai-live-token defaults (LIVE_SLICE_MINUTES max 15, LIVE_USER_MINUTES 300, LIVE_GLOBAL_MINUTES 3000).
insert into public.ai_limits (kind, slice_max, user_max, global_max)
values ('live_minutes', 15, 300, 3000)
on conflict (kind) do nothing;

-- Same signature as before so the edge function keeps working. The caller's limits can only make things stricter.
create or replace function public.reserve_ai_minutes(p_kind text, p_amount integer, p_user_max integer, p_global_max integer)
returns text
language plpgsql security definer set search_path = public as $$
declare
  lim public.ai_limits;
  u integer;
  g integer;
  user_cap integer;
  global_cap integer;
begin
  if auth.uid() is null then
    return 'not_signed_in';
  end if;
  select * into lim from ai_limits where kind = p_kind;
  if lim.kind is null or p_amount is null or p_amount < 1 or p_amount > lim.slice_max then
    return 'bad_request';
  end if;
  user_cap := least(coalesce(p_user_max, lim.user_max), lim.user_max);
  global_cap := least(coalesce(p_global_max, lim.global_max), lim.global_max);
  insert into ai_global_usage (day, kind, count) values (current_date, p_kind, 0) on conflict do nothing;
  insert into ai_usage (user_id, day, kind, count) values (auth.uid(), current_date, p_kind, 0) on conflict do nothing;
  select count into g from ai_global_usage where day = current_date and kind = p_kind for update;
  select count into u from ai_usage where user_id = auth.uid() and day = current_date and kind = p_kind for update;
  if u + p_amount > user_cap then
    return 'user_limit';
  end if;
  if g + p_amount > global_cap then
    return 'global_limit';
  end if;
  update ai_usage set count = count + p_amount where user_id = auth.uid() and day = current_date and kind = p_kind;
  update ai_global_usage set count = count + p_amount where day = current_date and kind = p_kind;
  return 'ok';
end $$;

revoke all on function public.reserve_ai_minutes(text, integer, integer, integer) from public, anon;
grant execute on function public.reserve_ai_minutes(text, integer, integer, integer) to authenticated;
