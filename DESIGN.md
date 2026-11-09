# DESIGN.md — Visual & UI System

> Hisaab AI · Material 3 · Simple, warm, Indian-home friendly · Light + Dark

---

## 1. Design principles
1. **Simple first** — a 50-year-old home manager must use it without help.
2. **Big & clear** — large numbers, large touch targets (≥ 48 dp, primary actions 56 dp).
3. **Fast entry** — adding an expense is never more than 3 taps.
4. **Calm money** — no scary red everywhere; red only for real alerts (over budget, EMI due).
5. **Bilingual** — every screen works in English and Hindi; layouts allow 30% longer text.
6. **Light on phone** — flat colors, vectors, small shadows, minimal animation.

## 2. Color tokens
| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `primary` | `#1B7F5C` (deep green) | `#6FD6AE` | Main actions, FAB, active tab |
| `onPrimary` | `#FFFFFF` | `#00382A` | Text on primary |
| `primaryContainer` | `#C9F2E0` | `#005139` | Selected chips, cards highlight |
| `secondary` | `#F2A93B` (saffron) | `#FFC877` | Goals, highlights |
| `tertiary` | `#3E6FD8` (blue) | `#A9C2FF` | Loans/EMI section |
| `error` | `#C62828` | `#FFB4AB` | Over budget, overdue EMI |
| `warning` (custom) | `#E68A00` | `#FFB95C` | 80% budget, EMI due soon |
| `success` (custom) | `#2E7D32` | `#81C784` | Paid, under budget |
| `background` | `#F7F8F6` | `#101413` | App background |
| `surface` | `#FFFFFF` | `#181D1B` | Cards, sheets |
| `onSurface` | `#1A1C1B` | `#E1E3E0` | Main text |
| `onSurfaceVariant` | `#5D635F` | `#BFC9C3` | Secondary text |
| `outline` | `#D5DBD7` | `#3A413D` | Dividers, borders |

Category colors: 12-color palette, each with ≥ 3:1 contrast on surface (defined in `CategoryColors.kt`).
Dynamic color (Material You) **off** by default to keep brand; optional toggle in Settings.

All text must meet **WCAG AA 4.5:1**.

## 3. Typography
Font: **Noto Sans** (Latin) + **Noto Sans Devanagari** (Hindi) — consistent across scripts. Bundle only needed weights (Regular 400, Medium 500, SemiBold 600) as downloadable/variable font to keep APK small.

| Style | Size / Line | Weight | Use |
|-------|-------------|--------|-----|
| `displayAmount` | 40 / 48 sp | 600 | Big total on Home |
| `headlineSmall` | 24 / 32 | 600 | Screen titles |
| `titleMedium` | 16 / 24 | 600 | Card titles |
| `bodyLarge` | 16 / 24 | 400 | Main text |
| `bodyMedium` | 14 / 20 | 400 | Secondary |
| `labelLarge` | 14 / 20 | 500 | Buttons, chips |
| `labelSmall` | 12 / 16 | 500 | Captions |

Money uses **tabular numbers** (`fontFeatureSettings = "tnum"`). Format with Indian grouping: `₹1,25,000.50` (en-IN locale).

## 4. Spacing, shape, elevation
- Spacing scale (dp): `4, 8, 12, 16, 24, 32`. Screen padding 16. Card inner padding 16.
- Corner radius: chips 8, cards 16, sheets 24 (top), FAB 16, buttons full-round.
- Elevation: cards 1 dp, FAB 3 dp, sheets 1 dp + scrim. Prefer tonal color over shadow.
- Icons: Material Symbols Rounded (vector), 24 dp; category icons 20 dp in 40 dp circle.

## 5. Motion
- Durations: 150 ms (small), 250 ms (sheets), 300 ms (screen).
- Only fade/slide; no complex physics.
- Respect "Remove animations" system setting.
- Progress (budget/loan/goal) animates once on first show only.

