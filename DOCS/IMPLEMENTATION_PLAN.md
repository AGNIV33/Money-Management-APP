# IMPLEMENTATION_PLAN.md — Build Order

> Do tasks **in order**. Tick `[x]` when done (Definition of Done in `CLAUDE.md` §8).
> Each phase ends with a working, testable build.
> Estimated time assumes 3–4 focused hours/day.

---

## Phase 0 — Setup (Day 1–2)
- [ ] 0.1 Install Android Studio (latest stable), JDK 17+, create emulator: Pixel API 26, 2 GB RAM (low-end) + one recent API emulator.
- [x] 0.2 Create project `com.agniv.hisaab` (Empty Compose Activity), minSdk 26, target/compile latest stable SDK.
- [x] 0.3 Gradle Kotlin DSL + `libs.versions.toml`; add Hilt, Room, Navigation Compose, Coroutines, DataStore, WorkManager, kotlinx-serialization, Coil.
- [x] 0.4 Create modules (see `ARCHITECTURE.md` §3) with convention plugins (`build-logic/`).
- [x] 0.5 Add ktlint + detekt; GitHub repo + `.gitignore` (exclude `local.properties`, keystores).
- [x] 0.6 GitHub Actions CI: build + unit tests + lint on every push.
- [x] 0.7 Supabase: create `hisaab-dev` project; save URL + anon key in `local.properties` → `BuildConfig`.
- [x] 0.8 `core:common`: `Money` (paise), `MoneyFormatter` (en-IN), `DispatcherProvider`, `Result` helpers + unit tests.
- [x] 0.9 `core:designsystem`: theme (colors, type, shapes from `DESIGN.md`), light/dark, `HAmountText`, `HSummaryCard`.
**✅ Exit:** app opens to a themed blank screen; CI green.

## Phase 1 — Core: Auth, Family, Expenses (Week 1–2)
- [x] 1.1 Supabase SQL migration: tables + RLS policies (`supabase/migrations/001_init.sql`).
- [x] 1.2 `core:network`: supabase-kt client (Auth + Postgrest), DTOs.
- [ ] 1.3 `feature:auth`: language pick, onboarding slides, phone OTP + Google sign-in, session restore.
- [ ] 1.4 `feature:family`: create family (invite code), join by code, members list, roles.
- [x] 1.5 `core:database`: Room entities + DAOs (families, members, categories, expenses, sync_queue); seed default categories (en + hi names).
- [x] 1.6 `core:domain`: models + use cases `AddExpense`, `UpdateExpense`, `DeleteExpense`, `ObserveMonthExpenses`, `ObserveMonthSummary`.
- [x] 1.7 `core:data`: `ExpenseRepository` (Room first + sync_queue).
- [x] 1.8 `SyncWorker` push/pull + last-write-wins + soft delete. Unit tests with fake server.
- [x] 1.9 `feature:expenses`: Add Expense sheet (`HNumberPad`, chips, defaults), list grouped by date, edit, swipe delete + undo, filters, search.
- [ ] 1.10 Visibility FAMILY/PRIVATE enforced in queries + RLS (test with 2 accounts).
- [x] 1.11 `feature:dashboard`: month switcher, total, vs last month, top categories donut (Vico), per-member bars, recent list.
- [x] 1.12 Budget: set monthly + per-category; 80%/100% notifications.
- [x] 1.13 Hindi strings for all Phase 1 screens.
**✅ Exit (v0.1 internal):** 2 phones in same family add expenses offline, sync works, dashboard correct.

## Phase 2 — Loans, EMI, Calculators (Week 3)
- [x] 2.1 `core:domain` `EmiMath`: EMI formula, amortisation, prepayment, affordability, 0% rate case. **Heavy unit tests** (compare with known bank calculator values).
- [x] 2.2 Room + Supabase: loans, loan_payments (+ RLS, visibility by owner).
- [x] 2.3 `feature:loans`: list + summary (total EMI, burden %, outstanding), detail with schedule, mark paid, add/edit manual form with validation.
- [ ] 2.4 `EmiReminderWorker`: D-3, D-1, D-0 notifications; notification action "Mark paid"; Android 13 notification permission flow.
- [ ] 2.5 Decide & implement encryption for sensitive local fields (measure on low-end emulator; log result in `TESTING.md`).
- [ ] 2.6 `feature:tools`: Calculator, EMI calc (+ pie + table), Prepayment saver, Can I afford?
- [ ] 2.7 Dashboard "Next EMI" card.
- [ ] 2.8 Recurring expenses + `RecurringWorker`.
**✅ Exit:** all loan math tests pass; reminders fire on time.

