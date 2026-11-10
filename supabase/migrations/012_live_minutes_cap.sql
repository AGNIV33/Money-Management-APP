-- Daily minutes cap for the voice assistant, per user and for everyone together, so the free quota is never exceeded.
create table public.ai_global_usage (
  day    date not null,
  kind   text not null,
  count  integer not null default 0,
  primary key (day, kind)
);
alter table public.ai_global_usage enable row level security;
-- No policies: nobody reads or writes this table directly.

-- Reserves p_amount minutes for the signed-in user. Returns 'ok', 'user_limit' or 'global_limit'.
-- Runs as the table owner so the counters cannot be reset by a user.
create or replace function public.reserve_ai_minutes(p_kind text, p_amount integer, p_user_max integer, p_global_max integer)
returns text
language plpgsql security definer set search_path = public as $$
declare
  u integer;
  g integer;
begin
  if auth.uid() is null then
    return 'not_signed_in';
  end if;
  insert into ai_global_usage (day, kind, count) values (current_date, p_kind, 0) on conflict do nothing;
  insert into ai_usage (user_id, day, kind, count) values (auth.uid(), current_date, p_kind, 0) on conflict do nothing;
  select count into g from ai_global_usage where day = current_date and kind = p_kind for update;
  select count into u from ai_usage where user_id = auth.uid() and day = current_date and kind = p_kind for update;
  if u + p_amount > p_user_max then
    return 'user_limit';
  end if;
  if g + p_amount > p_global_max then
    return 'global_limit';
  end if;
  update ai_usage set count = count + p_amount where user_id = auth.uid() and day = current_date and kind = p_kind;
  update ai_global_usage set count = count + p_amount where day = current_date and kind = p_kind;
  return 'ok';
end $$;

revoke all on function public.reserve_ai_minutes(text, integer, integer, integer) from public, anon;
grant execute on function public.reserve_ai_minutes(text, integer, integer, integer) to authenticated;
