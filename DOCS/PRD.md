# PRD — Hisaab AI (working name)

> Product Requirements Document · v1.0 · Owner: Agniv Sarkar · Date: 2026-11-09
> Working name "Hisaab AI" — can be renamed later (search & replace).

---

## 1. One-line pitch
A family expense app for Indian homes. Every family member adds spending, EMIs and loans in one shared place. On-device ML learns the spending pattern and AI suggests simple ways to save.

## 2. Problem
- Indian families track home spending on paper, WhatsApp or memory.
- Each member spends separately → nobody sees the full monthly picture.
- EMIs and loans (home, car, phone, credit card, gold) are scattered → missed due dates, late fees, too much debt.
- Existing trackers are built for one person, not a family, and give no clear saving advice.

## 3. Goals
| # | Goal | Measure |
|---|------|---------|
| G1 | Family sees total monthly spend in one screen | Dashboard loads < 1 s on low-end phone |
| G2 | Adding an expense is very fast | Manual add ≤ 3 taps, voice add ≤ 5 s |
| G3 | No missed EMI | Reminder 3 days + 1 day before every due date |
| G4 | Real saving tips | ≥ 3 personalised tips per month |
| G5 | Works on cheap phones | Android 8+, 2 GB RAM, APK < 20 MB, cold start < 2 s |
| G6 | Free to build and run at start | ₹0 server cost up to early users (free tiers) |

## 4. Non-goals (v1)
- No bank account linking / Account Aggregator.
- No UPI payments inside the app.
- No investment advice (stocks, mutual funds).
- No SMS reading in v1 (Play Store restricted permission — see §11).
- No iOS in v1.

## 5. Target users (personas)
1. **Home manager (Mother, 35–55)** — handles groceries, milk, maid, gas. Wants simple Hindi/English UI, big buttons, voice.
2. **Earning member (Father/Son, 25–55)** — pays EMIs, bills, rent. Wants EMI tracker, totals, alerts.
3. **Student/young member (18–25)** — small personal spends, wants privacy for some items.
4. **Family admin** — creates the family, invites members, sets budget.

## 6. Features

### P0 — Must have (MVP)
| ID | Feature | Details |
|----|---------|---------|
| F1 | Sign up / Login | Phone OTP or Google sign-in (Supabase Auth). |
| F2 | Family group | Create family, invite via code/link, roles: Admin, Member. Leave/remove member. |
| F3 | Add expense (manual) | Amount, category, date, note, paid-by, payment mode (Cash/UPI/Card). ≤ 3 taps with smart defaults. |
| F4 | Categories | Default Indian set: Groceries, Milk, Vegetables, Rent, Electricity, Water, Gas/LPG, Mobile/Internet, Maid/Help, Fuel, Transport, Food Outside, Shopping, Medical, Education, Festivals/Gifts, EMI, Entertainment, Others. Custom categories allowed. |
| F5 | Dashboard | This month total, vs last month, top categories, per-member split, budget left. |
| F6 | Monthly budget | Family budget + optional per-category limits. Alert at 80% and 100%. |
| F7 | EMI & Loan manager | Loan type, lender, principal, interest %, tenure, EMI amount, due day, start date. Paid vs remaining, progress bar, next due. |
| F8 | EMI reminders | Local notification 3 days and 1 day before, and on due day. Mark as paid. |
| F9 | Calculators | (a) Basic calculator (b) EMI calculator + amortisation table (c) Prepayment saver (d) "Can I afford this loan?" |
| F10 | Privacy per item | Each expense/loan: "Family" or "Only me". |
| F11 | Offline-first | Everything works offline; syncs when online. |
| F12 | App lock | Fingerprint / device PIN (optional). |