## 6. Core components (`core:designsystem`)
| Component | Notes |
|-----------|-------|
| `HAmountText` | Formats `Money`, tabular, color by sign/status |
| `HSummaryCard` | Title, big amount, sub-line ("₹2,300 less than last month ▼") |
| `HCategoryChip` | Icon + name, selectable, horizontal scroll row |
| `HExpenseRow` | Icon, title, sub (member • mode • date), amount right; privacy lock icon if PRIVATE |
| `HLoanCard` | Type icon, lender, EMI, next due badge, progress bar paid/total |
| `HProgressBar` | Budget/goal/loan; color = success/warning/error by % |
| `HNumberPad` | Big 3×4 keypad for amount (₹, 0–9, ., ⌫) — faster than system keyboard |
| `HFab` | + add; long-press → voice; menu: Expense / Scan bill / Loan |
| `HEmptyState` | Simple illustration (vector) + one line + action button |
| `HInsightCard` | 💡 icon, title, detail, "Save ~₹X/month" pill |
| `HSegmented` | Month switcher ◀ Nov 2026 ▶ |
| `HDialog` / `HBottomSheet` | Standard confirm & forms |
| `HAiBadge` | Small "AI suggested" tag + "Check before saving" text |

## 7. Screens (wireframe notes)

### 7.1 Onboarding (3 slides + language)
- Language pick: English / हिंदी (big buttons).
- Slides: "Track family spend", "Never miss an EMI", "Save with AI tips". Skip available.

### 7.2 Auth & Family setup
- Phone OTP / Google button.
- "Create family" (name, budget optional) or "Join family" (6-char code / link).

### 7.3 Home (Dashboard)
```
[Family name ▾]                       [🔔]
◀ November 2026 ▶
┌ This month spent ─────────────────┐
│ ₹ 32,450                          │
│ ▼ ₹2,300 less than October        │
│ Budget ███████░░░ 72%  ₹12,550 left│
└───────────────────────────────────┘
Next EMI: Car loan ₹8,200 • in 3 days  [Mark paid]
Top categories   [donut chart + 4 rows]
Who spent        [4 member bars]
💡 Insight card (1)
Recent           [5 HExpenseRow] → See all
                                   (+) FAB
[Home] [Expenses] [Loans] [Tools] [More]
```

### 7.4 Add Expense (bottom sheet)
- Amount (HNumberPad) on top, big.
- Category chips (AI-guessed one preselected, with small "auto" dot).
- Row: Date (Today default) • Paid by (me default) • Mode (UPI default, remembers last).
- Note (optional), Family/Only me toggle.
- [Save] full width. Mic icon to switch to voice.

### 7.5 Voice add
- Big mic pulse circle, live text, parsed preview card → [Edit] [Save].

### 7.6 Scan bill
- Camera preview with frame → result card (amount, date, merchant, category) → confirm.

### 7.7 Expenses list
- Month switcher, search, filter (member, category, mode), grouped by date with day totals. Swipe left → delete (with Undo snackbar).

### 7.8 Loans
- Summary: Total EMIs/month, EMI burden % (color), total outstanding.
- List of `HLoanCard`. Detail: schedule table, paid months ✅, prepayment calculator shortcut.
- Add Loan: [Fill with AI 🎤/📷] or manual form → Review screen with `HAiBadge` on AI-filled fields.

### 7.9 Tools
- Grid of 4 cards: Calculator, EMI, Prepayment, Can I afford?
- EMI result: EMI big, total interest, total payable, pie (principal vs interest), amortisation table (yearly collapse).

### 7.10 Insights / Monthly Report
- Header "October Report Card" + grade emoji.
- Charts: category bar (this vs last month), 6-month trend line.
- 3–5 `HInsightCard`s. Disclaimer footer: "Tips are suggestions, not financial advice."

### 7.11 More
- Family (members, invite, roles), Budget, Goals, Categories, Recurring, Settings (language, theme, app lock, notifications), Privacy & data (export, delete), About.

## 8. States every screen must design
Loading (skeleton, no spinners on lists) • Empty • Error (retry) • Offline banner ("Offline — saved on phone, will sync") • Success snackbar.

## 9. Copy tone
- Short, friendly, no jargon. "EMI due in 3 days" not "Upcoming installment obligation".
- Hindi strings natural, not word-by-word translation.
- Numbers always with ₹ and Indian grouping.

## 10. Accessibility checklist
- Content descriptions for icons/charts (chart has text summary).
- Font scale up to 200% without clipping (test).
- Don't rely only on color: add ▲▼ icons, labels.
- Focus order logical; TalkBack reads amounts as "rupees".

## 11. Assets
- App icon: green rounded square, white ₹ inside a small house outline (adaptive icon, monochrome layer for Android 13+ themed icons).
- Illustrations: simple flat vectors, ≤ 3 colors, all as VectorDrawable.
