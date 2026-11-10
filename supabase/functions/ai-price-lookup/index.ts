// Kinly voice assistant tool: "what does this product cost in India right now?" (IMPLEMENTATION_PLAN 4.12).
//
// Only a short product name arrives here. No money data, no names. Gemini searches the web (grounded search) and
// the answer is cut down to a few plain sentences before it goes back to the assistant.
//
// Secrets: GEMINI_API_KEY (required), AI_MODEL (optional, default gemini-2.5-flash).
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const DAILY_LIMIT = 30;
const MAX_PRODUCT = 80;
const MAX_ANSWER = 600;

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

// A product name has letters, a few digits (iPhone 17, 256GB) and light punctuation. Anything that looks like
// an amount, an account number or a contact is refused, so money details cannot ride along in the search.
function looksSafe(product: string): boolean {
  if (!/^[\p{L}\p{N}\s.\-+&'/()]+$/u.test(product)) return false;
  if (/\d{5,}/.test(product.replace(/[\s,]/g, ""))) return false;
  return !/(₹|\brs\b|\brupees?\b|\blakh\b|\bcrore\b)/i.test(product);
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
  if (!body || typeof body !== "object" || Object.keys(body).some((k) => k !== "product")) return json({ error: "unexpected_field" }, 400);
  const product = typeof body.product === "string" ? body.product.trim() : "";
  if (product.length < 2 || product.length > MAX_PRODUCT || !looksSafe(product)) return json({ error: "bad_product" }, 400);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: auth } },
  });
  const user = await db.auth.getUser();
  if (user.error || !user.data.user) return json({ error: "not_signed_in" }, 401);
  const allowed = await db.rpc("bump_ai_usage", { p_kind: "price_lookup", p_max: DAILY_LIMIT });
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
        contents: [
          {
            role: "user",
            parts: [
              {
                text:
                  `Search the web. What is the current price in India, in rupees, of: ${product}? ` +
                  "Give the usual price range and the latest launch or model if relevant. Say if the product is not released yet. " +
                  "Plain text, at most 3 short sentences, no links, no markdown.",
              },
            ],
          },
        ],
        tools: [{ google_search: {} }],
        generationConfig: { temperature: 0 },
      }),
      signal: AbortSignal.timeout(25_000),
    });
  } catch {
    return json({ error: "ai_failed" }, 502);
  }
  if (!res.ok) return json({ error: "ai_failed" }, 502);
  try {
    const out = await res.json();
    const text = (out?.candidates?.[0]?.content?.parts ?? [])
      .map((p: any) => (typeof p?.text === "string" ? p.text : ""))
      .join(" ")
      .replace(/[*#_`>\[\]]/g, "")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, MAX_ANSWER);
    if (text.length === 0) return json({ error: "nothing_found" }, 422);
    return json({ answer: text });
  } catch {
    return json({ error: "ai_failed" }, 502);
  }
});
