// Kinly: monthly saving tips (IMPLEMENTATION_PLAN 4.5).
//
// The app sends ONLY totals per default category (never names, notes, phone or account numbers).
// This function checks the numbers, asks Gemini for 3-5 short tips, checks the answer, stores it
// (one report per family per month) and returns it. The Gemini key lives only in this function's secrets.
//
// Secrets (Supabase dashboard > Edge Functions > Secrets):
//   GEMINI_API_KEY  required. Without it the function answers 503 "ai_not_configured" and the app keeps
//                   showing its on-device insights.
//   AI_MODEL        optional, default "gemini-2.5-flash".
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

// Only these category names may be sent: stops free text (people, shops) reaching the AI.
const CATEGORIES = new Set([
  "Groceries", "Milk", "Vegetables", "Rent", "Electricity", "Water", "Gas/LPG", "Mobile/Internet",
  "Maid/Help", "Fuel", "Transport", "Food Outside", "Shopping", "Medical", "Education",
  "Festivals/Gifts", "EMI", "Entertainment", "Others",
]);

const MAX_PAISE = 100_000_000_000; // ₹100 crore: far above any household
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MONTH = /^\d{4}-(0[1-9]|1[0-2])-01$/;

type Input = {
  familyId: string;
  month: string;
  language: "en" | "hi";
  totalPaise: number;
  previousTotalPaise: number;
  budgetPaise: number | null;
  emiSharePercent: number | null;
  categories: { name: string; thisPaise: number; lastPaise: number }[];
};

type Tip = { title: string; detail: string; estSavingPaise: number };

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const isPaise = (v: unknown): v is number => Number.isInteger(v) && (v as number) >= 0 && (v as number) <= MAX_PAISE;

function validate(raw: any): Input | string {
  if (!raw || typeof raw !== "object") return "bad_body";
  const allowed = ["familyId", "month", "language", "totalPaise", "previousTotalPaise", "budgetPaise", "emiSharePercent", "categories"];
  if (Object.keys(raw).some((k) => !allowed.includes(k))) return "unexpected_field";
  if (typeof raw.familyId !== "string" || !UUID.test(raw.familyId)) return "bad_family";
  if (typeof raw.month !== "string" || !MONTH.test(raw.month)) return "bad_month";
  if (raw.language !== "en" && raw.language !== "hi") return "bad_language";
  if (!isPaise(raw.totalPaise) || !isPaise(raw.previousTotalPaise)) return "bad_totals";
  if (raw.budgetPaise !== null && !isPaise(raw.budgetPaise)) return "bad_budget";
  if (raw.emiSharePercent !== null && !(Number.isInteger(raw.emiSharePercent) && raw.emiSharePercent >= 0 && raw.emiSharePercent <= 1000)) {
    return "bad_emi";
  }
  if (!Array.isArray(raw.categories) || raw.categories.length === 0 || raw.categories.length > 25) return "bad_categories";
  const seen = new Set<string>();
  for (const c of raw.categories) {
    if (!c || typeof c !== "object" || Object.keys(c).some((k) => !["name", "thisPaise", "lastPaise"].includes(k))) return "bad_category";
    if (typeof c.name !== "string" || !CATEGORIES.has(c.name) || seen.has(c.name)) return "bad_category_name";
    if (!isPaise(c.thisPaise) || !isPaise(c.lastPaise)) return "bad_category_amount";
    seen.add(c.name);
  }
  return raw as Input;
}

const GEMINI_SCHEMA = {
  type: "OBJECT",
  properties: {
    tips: {
      type: "ARRAY",
      minItems: 3,
      maxItems: 5,
      items: {
        type: "OBJECT",
        properties: {
          title: { type: "STRING" },
          detail: { type: "STRING" },
          estSavingPaise: { type: "INTEGER" },
        },
        required: ["title", "detail", "estSavingPaise"],
      },
    },
  },
  required: ["tips"],
};

function prompt(input: Input): string {
  const lang = input.language === "hi" ? "simple Hindi (Devanagari script)" : "simple English";
  return [
    "You are a friendly money coach for an Indian household.",
    "You get one month of spending totals per category, in paise (100 paise = 1 rupee), and the same for last month.",
    `Write 3 to 5 short, practical tips in ${lang}. Use easy words. Show money in rupees with the ₹ sign, never paise.`,
    "Base every tip only on the numbers given. Do not give investment, tax or legal advice. Do not name any product, brand or app.",
    "estSavingPaise: a realistic saving per month in paise if the tip is followed; 0 if you are not sure.",
    "Title: at most 8 words. Detail: at most 2 short sentences.",
    "",
    JSON.stringify({
      month: input.month.slice(0, 7),
      totalPaise: input.totalPaise,
      previousTotalPaise: input.previousTotalPaise,
      budgetPaise: input.budgetPaise,
      emiSharePercentOfSpending: input.emiSharePercent,
      categories: input.categories,
    }),
  ].join("\n");
}

function cleanTips(raw: unknown, totalPaise: number): Tip[] | null {
  const list = (raw as any)?.tips;
  if (!Array.isArray(list) || list.length < 3 || list.length > 5) return null;
  const tips: Tip[] = [];
  for (const t of list) {
    if (typeof t?.title !== "string" || typeof t?.detail !== "string") return null;
    const title = t.title.trim().slice(0, 80);
    const detail = t.detail.trim().slice(0, 300);
    if (!title || !detail) return null;
    const est = Number.isInteger(t.estSavingPaise) ? Math.min(Math.max(t.estSavingPaise, 0), totalPaise) : 0;
    tips.push({ title, detail, estSavingPaise: est });
  }
  return tips;
}

async function askGemini(input: Input, key: string, model: string): Promise<Tip[] | null> {
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt(input) }] }],
      generationConfig: { responseMimeType: "application/json", responseSchema: GEMINI_SCHEMA, temperature: 0.4 },
    }),
    signal: AbortSignal.timeout(25_000),
  });
  if (!res.ok) return null;
  const body = await res.json();
  const text = body?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof text !== "string") return null;
  try {
    return cleanTips(JSON.parse(text), input.totalPaise);
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const auth = req.headers.get("Authorization");
  if (!auth) return json({ error: "not_signed_in" }, 401);

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "bad_body" }, 400);
  }
  const input = validate(raw);
  if (typeof input === "string") return json({ error: input }, 400);

  // The user's own token: row level security decides whether they belong to this family.
  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: auth } },
  });

  const existing = await db.from("ai_reports").select("tips, language").eq("family_id", input.familyId).eq("month", input.month).maybeSingle();
  if (existing.error) return json({ error: "not_allowed" }, 403);
  if (existing.data) return json({ tips: existing.data.tips, cached: true });

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return json({ error: "ai_not_configured" }, 503);

  const tips = await askGemini(input, key, Deno.env.get("AI_MODEL") ?? "gemini-2.5-flash");
  if (!tips) return json({ error: "ai_failed" }, 502);

  const saved = await db.from("ai_reports").insert({ family_id: input.familyId, month: input.month, language: input.language, tips });
  if (saved.error) {
    // Someone in the family made it a moment ago: return theirs.
    const again = await db.from("ai_reports").select("tips").eq("family_id", input.familyId).eq("month", input.month).maybeSingle();
    if (again.data) return json({ tips: again.data.tips, cached: true });
    return json({ error: "not_allowed" }, 403);
  }
  return json({ tips, cached: false });
});
