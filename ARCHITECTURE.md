# ARCHITECTURE.md — Technical Blueprint

> Hisaab AI · Native Android · Offline-first · Low-end friendly

---

## 1. Big picture

```
┌──────────────────────────── Android App ────────────────────────────┐
│  UI (Jetpack Compose)  ──events──▶  ViewModel  ──▶  Use Cases       │
│         ▲  StateFlow                                   │            │
│         └──────────────────────────────────────────────┘            │
│                                   │                                  │
│                           Repositories                               │
│                 ┌─────────────────┼──────────────────┐               │
│             Room DB          Sync Engine         ML Engine           │
│          (source of truth)  (WorkManager)   (LiteRT + stats)         │
└──────────────────────────────────┼───────────────────────────────────┘
                                   │ HTTPS
┌──────────────────────────── Supabase ───────────────────────────────┐
│  Auth (OTP/Google) │ Postgres + RLS │ Edge Functions ──▶ Cloud AI API │
└──────────────────────────────────────────────────────────────────────┘
```

- **UI reads only from Room.** Network never blocks the screen.
- **Writes** → Room first (instant) → added to `sync_queue` → WorkManager pushes to Supabase.
- **Cloud AI** is called **only** through Supabase Edge Functions (key stays on server).

## 2. Layers (Clean Architecture)
| Layer | Contains | Depends on |
|-------|----------|-----------|
| **UI** | Composables, ViewModels, UiState, navigation | Domain |
| **Domain** | Models, use cases, repository interfaces (pure Kotlin, no Android) | nothing |
| **Data** | Repository impls, Room, Supabase, sync, mappers | Domain |
| **ML** | Classifier, insight engine, voice parser | Domain |

## 3. Gradle modules
```
:app
:core:common        Money, Result, DispatcherProvider, DateUtils
:core:designsystem  Theme, colors, typography, shared components
:core:domain        Models + use cases + repo interfaces
:core:database      Room DB, entities, DAOs, migrations, SQLCipher (sensitive)
:core:network       Supabase client, DTOs, Edge Function API
:core:data          Repositories, SyncEngine, mappers
:core:ml            LiteRT classifier, InsightEngine, VoiceParser, OCR parser
:feature:auth  :feature:family  :feature:dashboard  :feature:expenses
:feature:loans :feature:tools   :feature:insights   :feature:goals
:feature:settings
:baselineprofile    Baseline profile generator
```
Why modules: faster builds, clear boundaries, features can't touch each other's internals.

## 4. Data model

### 4.1 Domain models (Kotlin)
```kotlin
@JvmInline value class Money(val paise: Long)

data class Family(val id: String, val name: String, val inviteCode: String, val monthlyBudget: Money?)
data class Member(val id: String, val familyId: String, val userId: String, val name: String, val role: Role) // ADMIN, MEMBER
data class Category(val id: String, val familyId: String?, val name: String, val icon: String, val color: Long, val isDefault: Boolean)

data class Expense(
  val id: String, val familyId: String, val paidBy: String /*memberId*/,
  val amount: Money, val categoryId: String, val date: LocalDate,
  val note: String?, val mode: PayMode /*CASH, UPI, CARD, OTHER*/,
  val visibility: Visibility /*FAMILY, PRIVATE*/, val source: Source /*MANUAL, VOICE, SCAN, RECURRING*/,
  val recurringId: String?, val updatedAt: Instant, val deleted: Boolean
)

data class Loan(
  val id: String, val familyId: String, val ownerId: String,
  val type: LoanType /*HOME, CAR, BIKE, PERSONAL, EDUCATION, GOLD, CONSUMER, CREDIT_CARD, OTHER*/,
  val lender: String, val principal: Money, val annualRateBps: Int /*8.5% = 850*/,
  val tenureMonths: Int, val emi: Money, val startDate: LocalDate, val dueDay: Int,
  val visibility: Visibility, val updatedAt: Instant, val deleted: Boolean
)
data class LoanPayment(val id: String, val loanId: String, val month: YearMonth, val amount: Money, val paidOn: LocalDate)

data class Budget(val familyId: String, val month: YearMonth, val total: Money, val perCategory: Map<String, Money>)
data class Goal(val id: String, val familyId: String, val name: String, val target: Money, val saved: Money, val deadline: LocalDate?)
data class Recurring(val id: String, val template: Expense, val rule: RecurRule /*DAILY, WEEKLY, MONTHLY(day)*/)
data class Insight(val id: String, val familyId: String, val month: YearMonth, val kind: InsightKind, val text: String, val createdAt: Instant)
```
- Interest stored as **basis points (Int)** → no float errors.
- IDs are **UUID v4 generated on device** → safe offline creation.

### 4.2 Room tables
`families, members, categories, expenses, loans, loan_payments, budgets, goals, recurring, insights, sync_queue, ml_feedback`

`sync_queue(id, table, rowId, op /*UPSERT, DELETE*/, payloadJson, attempts, createdAt)`
`ml_feedback(text, chosenCategoryId, createdAt)` → used to personalise the classifier.

Indexes: `expenses(familyId, date)`, `expenses(categoryId)`, `loans(familyId)`.

### 4.3 Supabase (Postgres) tables
Same tables (minus `sync_queue`, `ml_feedback`) with `updated_at timestamptz`, `deleted boolean`.