### P1 — Should have
| ID | Feature | Details |
|----|---------|---------|
| F13 | Voice entry | Hinglish/English/Hindi: "aaj 200 ka doodh" → ₹200, Milk, today. On-device parser first, AI fallback. |
| F14 | Bill / receipt scan | ML Kit OCR on-device → total, date, merchant → category guess. |
| F15 | AI quick-fill for loans | Speak or scan a loan letter → AI fills loan form. User confirms before save. |
| F16 | Auto category (ML) | On-device LiteRT text classifier: note/merchant → category. Learns from user corrections. |
| F17 | Pattern insights | On-device stats: spikes, trends, recurring spends, next-month forecast. |
| F18 | AI Monthly Report Card | Once a month, cloud AI turns stats into 3–5 simple saving tips (in user's language). |
| F19 | Saving goals | Goal name, target, date, progress jar. |
| F20 | Recurring expenses | Rent, maid, milk, internet — auto-add monthly/daily. |
| F22 | Udhaar (who owes whom) | Track money others owe the family ("you will get") and money the family owes, e.g. fish or groceries bought on credit ("you will give"): person/shop, amount, date, note, part payments, settled, optional due-date reminder, Family or Only me. "Bought on credit" in Add expense creates the entry. (Asked by Agniv 2026-11-10.) |
| F21 | Passbook / statement import | Upload a bank passbook or statement as a photo or PDF. The app reads each line (date, amount, description), matches it against expenses and EMI payments already in Kinly, shows "already added" vs "missing", and lets the user review and add the missing ones. Never auto-saves. Account numbers are masked on the phone before anything leaves it. (Asked by Agniv 2026-11-10.) |

### P2 — Nice to have (later)
- Festival/wedding season spend warnings.
- Export PDF/CSV report.
- SMS auto-capture (only after Play Store approval).
- Home-screen widget (today's spend, next EMI).
- More languages (Bengali, Marathi, Tamil…).
- Split bill between members.

## 7. Key user flows
1. **Onboarding:** Language → Login → Create or Join family → Set monthly budget (skip allowed) → Dashboard.
2. **Add expense:** FAB (+) → amount keypad → category chip (pre-guessed) → Save. Or long-press FAB → voice.
3. **Add loan:** Loans tab → + → "Fill with AI" (voice/scan) or manual → Review → Save → reminders auto-scheduled.
4. **Monthly report:** 1st of month → notification "Your October report is ready" → Report screen with charts + AI tips.
5. **EMI calculator:** Tools tab → EMI → enter P, rate, years → EMI, total interest, table.

## 8. Calculation rules (must be exact)
- **EMI:** `EMI = P × r × (1+r)^n / ((1+r)^n − 1)` where `r = annualRate / 12 / 100`, `n = months`. If `r = 0` → `EMI = P / n`.
- **Amortisation (month i):** `interest_i = balance × r`; `principal_i = EMI − interest_i`; `balance -= principal_i`. Last month adjusts for rounding.
- **Money:** store as **Long paise** (₹1 = 100). Never Float/Double for stored money. Round half-up to paise for display.
- **Debt burden %:** `total monthly EMIs / total monthly income × 100`. Warn > 40% (configurable).

## 9. AI behaviour rules
- AI **suggests**, user **confirms**. AI never saves data on its own.
- AI tips must use the family's real numbers; no generic advice.
- Not financial/legal advice — show small disclaimer on AI screens.
- Send **only aggregated numbers** (category totals, trends) to cloud AI. No names, phone numbers, notes or loan account numbers.
- If AI fails/offline → show on-device insights only. App never breaks.

## 10. Non-functional requirements
| Area | Requirement |
|------|-------------|
| Performance | Cold start < 2 s, 60 fps scroll on 2 GB RAM phone, no work on main thread. |
| Size | Release APK/AAB download < 20 MB (ML model < 2 MB). |
| Offline | 100% core features offline. |
| Security | Supabase Row Level Security; encrypted local DB for sensitive fields; HTTPS only; no secrets in APK. |
| Privacy | Clear privacy policy; delete account + data option; "Only me" items never leave owner's view. |
| Accessibility | Font scaling, TalkBack labels, contrast ≥ 4.5:1, touch targets ≥ 48 dp. |
| Language | English + Hindi at launch. |
| Min SDK | Android 8.0 (API 26). |

## 11. Constraints & risks
| Risk | Mitigation |
|------|-----------|
| READ_SMS is a restricted Play permission; "SMS-based money management" exception is reviewed case by case. | Ship v1 without SMS. Apply later with declaration form. |
| Personal Play account needs closed test with 12 testers for 14 days before production. | Recruit friends/classmates early (Phase 5). |
| Play developer account: one-time $25 fee. | Only unavoidable cost. |
| Free-tier limits (Supabase, AI API) can change. | Check limits at build time; cache AI results; call AI max once/month per family. |
| Wrong AI extraction for loans. | Always show review screen; validate numbers. |

## 12. Monetisation
- **Free:** 1 family, up to 4 members, manual + voice add, EMI tracker, calculators, basic insights.
- **Pro (₹49–99/month or yearly):** AI report card, bill scan, AI loan quick-fill, unlimited members, PDF export, goals.
- Ads (AdMob) only in free tier, never on add-expense screen.

## 13. Success metrics (first 3 months after launch)
- D7 retention ≥ 25%.
- Avg ≥ 4 expenses added per family per week.
- ≥ 30% families add at least one loan.
- Crash-free users ≥ 99.5%.
- Play rating ≥ 4.3.

## 14. Release plan
- v0.1 internal (Phases 1–2) → v0.5 closed test (Phases 3–4) → v1.0 production (Phase 5). See `IMPLEMENTATION_PLAN.md`.
