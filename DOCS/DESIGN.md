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

> 2026-11-16 (later): the whole UI now follows the 16 design boards in `/design` (one PNG per screen). Code is the source of truth: `core/designsystem` theme (`Color.kt`, `Type.kt` with Inter, `Shape.kt`) and components. Dark theme: background #111A16, cards #1B2721, light-green primary #5FD39F with dark text on buttons (the boards' white-on-green is only 3.4:1).

> 2026-11-16: the light theme now follows the Kinly design spec, and `Color.kt` is the source of truth. Primary `#1B7F5B`, primaryContainer `#E6F3EC`, background `#FAFAF6`, onSurface `#17251F`, onSurfaceVariant `#68766F`, outlineVariant (card borders) `#E5EAE6`, outline (field borders) `#7D8983`, secondary/accent `#E9B872` (never as text), error `#B84040`, warning `#8F5E12`, success `#1E7A4B`. Shapes: cards 20, sheets 28, chips 10, buttons 12. Cards are flat with a 1 dp border (`HCard`). The table below is the older palette; its dark-theme values are still in use.
| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `primary` | `#1B7F5C` (deep green) | `#6FD6AE` | Main actions, FAB, active tab |
| `onPrimary` | `#FFFFFF` | `#00382A` | Text on primary |
| `primaryContainer` | `#C9F2E0` | `#005139` | Selected chips, cards highlight |
| `secondary` | `#F2A93B` (saffron) | `#FFC877` | Goals, highlights |
| `tertiary` | `#3461C4` (blue) | `#A9C2FF` | Loans/EMI section |
| `error` | `#C62828` | `#FFB4AB` | Over budget, overdue EMI |
| `warning` (custom) | `#9E5C00` | `#FFB95C` | 80% budget, EMI due soon |
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
- Hub: "Tools" + subtitle, 2×2 grid (EMI, Prepayment, Can I afford?, Interest / Savings) with outlined icons in soft green circles, a wide Calculator row, "Recently used" (last results, kept on the phone only, DataStore via `ToolsHistory` in core:data), and a Kinly Tip (real numbers from the family's first open loan, else a general tip).
- Tool pages (boards, Nov 2026): back arrow + big title + subtitle + green glow picture; one input card with rows (icon tile, label + helper, value box on the right: ₹ with Indian commas / % / Years-Months, slider with min/max under it); live result on a `primaryContainer` card with a primary border; icon + text empty state.
- EMI: EMI / month, donut with total in the middle and principal/interest %, total interest, total payment; year-by-year table (opening, principal, interest, closing) showing 3 years + "View all"; Save (to Recently used) and Share (plain text).
- Prepayment: EMIs-paid stepper, extra payment, Finish sooner / Smaller EMI cards, "You save" banner, Before vs After bars (total interest, tenure or EMI) with saving pills, tip line.
- Can I afford it?: income, current EMIs, new loan, rate, tenure; half-circle gauge with bands that match the logic (green under 30%, amber 30–40%, red over 40%), level chip + sentence, new EMI, money left each month, advice.
- Calculator: display with expression above answer and a history menu; GST +18 / +12 / +5% and Discount chips; equal keys incl. 00; haptic tap.
- Interest / Savings: FD (quarterly compounding, simple interest for leftover months) or monthly saving (SIP style).
- Only theme tokens; no hard-coded colours.

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
