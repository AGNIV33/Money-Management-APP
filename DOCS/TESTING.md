# TESTING.md — Safety Net & Bug Log

> Rules for testing, checklists before release, and a running bug log.
> Every bug found (by human or agent) goes in §9.

---

## 1. Test pyramid
| Level | Tool | What | Target |
|-------|------|------|--------|
| Unit | JUnit5 / kotlin.test, Turbine (Flow), MockK, kotlinx-coroutines-test | Money, EmiMath, VoiceParser, InsightEngine, use cases, ViewModels, mappers | ≥ 80% coverage on `core:domain`, `core:ml`, `core:common` |
| Integration | Room in-memory DB, fake Supabase | DAOs, repositories, SyncEngine, migrations | All DAOs + sync paths |
| UI | Compose UI Test, Robolectric (fast) | Add expense, loan form validation, calculators | All P0 flows |
| E2E / manual | Real devices + emulators | Full journeys, 2-device family sync | Before each release |
| Performance | Macrobenchmark, Baseline Profile, JankStats | Cold start, scroll, memory | See §5 |
| Security | Manual + Supabase advisors | RLS, secrets, privacy | Before each release |

Run: `./gradlew test` (unit) · `./gradlew connectedAndroidTest` (device) · CI runs unit + lint on every push.

## 2. Must-have unit tests (critical math)

### 2.1 Money
- [ ] ₹0.10 + ₹0.20 == ₹0.30 exactly (paise Long)
- [ ] Format `12550000` paise → `₹1,25,500.00` (en-IN grouping)
- [ ] Negative / zero amounts rejected on save

### 2.2 EMI (verify against a trusted bank EMI calculator; record values here)
| P | Rate | Tenure | Expected EMI | Status |
|---|------|--------|--------------|--------|
| ₹10,00,000 | 8.5% | 20 yr | fill from bank calc | [ ] |
| ₹5,00,000 | 10.5% | 5 yr | fill from bank calc | [ ] |
| ₹1,00,000 | 0% | 12 mo | ₹8,333.33 (last row adjusts) | [ ] |
| ₹50,000 | 14% | 6 mo | fill from bank calc | [ ] |
- [ ] Amortisation: sum of principal parts == P (after last-row adjust)
- [ ] Balance never negative; final balance == 0
- [ ] Prepayment reduces tenure/interest correctly
- [ ] Rate in basis points: 8.5% → 850

### 2.3 VoiceParser (≥ 50 phrases)
| Input | Expected |
|-------|----------|
| "aaj 200 ka doodh" | ₹200, Milk, today |
| "kal sabzi 150 rupaye" | ₹150, Vegetables, yesterday |
| "petrol 2k" | ₹2,000, Fuel, today |
| "do sau pachaas bijli bill" | ₹250, Electricity |
| "dedh hazaar ka gas cylinder" | ₹1,500, Gas/LPG |
| "spent 499 on swiggy yesterday" | ₹499, Food Outside, yesterday |
| "maid salary 3000" | ₹3,000, Maid/Help |
| "आज 300 का राशन" | ₹300, Groceries, today |
- [ ] Low confidence → returns `needsReview = true` (never silent wrong save)

### 2.4 InsightEngine
- [ ] MoM % correct incl. last month = 0 (no divide-by-zero)
- [ ] Spike detection on synthetic data
- [ ] Recurring detection (same amount ±5%, same category, monthly)
- [ ] EMI burden % correct; warning at > 40%

### 2.5 Receipt parser
- [ ] Picks "Grand Total" over "Subtotal"
- [ ] Handles `₹`, `Rs.`, `INR`, commas
- [ ] Date formats: dd/mm/yyyy, dd-MM-yy, "09 Nov 2026"

## 3. Sync & data tests
- [ ] Offline add → online → appears on 2nd device
- [ ] Edit same expense on 2 devices offline → last write wins, no crash, no duplicate
- [ ] Delete offline → syncs as soft delete
- [ ] App killed mid-sync → queue resumes, no data loss
- [ ] Room migration tests for every schema version (`MigrationTestHelper`)
- [ ] Large data: 10,000 expenses → list scrolls smoothly, dashboard < 1 s

