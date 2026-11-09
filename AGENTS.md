# CLAUDE.md — Agent Instructions for Hisaab AI

> Read this file first, every session. It tells any AI coding agent (Claude, Codex, etc.) how to work in this repo.
> `AGENTS.md` should be a copy of (or symlink to) this file.

---

## 1. Project in one paragraph
Hisaab AI is a native Android app (Kotlin + Jetpack Compose) for Indian families to track shared expenses, EMIs and loans, with on-device ML insights and cloud-AI saving tips. Offline-first, must run smoothly on low-end phones (Android 8+, 2 GB RAM).

## 2. Source-of-truth docs (read before coding)
| File | Use it for |
|------|-----------|
| `PRD.md` | What to build and why. Feature IDs (F1…F20). |
| `ARCHITECTURE.md` | Modules, layers, data model, sync, ML, AI. |
| `DESIGN.md` | Colors, type, spacing, components, screens. |
| `IMPLEMENTATION_PLAN.md` | Build order. Do tasks in order. Tick boxes when done. |
| `TESTING.md` | Test rules, checklists, bug log. Add every bug found. |

If code and docs disagree → **ask the human**, don't guess. Update the doc when a decision changes.

## 3. Tech stack (do not change without asking)
- Kotlin (latest stable), Jetpack Compose + Material 3
- MVVM + Clean Architecture (UI → Domain → Data)
- Hilt (DI), Room (local DB), Kotlin Coroutines + Flow
- WorkManager (background), DataStore (prefs)
- Navigation Compose (type-safe routes)
- Supabase (Auth, Postgres, Edge Functions) via supabase-kt + Ktor
- ML Kit Text Recognition (on-device OCR), LiteRT (on-device classifier)
- Vico (charts), Coil (images)
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- Use **latest stable** versions at build time. Never alpha/beta unless asked.

## 4. Golden rules
1. **Money = `Long` paise.** Never `Float`/`Double` for stored money. Use `Money` value class in `core:common`.
2. **Offline-first.** UI reads only from Room. Network writes go through the sync queue.
3. **No work on main thread.** DB, network, ML, parsing → `Dispatchers.IO`/`Default` via injected dispatchers.
4. **AI suggests, user confirms.** Never auto-save AI output.
5. **No secrets in the app.** AI API keys live only in Supabase Edge Function secrets. Only the Supabase *publishable/anon* key goes in the app (via `local.properties` → `BuildConfig`, never committed).
6. **Privacy:** send only aggregated numbers to cloud AI. Never send names, phone numbers, notes, account numbers.
7. **Respect `visibility`:** `PRIVATE` items must never show to other family members (enforced in DB RLS **and** in queries).
8. **Low-end first:** test on a 2 GB RAM emulator profile. Avoid heavy animations, huge images, recompositions in lists (use `key`, stable classes, `remember`).
9. **Small PRs / small steps.** One task from the plan at a time. Build must pass after every step.
10. **Don't invent requirements.** If unclear, ask in super simple language.

## 5. Code style
- Kotlin official style; ktlint + detekt must pass.
- Package by feature: `feature/expenses`, `feature/loans`, …
- Composables: `PascalCase`, stateless where possible; state hoisted to ViewModel.
- ViewModel exposes one `StateFlow<UiState>` + `onEvent(event)`.
- Use `sealed interface` for UI events and results.
- Repositories return `Flow<T>` for reads and `Result<T>` for writes.
- No `!!`. No `GlobalScope`. No hard-coded strings in UI → `strings.xml` (en + hi).
- All user-visible text must have Hindi translation in `values-hi/strings.xml`.
- Comments explain **why**, not what.

## 6. Folder map (short)
```
app/                      → MainActivity, NavHost, Application (Hilt)
core/common/              → Money, Result, dispatchers, date utils
core/designsystem/        → theme, colors, type, components
core/database/            → Room DB, DAOs, entities, migrations
core/network/             → Supabase client, DTOs
core/data/                → repositories, sync engine
core/domain/              → use cases, models (pure Kotlin)
core/ml/                  → LiteRT classifier, insight engine, voice parser
feature/auth/ family/ dashboard/ expenses/ loans/ tools/ insights/ goals/ settings/
supabase/                 → SQL migrations, RLS policies, edge functions
```

## 7. Commands
```bash
./gradlew assembleDebug            # build debug APK
./gradlew installDebug             # install on device
./gradlew test                     # unit tests
./gradlew connectedAndroidTest     # instrumented/UI tests
./gradlew ktlintCheck detekt       # lint
./gradlew :app:generateBaselineProfile  # baseline profile (release prep)
./gradlew bundleRelease            # Play Store AAB
```
Windows: use `gradlew.bat` instead of `./gradlew`.

## 8. Definition of Done (every task)
- [ ] Code builds, no new warnings
- [ ] Unit tests added/updated and passing
- [ ] Works offline
- [ ] Strings in en + hi
- [ ] Checked on low-end emulator (2 GB RAM, API 26)
- [ ] `IMPLEMENTATION_PLAN.md` box ticked
- [ ] Any bug found → logged in `TESTING.md`

## 9. Things never to do
- Don't add `READ_SMS` or other restricted permissions.
- Don't store the AI key or Supabase service-role key in the app or repo.
- Don't use `Double` for money math.
- Don't block UI waiting for network or AI.
- Don't delete user data without confirmation.
- Don't upgrade major library versions without asking.

## 10. How to talk to the human (Agniv)
- Short, point-wise, super simple language.
- Say what you changed and how to test it in 2–3 lines.
- If stuck, ask a clear question with options.
