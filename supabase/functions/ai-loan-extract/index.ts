// Kinly: "Fill this loan with AI" (IMPLEMENTATION_PLAN 4.8).
//
// The app reads a loan letter or a typed description ON THE PHONE, hides account / phone / ID numbers there,
// and sends only that masked text. This function refuses text that still looks personal, asks Gemini to pull out
// the loan fields as strict JSON, checks every field, and returns them. Nothing is saved: the app shows the
// result for the user to check and confirm.
//
// Secrets: GEMINI_API_KEY (required), AI_MODEL (optional, default gemini-2.5-flash).
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const MAX_TEXT = 4000;
const DAILY_LIMIT = 20;
const TYPES = ["HOME", "CAR", "BIKE", "PERSONAL", "EDUCATION", "GOLD", "CONSUMER", "CREDIT_CARD", "OTHER"];

// Second line of defence: the app already masked these, so seeing one means something is wrong. Refuse.
const PERSONAL = [
  /\b\d{9,}\b/, // account, card, reference numbers
  /[\w.+-]+@[\w-]+(\.[\w-]+)*/, // email, UPI id
  /\b[A-Z]{5}\d{4}[A-Z]\b/, // PAN
  /\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b/, // Aadhaar
  /(?<![\d.])(\+?91[\s-]?|0)?[6-9]\d{4}[\s-]?\d{5}(?!\d)/, // mobile
];

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const SCHEMA = {
  type: "OBJECT",
  properties: {
    loanType: { type: "STRING", enum: TYPES, nullable: true },
    lender: { type: "STRING", nullable: true },
    principalRupees: { type: "INTEGER", nullable: true },
    annualRatePercent: { type: "NUMBER", nullable: true },
    tenureMonths: { type: "INTEGER", nullable: true },
    emiRupees: { type: "INTEGER", nullable: true },
    firstEmiDate: { type: "STRING", nullable: true },
  },
};

function prompt(text: string): string {
  return [
    "Read this text about an Indian loan (a sanction letter, EMI schedule, SMS or the user's own words).",
    "Account, phone and ID numbers were hidden on purpose and appear as [hidden]; ignore them.",
    "Return only what is clearly stated. If a value is not stated, return null. Never guess or calculate.",
    "loanType: HOME, CAR, BIKE, PERSONAL, EDUCATION, GOLD, CONSUMER (phone, TV, appliance), CREDIT_CARD or OTHER.",
    "lender: the bank or company name, short. principalRupees: the loan amount in whole rupees.",
    "annualRatePercent: yearly interest rate as a number like 8.5. tenureMonths: total months (convert years to months).",
    "emiRupees: monthly instalment in whole rupees. firstEmiDate: first instalment date as YYYY-MM-DD.",
    "",
    "TEXT:",
    text,
  ].join("\n");
}

function clean(raw: any) {
  const out: Record<string, unknown> = {};
  const int = (v: unknown, lo: number, hi: number) => (Number.isInteger(v) && (v as number) >= lo && (v as number) <= hi ? v : null);
  out.loanType = typeof raw?.loanType === "string" && TYPES.includes(raw.loanType) ? raw.loanType : null;
  out.lender = typeof raw?.lender === "string" && raw.lender.trim().length > 0 ? raw.lender.trim().slice(0, 40) : null;
  out.principalRupees = int(raw?.principalRupees, 1, 10_000_000_000);
  out.annualRatePercent =
    typeof raw?.annualRatePercent === "number" && raw.annualRatePercent >= 0 && raw.annualRatePercent <= 42
      ? Math.round(raw.annualRatePercent * 100) / 100
      : null;
  out.tenureMonths = int(raw?.tenureMonths, 1, 480);
  out.emiRupees = int(raw?.emiRupees, 1, 10_000_000_000);
  out.firstEmiDate = typeof raw?.firstEmiDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(raw.firstEmiDate) ? raw.firstEmiDate : null;
  return out;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const auth = req.headers.get("Authorization");
  if (!auth) return json({ error: "not_signed_in" }, 401);

  let body: any;
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_body" }, 400);
  }
  if (!body || typeof body !== "object" || Object.keys(body).some((k) => k !== "text")) return json({ error: "unexpected_field" }, 400);
  const text = body.text;
  if (typeof text !== "string" || text.trim().length < 10 || text.length > MAX_TEXT) return json({ error: "bad_text" }, 400);
  if (PERSONAL.some((re) => re.test(text))) return json({ error: "not_masked" }, 400);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: auth } },
  });
  // A real signed-in user, not just the public project key.
  const user = await db.auth.getUser();
  if (user.error || !user.data.user) return json({ error: "not_signed_in" }, 401);
  const allowed = await db.rpc("bump_ai_usage", { p_kind: "loan_extract", p_max: DAILY_LIMIT });
  if (allowed.error) return json({ error: "failed" }, 500);
  if (allowed.data !== true) return json({ error: "daily_limit" }, 429);

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return json({ error: "ai_not_configured" }, 503);
  const model = Deno.env.get("AI_MODEL") ?? "gemini-2.5-flash";

  let res: Response;
  try {
    res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt(text) }] }],
        generationConfig: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0 },
      }),
      signal: AbortSignal.timeout(25_000),
    });
  } catch {
    return json({ error: "ai_failed" }, 502);
  }
  if (!res.ok) return json({ error: "ai_failed" }, 502);
  try {
    const out = await res.json();
    const parsed = JSON.parse(out?.candidates?.[0]?.content?.parts?.[0]?.text);
    const loan = clean(parsed);
    // Nothing usable at all: say so instead of returning an empty form.
    if (Object.values(loan).every((v) => v === null)) return json({ error: "nothing_found" }, 422);
    return json({ loan });
  } catch {
    return json({ error: "ai_failed" }, 502);
  }
});
