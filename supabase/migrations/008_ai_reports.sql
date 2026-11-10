-- Kinly: stored monthly AI tip reports (IMPLEMENTATION_PLAN 4.5/4.7).
create table public.ai_reports (
  id          uuid primary key default gen_random_uuid(),
  family_id   uuid not null references public.families (id),
  -- First day of the month the report is about.
  month       date not null check (extract(day from month) = 1),
  language    text not null check (language in ('en', 'hi')),
  tips        jsonb not null,
  created_by  uuid not null default auth.uid() references auth.users (id),
  created_at  timestamptz not null default now(),
  -- One free report per family per month; the function returns this one again instead of calling the AI twice.
  unique (family_id, month)
);

alter table public.ai_reports enable row level security;

create policy ai_reports_select on public.ai_reports for select to authenticated
  using (private.is_family_member(family_id));
create policy ai_reports_insert on public.ai_reports for insert to authenticated
  with check (private.is_family_member(family_id) and created_by = (select auth.uid()));
-- No update or delete: a report never changes once made.
