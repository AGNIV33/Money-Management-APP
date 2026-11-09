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
- [ ] 0.7 Supabase: create `hisaab-dev` project; save URL + anon key in `local.properties` → `BuildConfig`.
- [x] 0.8 `core:common`: `Money` (paise), `MoneyFormatter` (en-IN), `DispatcherProvider`, `Result` helpers + unit tests.
- [x] 0.9 `core:designsystem`: theme (colors, type, shapes from `DESIGN.md`), light/dark, `HAmountText`, `HSummaryCard`.
**✅ Exit:** app opens to a themed blank screen; CI green.

## Phase 1 — Core: Auth, Family, Expenses (Week 1–2)
- [ ] 1.1 Supabase SQL migration: tables + RLS policies (`supabase/migrations/001_init.sql`).
- [ ] 1.2 `core:network`: supabase-kt client (Auth + Postgrest), DTOs.
- [ ] 1.3 `feature:auth`: language pick, onboarding slides, phone OTP + Google sign-in, session restore.
- [ ] 1.4 `feature:family`: create family (invite code), join by code, members list, roles.
- [ ] 1.5 `core:database`: Room entities + DAOs (families, members, categories, expenses, sync_queue); seed default categories (en + hi names).
- [ ] 1.6 `core:domain`: models + use cases `AddExpense`, `UpdateExpense`, `DeleteExpense`, `ObserveMonthExpenses`, `ObserveMonthSummary`.
- [ ] 1.7 `core:data`: `ExpenseRepository` (Room first + sync_queue).
- [ ] 1.8 `SyncWorker` push/pull + last-write-wins + soft delete. Unit tests with fake server.
- [ ] 1.9 `feature:expenses`: Add Expense sheet (`HNumberPad`, chips, defaults), list grouped by date, edit, swipe delete + undo, filters, search.
- [ ] 1.10 Visibility FAMILY/PRIVATE enforced in queries + RLS (test with 2 accounts).
- [ ] 1.11 `feature:dashboard`: month switcher, total, vs last month, top categories donut (Vico), per-member bars, recent list.
- [ ] 1.12 Budget: set monthly + per-category; 80%/100% notifications.
- [ ] 1.13 Hindi strings for all Phase 1 screens.
**✅ Exit (v0.1 internal):** 2 phones in same family add expenses offline, sync works, dashboard correct.

## Phase 2 — Loans, EMI, Calculators (Week 3)
- [ ] 2.1 `core:domain` `EmiMath`: EMI formula, amortisation, prepayment, affordability, 0% rate case. **Heavy unit tests** (compare with known bank calculator values).
- [ ] 2.2 Room + Supabase: loans, loan_payments (+ RLS, visibility by owner).
- [ ] 2.3 `feature:loans`: list + summary (total EMI, burden %, outstanding), detail with schedule, mark paid, add/edit manual form with validation.
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
