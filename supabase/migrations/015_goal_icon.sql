-- Savings goals can carry an emoji picked in the app. Null = the default jar.
alter table public.goals
  add column if not exists icon text check (icon is null or char_length(icon) <= 16);