## Phase 3 — Smart input: Voice & Scan (Week 4)
- [ ] 3.1 `core:ml` `VoiceParser`: Hinglish/Hindi/English numbers ("200", "2k", "do sau", "dedh hazaar"), dates (aaj, kal, parso, yesterday), category keywords. 50+ unit test phrases.
- [ ] 3.2 Voice add screen with `SpeechRecognizer` (en-IN, hi-IN), mic permission at use time, preview → edit → save.
- [ ] 3.3 ML Kit Text Recognition (bundled), camera screen (CameraX), receipt parser (TOTAL/₹ regex, date), confirm card.
- [ ] 3.4 App lock (biometric / device PIN) toggle.
**✅ Exit:** 80%+ of test voice phrases parse correctly on device.

## Phase 4 — ML insights + Cloud AI (Week 5–6)
- [ ] 4.1 Build training dataset (`ml/training/dataset.csv`, ~2–5k labelled Indian expense phrases/merchants).
- [ ] 4.2 Train small text classifier → export `.tflite` (< 2 MB) → add to `core:ml` assets; `CategoryClassifier` with LiteRT; benchmark < 30 ms on low-end.
- [ ] 4.3 Personal learning: save corrections to `ml_feedback`; keyword overrides beat model.
- [ ] 4.4 `InsightEngine` (pure Kotlin): MoM change, spike (z-score), recurring detect, 3-mo forecast, budget burn rate, EMI burden. Unit tests.
- [ ] 4.5 Choose AI provider (check current free tier + pricing); write Edge Functions `ai-monthly-report`, `ai-loan-extract`, `ai-voice-fallback` with strict JSON schema + server validation + rate limit.
- [ ] 4.6 On-device masking of account numbers/phones before any AI call.
- [ ] 4.7 `feature:insights`: Monthly Report Card screen, `HInsightCard`s, disclaimer; offline fallback to on-device insights.
- [ ] 4.8 Loans "Fill with AI" (voice/scan → extract → review with `HAiBadge`).
- [ ] 4.9 `feature:goals`: create goal, add savings, progress jar.
**✅ Exit (v0.5 closed test):** report generates for a test family with real numbers; AI never saves without confirm.

## Phase 5 — Polish, performance, release (Week 7–8)
- [ ] 5.1 Baseline Profile + Startup Profile module; measure cold start (Macrobenchmark).
- [ ] 5.2 R8 full mode, resource shrink; check AAB size < 20 MB.
- [ ] 5.3 Low-end pass: 2 GB emulator, scroll jank check (JankStats), memory < 150 MB.
- [ ] 5.4 Accessibility pass (TalkBack, 200% font, contrast).
- [ ] 5.5 Crash reporting (Firebase Crashlytics or Sentry free tier) + privacy-safe analytics.
- [ ] 5.6 Settings: export CSV/PDF, delete account & data (server + local).
- [ ] 5.7 Privacy policy page (host on GitHub Pages), Data safety form answers.
- [ ] 5.8 Play Console: pay one-time $25, identity verification, app listing (en + hi), screenshots, feature graphic.
- [ ] 5.9 Closed test: **12+ testers opted in for 14 continuous days** (personal account rule) — start recruiting in Phase 4.
- [ ] 5.10 Fix bugs from `TESTING.md`; apply for production access; release v1.0.
- [ ] 5.11 Monetisation: Play Billing for Pro, AdMob in free tier (not on add screens).

## Phase 6 — After launch (backlog)
- [ ] SMS auto-capture (apply "SMS-based money management" declaration; only if approved)
- [ ] Home-screen widget (Glance)
- [ ] Supabase Realtime live updates
- [ ] More languages (Bengali, Marathi, Tamil)
- [ ] Split bills between members
- [ ] Festival season warnings

---