**Row Level Security (must):**
```sql
-- user can only see rows of families they belong to
create policy "family read" on expenses for select using (
  family_id in (select family_id from members where user_id = auth.uid())
  and (visibility = 'FAMILY' or paid_by_user = auth.uid())
);
-- similar insert/update/delete policies; loans use owner_user instead of paid_by_user
```
Service-role key is **never** in the app.

## 5. Sync engine
- **Push:** WorkManager `SyncWorker` (constraint: network connected) reads `sync_queue` in order, upserts to Supabase, removes on success, exponential backoff on failure.
- **Pull:** fetch rows where `updated_at > lastPulledAt` per table; upsert into Room.
- **Conflict rule:** last-write-wins by `updated_at` (server time on write). Deletes are soft (`deleted = true`).
- **Triggers:** app start, after local write (debounced 5 s), periodic every 6 h, pull-to-refresh.
- Optional later: Supabase Realtime for live family updates.

## 6. ML & AI design

### 6.1 On-device (free, offline)
| Part | How | Runs |
|------|-----|------|
| **Category classifier** | Small LiteRT text model (< 2 MB) trained on Indian merchant/note words (Swiggy→Food Outside, doodh→Milk). Plus **personal rules**: `ml_feedback` keyword map overrides model. | On save/typing, `Dispatchers.Default` |
| **Insight engine** | Pure Kotlin stats: month-over-month change per category, z-score spike detection, recurring detection, 3-month moving average forecast, budget burn rate, EMI burden %. | `InsightWorker` daily + on month end |
| **Voice parser** | Android `SpeechRecognizer` (en-IN / hi-IN) → rule parser: numbers (incl. "do sau", "200", "2k"), dates ("aaj", "kal", "yesterday"), category words. | On device |
| **Receipt OCR** | ML Kit Text Recognition (bundled Latin model; Devanagari model optional) → regex for TOTAL/Grand Total/₹ amounts + dates. | On device |

Model training (offline, on PC): small dataset CSV → TensorFlow/Keras text classifier → convert to `.tflite` → put in `core/ml/src/main/assets/`. Version the model file.

### 6.2 Cloud AI (via Edge Functions)
| Edge function | Input | Output |
|---------------|-------|--------|
| `ai-monthly-report` | Aggregated JSON: category totals (this + last 3 months), budget, EMI burden %, goals progress, language | 3–5 tips JSON `{title, detail, estSavingPaise}` |
| `ai-loan-extract` | Voice text or OCR text of loan letter (account numbers masked on device first) | Loan fields JSON + confidence |
| `ai-voice-fallback` | Voice text when local parser confidence low | Expense fields JSON |

Rules: strict JSON schema in prompt, validate on server **and** on device, rate-limit per family (e.g., report 1/month free), cache results in `insights`. AI provider is swappable (one adapter inside the edge function). Provider + free-tier limits to be confirmed at build time.

## 7. Background work (WorkManager)
| Worker | When |
|--------|------|
| `SyncWorker` | after writes, periodic 6 h, network required |
| `RecurringWorker` | daily 00:30 → create due recurring expenses |
| `EmiReminderWorker` | daily 09:00 → schedule/post reminders for D-3, D-1, D-0 |
| `InsightWorker` | daily light stats; full report on 1st of month |
| `ModelUpdateWorker` | rebuild personal keyword map from `ml_feedback` weekly |

## 8. Security
- Supabase Auth JWT stored by supabase-kt session manager (encrypted storage).
- Sensitive local fields (loan lender/amounts) → Room with SQLCipher **or** Android Keystore-encrypted columns (decide in Phase 2; measure speed on low-end).
- Optional biometric app lock (`androidx.biometric`).
- `android:allowBackup` rules exclude DB tokens; `usesCleartextTraffic=false`.
- R8 full mode + obfuscation in release.
- No restricted permissions (no SMS, no call log). Permissions used: `INTERNET`, `POST_NOTIFICATIONS`, `RECORD_AUDIO` (voice, ask at use), `CAMERA` (scan, ask at use), `USE_BIOMETRIC`.

## 9. Performance plan (low-end)
- Baseline Profiles + Startup Profiles for faster start and scroll.
- R8 shrink, resource shrink, WebP/vector icons only, no large images.
- Lazy lists with stable keys; `@Immutable`/`@Stable` UI models; avoid recomposition storms.
- Paging 3 for long expense history.
- Heavy work in WorkManager; results cached in Room.
- App Startup library to lazy-init ML Kit / LiteRT only when needed.
- Targets: cold start < 2 s, frame time < 16 ms, memory < 150 MB on 2 GB device.

## 10. Navigation (main graph)
```
Splash → (Onboarding | Auth) → FamilySetup → Main(BottomBar)
Main: Home | Expenses | Loans | Tools | More
Home → AddExpense(sheet) / VoiceAdd / ScanBill / Report
Loans → LoanDetail → AddLoan(manual | AI fill) → Review
Tools → Calculator | EmiCalc | PrepaymentCalc | AffordCalc
More → Family | Budget | Goals | Categories | Settings | Privacy | About
```

## 11. Environments & config
- `local.properties` (not committed): `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
- Build types: `debug` (dev Supabase project), `release` (prod project).
- Edge function secrets: `AI_API_KEY`, `AI_MODEL`.

## 12. Repo layout
```
/app /core/* /feature/* /baselineprofile
/supabase/migrations/*.sql  /supabase/functions/<name>/index.ts
/ml/training/ (notebook, dataset.csv, export script)
/docs → PRD.md ARCHITECTURE.md DESIGN.md IMPLEMENTATION_PLAN.md TESTING.md
CLAUDE.md AGENTS.md README.md
```