## 4. Security & privacy audit (before every release)
- [ ] RLS: user A cannot read family B rows (test with REST call using A's JWT)
- [ ] PRIVATE expense/loan invisible to other family members (app + API)
- [ ] No AI key / service-role key in APK (`apkanalyzer` + grep strings)
- [ ] `local.properties` and keystore not in git
- [ ] AI requests contain only aggregates; account numbers masked (log payload in debug, inspect)
- [ ] Cleartext traffic disabled; all calls HTTPS
- [ ] Logs contain no amounts tied to names, no tokens (release build)
- [ ] Delete account removes server + local data
- [ ] Supabase security advisors: no warnings
- [ ] Permissions list = INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO, CAMERA, USE_BIOMETRIC only (no SMS/Call log)

## 5. Performance budget (test on API 26, 2 GB RAM emulator + one real low-end phone)
| Metric | Target | Result |
|--------|--------|--------|
| Cold start | < 2.0 s | |
| Warm start | < 1.0 s | |
| Dashboard load (1 yr data) | < 1.0 s | |
| Expense list scroll | 0 frozen frames, < 5% janky | |
| Classifier inference | < 30 ms | |
| Memory (PSS) | < 150 MB | |
| AAB download size | < 20 MB | |

## 6. Device / OS matrix
| Device type | API | Must pass |
|-------------|-----|-----------|
| Low-end emulator (2 GB) | 26 | ✅ |
| Mid real phone (any) | 30–33 | ✅ |
| Recent emulator | latest | ✅ |
| Small screen 5" / large 6.7" / tablet | — | layout check |
| Dark mode, Hindi, font 200% | — | ✅ |

## 7. Manual release checklist
- [ ] Fresh install → onboarding → create family → add 5 expenses → dashboard right
- [ ] Join family on 2nd phone with code
- [ ] Add loan manually + via AI fill; review screen shows AI badge
- [ ] EMI reminder fires (change device date to test)
- [ ] All 4 calculators match test table
- [ ] Voice add (en + hi), scan bill (printed receipt)
- [ ] Airplane mode: every core feature works
- [ ] Monthly report generates; offline shows on-device insights
- [ ] App lock works; Pro purchase (test track) works
- [ ] No crash in 30-min monkey test: `adb shell monkey -p com.agniv.hisaab 5000`

## 8. Play Store pre-launch
- [ ] Closed test: ≥ 12 testers opted in for 14 continuous days (personal account)
- [ ] Pre-launch report: no crashes / accessibility errors
- [ ] Data safety form matches real data use
- [ ] Privacy policy URL live

## 9. Bug log
Severity: **S1** crash/data loss/security · **S2** wrong numbers/broken feature · **S3** UI/annoyance · **S4** polish

| ID | Date | Sev | Area | Steps to reproduce | Expected | Actual | Status | Fix (commit) |
|----|------|-----|------|--------------------|----------|--------|--------|--------------|
| BUG-001 | 2026-11-09 | S3 | Build / lint | Run `gradlew detekt` with Gradle daemon on JDK 25 (Android Studio default) | detekt runs | Fails with "25.0.4" / invalid `--jvm-target` (detekt 1.23 max is JDK 22) | Fixed | Daemon pinned to JDK 21 (`gradle/gradle-daemon-jvm.properties` + foojay resolver); detekt `jvmTarget = 17` in build-logic |
| BUG-002 | 2026-11-10 | S2 | Sync / RLS (expenses) | Member B edits a FAMILY expense created by member A; sync pushes it as an upsert | Edit reaches the server | Found by reading, not yet seen live: Postgres checks the INSERT policy on upsert too, and `expenses_insert` needs `owner_user = auth.uid()`, so B's edit is refused and stays in B's queue | Fixed (not yet tried with 2 phones) | Migration 005: `expenses_insert` also accepts a write that carries the stored owner (`private.expense_owner()`) |
| BUG-003 | 2026-11-10 | S4 | Build | `gradlew clean` then `assembleDebug` | Builds | Hilt "SessionRepository cannot be provided": `core:data` KSP output was restored empty | Workaround | `./gradlew :core:data:kspDebugKotlin --rerun` |

## 10. Audit notes / decisions
| Date | Topic | Finding / Decision |
|------|-------|--------------------|
| 2026-11-09 | Setup | Testing doc created |
| 2026-11-09 | Test libs | Unit tests use JUnit 4 + Truth (+ Turbine, coroutines-test later), not JUnit 5: Android instrumented tests and Compose test rules still need JUnit 4, so one framework everywhere. |
| 2026-11-09 | Supabase security | `hisaab-dev` advisors after 001+002: RLS helpers moved to `private` schema. Remaining warnings are expected: `create_family`/`join_family` are meant to be called by signed-in users; `rls_auto_enable()` is Supabase's own function, not ours. |
| 2026-11-09 | Fonts | Phase 0 uses system font; bundled Noto Sans + Devanagari deferred to Phase 5 (DESIGN.md §3). |
| 2026-11-10 | Local data protection (2.5) | Only real secret on the phone is the Supabase refresh token: now Keystore AES-GCM encrypted (5 Robolectric tests: round trip, not readable on disk, legacy migration, damaged data → login again, tamper rejected). Room stays plain SQLite under Android file encryption + app sandbox + backup exclusion; SQLCipher rejected for size/speed on 2 GB phones. Revisit if PRD adds bank account numbers or similar. |
| 2026-11-10 | Who can delete | Decision (Agniv asked for a delete for mistakes): the person who added an expense or loan, or a family admin, may delete it. Other members can edit shared items but not delete. Enforced in the app (button hidden) and on the server (migration 005 trigger refuses the delete). Un-marking a paid EMI is open to anyone who can see the loan. Expense: swipe + Undo, or Delete in the edit sheet with a confirm. Loan: Delete with confirm. |
| 2026-11-10 | Voice assistant (4.11-4.12b) | Privacy design: the Live key lives only as the Supabase secret `GEMINI_LIVE_API_KEY`; the phone only gets a short-lived token locked to the model, audio-only, the finance-only instruction and the 7 tools. Tool answers are totals in whole rupees, goals by percent, udhaar without names, categories default names or Others; `AssistantToolbox.isSafe` blocks any text that is long, has symbols or has a 9+ digit run. Tests: hostile names / notes / phone numbers / injection text in categories, udhaar, goals and loans never appear in any tool answer. Cost control: per-user (20 min) and global (150 min) daily caps before a token is made; keep the key's Google project without billing. Found during the live check: Google Search grounding inside Live is refused on a free key, so current prices use `ai-price-lookup`. **Still to check on a real phone:** echo (assistant hearing itself on the loudspeaker), interruption speed, switching sessions every 5 minutes without a gap, mic permission denied path, silence auto-stop after 45 s, background / screen-off stops the mic. |