## Progress log
| Date | Phase.Task | Note |
|------|-----------|------|
| 2026-11-09 | — | Docs created |
| 2026-11-09 | 0.1 | Android Studio + SDK installed (SDK at `F:\APPLICATIONS\INSTALLATIONS\Android\Sdk`). Low-end 2 GB API 26 emulator still to create. |
| 2026-11-09 | 0.2–0.6, 0.8, 0.9 | Project, catalog, build-logic, core modules, ktlint/detekt, CI, `Money`/`MoneyFormatter`/dispatchers/Result (17 tests), theme + `HAmountText`/`HSummaryCard`. AGP 9.5.1, Kotlin 2.4.21, compileSdk 37. Feature modules created when each feature starts. Gradle daemon pinned to JDK 21 (detekt). |
| 2026-11-09 | 0.7 | `BuildConfig.SUPABASE_URL/ANON_KEY` wired from `local.properties`. Supabase project itself not created yet. |
| 2026-11-09 | 0.7, 1.1 | `hisaab-dev` created by Agniv (Singapore region). Migrations 001 (tables, RLS, create/join RPCs) + 002 (helpers in private schema) applied. Building 1.5–1.7 (offline) before 1.2–1.4 (auth) so screens can be seen sooner. |
| 2026-11-09 | 1.9 (part) | `feature:expenses`: Add Expense sheet (keypad, categories, date, mode, note, Only me) + this month's total and list, long-press to delete. Runs as a phone-only family until login (1.3). Category icons are emoji for now. |
| 2026-11-09 | 1.9 | Edit (tap row), swipe left to delete + Undo, month switcher, search (note / category en+hi), mode + category filters, grouped by day with day totals. Member filter waits for family (1.4). Login decision: Google + email code now, phone OTP before launch. |
| 2026-11-09 | 1.2–1.4 | supabase-kt client; email-code login (needs custom SMTP so the template can show {{ .Token }}); Google button appears once GOOGLE_WEB_CLIENT_ID is in local.properties. Family setup after login: create (re-uses phone ids), join by code, or auto-link an existing membership; local expenses move into the family and are re-queued. Family sheet: invite code + share, members, admin badge. Sign-out deferred until sync (1.8) so no unsynced data is lost. Language-pick + onboarding slides of 1.3 still to do. |
| 2026-11-09 | 1.8 | SyncEngine: push queue in batches (dedupe per row), pull changed rows by server updated_at (skips rows with a pending local edit), refresh members. WorkManager: debounced 5 s after writes, on app start, every 6 h, network-only, exponential backoff. 5 Robolectric tests with a fake server. Not yet: categories/budgets sync, pull-to-refresh. |
| 2026-11-09 | 1.11 (part) | Home now has a top-categories donut (plain Canvas `HDonut`, not Vico: smaller and enough for a donut) and a who-spent bar card (hidden for 1-person families). Lives in feature:expenses for now to avoid feature→feature deps; budget bar comes with 1.12. |
| 2026-11-09 | Brand | App renamed **Kinly** (display name, login, share text). Adaptive icon cut from the logo (white K+house+family on Kinly green gradient; monochrome layer reuses the glyph). Logo files in `DOCS/brand/`. Budget plumbing started (family budget synced via sync queue); budget UI + alerts done below. All .md docs moved to `DOCS/` (CLAUDE.md, AGENTS.md, README.md stay at root: tools look for them there). |

| 2026-11-09 | 1.12 | Family monthly budget: card with progress bar (green / amber from 80% / red from 100%, words + ▲ as well as colour), admins set/change/remove it, syncs via the queue. `BudgetAlertMonitor` (runs while the app process lives, reads Room, so works offline) notifies once at 80% and once at 100% per month; remembers the last level so it never repeats, resets if spending drops. Android 13+ notification permission is asked right after a budget is set. Per-category limits not done yet (PRD F6 says optional). Background alert when the app process is dead still to do (EmiReminder-style worker in Phase 2). |

| 2026-11-09 | 1.13 + 1.3 rest | Hindi parity check: every English string in all 4 modules has a Hindi one. First launch: language pick (English / हिंदी, both shown bilingually), 3 slides with Skip, then login. Language is stored in SharedPreferences and applied in `attachBaseContext` (needs to be synchronous). Signed-in users never see onboarding. A language switch inside the app (Settings) is still missing; it comes with Settings (5.6). Phase 1 exit test (2 phones in one family) skipped by Agniv for now; privacy rules are covered by DAO + RLS tests and one server check. |

| 2026-11-10 | 2.1 | `EmiMath` in paise with rate in basis points: EMI, schedule (last row absorbs rounding, ends at exactly ₹0), balance after n, prepay (shorter loan or smaller EMI), max loan for an EMI, affordability (30% caution, >40% risky). 15 tests incl. bank-calculator values and invariants over 384 loans. |
| 2026-11-10 | 2.2 | Room v2 (auto-migration, adds `loans` + `loan_payments`), Supabase `004_loans.sql` applied (RLS: PRIVATE only for owner, payments follow their loan; other members can edit FAMILY loans without taking ownership). Paid marks: one row per loan+month with an id made from both, so two phones marking the same EMI never double-count. EMIs due before the loan was added count as paid. Sync pushes loans before payments, pulls with its own clock. Loans move into the family on sign-in. |
| 2026-11-10 | 2.3 | `feature:loans` + bottom tabs (Home / Loans). Summary (EMIs a month, still to repay), loan cards (type, lender, EMI, green paid bar, next due in words + colour), detail (next EMI + Mark paid / Undo, facts, full schedule with ✅ and overdue rows in red), add/edit sheet (type chips, lender, amount, rate %, tenure years/months, optional bank EMI with formula hint, first EMI date, Only me), delete with confirm. en + hi. Burden % waits for a monthly income field (with "Can I afford?" in 2.6). |
