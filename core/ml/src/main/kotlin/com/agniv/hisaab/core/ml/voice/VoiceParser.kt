package com.agniv.hisaab.core.ml.voice

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.PayMode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class Confidence { HIGH, MEDIUM, LOW }

/**
 * What was understood from a spoken or typed sentence. Nothing here is saved: the screen shows it
 * and the user confirms (CLAUDE.md rule 4). [categoryName] is the English name of a default category.
 */
data class VoiceExpense(
    val amount: Money?,
    val date: LocalDate,
    val categoryName: String?,
    val mode: PayMode?,
    val note: String?,
    val confidence: Confidence,
)

/**
 * Rule-based reader for phrases like "aaj 200 ka doodh", "kal do sau rupaye petrol", "dedh hazaar rent",
 * "yesterday 150 groceries upi" (PRD F13). Works on the phone, no network.
 * Everything is decimal arithmetic: spoken numbers never go through Double.
 * One small state machine for numbers plus keyword tables, so the detekt size rules are switched off here.
 */
@Suppress("TooManyFunctions", "NestedBlockDepth", "ComplexCondition", "ReturnCount")
object VoiceParser {
    private const val MAX_PAISE = 99_999_999_900L
    private const val MAX_DAYS_AGO = 365
    private const val WORD_ONLY_MIN = 10
    private const val SCALE = 2
    private const val PRIORITY_DIGITS_AND_CURRENCY = 3
    private const val PRIORITY_CURRENCY = 2
    private const val PRIORITY_DIGITS = 1

    private val digitToken = Regex("""^\d[\d,]*(\.\d+)?k?$""")
    private val splitter = Regex("""[^\p{L}\p{M}\p{N}.,]+""")

    fun parse(
        text: String,
        today: LocalDate,
    ): VoiceExpense {
        val tokens = tokenize(text)
        val norm = tokens.map { it.norm }
        val runs = findRuns(norm)
        val amountRun = pickAmount(runs, norm)
        val daysAgo = findDaysAgo(norm, runs)
        val used = mutableSetOf<Int>()
        amountRun?.let { used += it.start until it.end }
        daysAgo.usedIndices.forEach { used += it }
        val mode = findMode(norm, used)
        val category = findCategory(norm)
        category?.index?.let { used += it }
        norm.forEachIndexed { i, w -> if (w in CURRENCY || w in FILLERS) used += i }
        val note =
            tokens
                .filterIndexed { i, _ -> i !in used }
                .joinToString(" ") { it.raw }
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.take(MAX_NOTE)
                ?.replaceFirstChar { it.uppercase() }
        val amount = amountRun?.value?.toMoney()
        val confidence =
            when {
                amount == null -> Confidence.LOW
                category == null -> Confidence.MEDIUM
                else -> Confidence.HIGH
            }
        return VoiceExpense(amount, today.minusDays(daysAgo.days.toLong()), category?.name, mode, note, confidence)
    }

    private const val MAX_NOTE = 200

    private class Token(
        val raw: String,
        val norm: String,
    )

    private fun tokenize(text: String): List<Token> =
        text
            .replace("₹", " ₹ ")
            .split(splitter)
            .map { it.trim('.', ',') }
            .filter { it.isNotEmpty() }
            .map { Token(it, TextNorm.word(it)) }

    // ---- numbers -------------------------------------------------------------------------------------------

    private class Run(
        val start: Int,
        val end: Int,
        val value: BigDecimal,
        val hasDigits: Boolean,
    )

    private fun digitValue(word: String): BigDecimal? {
        if (!digitToken.matches(word)) return null
        val k = word.endsWith("k")
        val number = BigDecimal(word.removeSuffix("k").replace(",", ""))
        return if (k) number.multiply(BigDecimal(THOUSAND)) else number
    }

    private fun findRuns(words: List<String>): List<Run> {
        val runs = mutableListOf<Run>()
        var i = 0
        while (i < words.size) {
            val run = runFrom(words, i)
            if (run == null) {
                i++
            } else {
                runs += run
                i = run.end
            }
        }
        return runs
    }

    private fun startsNumber(word: String): Boolean = digitValue(word) != null || NumberWords.lookup(word) != null

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun runFrom(
        words: List<String>,
        start: Int,
    ): Run? {
        if (!startsNumber(words[start])) return null
        var total = BigDecimal.ZERO
        var cur = BigDecimal.ZERO
        var lastUnit: BigDecimal? = null
        var lastWasDigits = false
        var sawDigits = false
        var j = start
        while (j < words.size) {
            val w = words[j]
            if (w == "and" && j > start && j + 1 < words.size && startsNumber(words[j + 1])) {
                j++
                continue
            }
            val digits = digitValue(w)
            val nw = if (digits == null) NumberWords.lookup(w) else NumWord.Unit(digits)
            if (nw == null && !(w == "k" && lastUnit != null)) break
            when {
                w == "k" && nw == null -> {
                    cur = cur.multiply(BigDecimal(THOUSAND))
                    lastUnit = null
                }
                nw is NumWord.Unit -> {
                    val tensThenOne =
                        lastUnit != null &&
                            !lastWasDigits &&
                            digits == null &&
                            lastUnit.remainder(BigDecimal.TEN).signum() == 0 &&
                            lastUnit >= BigDecimal(TWENTY) &&
                            lastUnit < BigDecimal(HUNDRED) &&
                            nw.value < BigDecimal.TEN
                    if (lastUnit != null && !tensThenOne) break
                    cur = cur.add(nw.value)
                    lastUnit = nw.value
                    lastWasDigits = digits != null
                    sawDigits = sawDigits || digits != null
                }
                nw is NumWord.Mult -> {
                    if (nw.value == HUNDRED_L) {
                        cur = (if (cur.signum() == 0) BigDecimal.ONE else cur).multiply(BigDecimal(nw.value))
                    } else {
                        total = total.add((if (cur.signum() == 0) BigDecimal.ONE else cur).multiply(BigDecimal(nw.value)))
                        cur = BigDecimal.ZERO
                    }
                    lastUnit = null
                }
            }
            j++
        }
        return if (j == start) null else Run(start, j, total.add(cur), sawDigits)
    }

    private fun pickAmount(
        runs: List<Run>,
        words: List<String>,
    ): Run? {
        fun adjacentCurrency(r: Run) = words.getOrNull(r.start - 1) in CURRENCY || words.getOrNull(r.end) in CURRENCY

        fun quantity(r: Run) = words.getOrNull(r.end) in QUANTITY_UNITS

        fun priority(r: Run): Int =
            when {
                r.hasDigits && adjacentCurrency(r) -> PRIORITY_DIGITS_AND_CURRENCY
                adjacentCurrency(r) -> PRIORITY_CURRENCY
                r.hasDigits -> PRIORITY_DIGITS
                // A bare "ek" or "do" is more likely a word than a price.
                r.value >= BigDecimal(WORD_ONLY_MIN) -> 0
                else -> -1
            }
        return runs
            .filter { !quantity(it) && priority(it) >= 0 && it.value.signum() > 0 }
            .maxByOrNull { priority(it) * RUN_ORDER + (runs.size - runs.indexOf(it)) }
    }

    private fun BigDecimal.toMoney(): Money? {
        val paise = setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).toBigInteger()
        return if (paise.signum() > 0 && paise <= java.math.BigInteger.valueOf(MAX_PAISE)) Money(paise.toLong()) else null
    }

    // ---- dates ---------------------------------------------------------------------------------------------

    private class DaysAgo(
        val days: Int,
        val usedIndices: Set<Int>,
    )

    private fun findDaysAgo(
        words: List<String>,
        runs: List<Run>,
    ): DaysAgo {
        // "3 din pehle", "3 days ago"
        for (r in runs) {
            val unit = words.getOrNull(r.end)
            val after = words.getOrNull(r.end + 1)
            val n = r.value.toInt()
            val wholeDays = r.value.stripTrailingZeros().scale() <= 0 && n in 1..MAX_DAYS_AGO
            if (unit in DAY_WORDS && after in AGO_WORDS && wholeDays) {
                return DaysAgo(n, (r.start until r.end).toSet() + (r.end) + (r.end + 1))
            }
        }
        val i = words.indexOfFirst { it in DAY_BEFORE }
        if (i >= 0) return DaysAgo(2, setOf(i))
        val seq = words.indexOfFirst { it == "day" }
        if (seq >= 0 && words.getOrNull(seq + 1) == "before" && words.getOrNull(seq + 2) == "yesterday") {
            return DaysAgo(2, setOf(seq, seq + 1, seq + 2))
        }
        val y = words.indexOfFirst { it in YESTERDAY }
        if (y >= 0) return DaysAgo(1, setOf(y))
        val t = words.indexOfFirst { it in TODAY }
        return DaysAgo(0, if (t >= 0) setOf(t) else emptySet())
    }

    // ---- mode, category ------------------------------------------------------------------------------------

    private fun findMode(
        words: List<String>,
        used: MutableSet<Int>,
    ): PayMode? {
        val i = words.indexOfFirst { it in MODE_WORDS }
        if (i < 0) return null
        used += i
        return MODE_WORDS.getValue(words[i])
    }

    private class CategoryHit(
        val name: String,
        val index: Int,
    )

    private fun findCategory(words: List<String>): CategoryHit? {
        val hits = words.mapIndexedNotNull { i, w -> CATEGORY_WORDS[w]?.let { CategoryHit(it, i) } }.toMutableList()
        // "light bill" is electricity; "light" alone is not.
        val light = words.indexOf("light")
        if (light >= 0 && "bill" in words) hits += CategoryHit("Electricity", light)
        hits.sortBy { it.index }
        // "auto ka kiraya" / "bus kiraya" is a fare, not house rent.
        val transport = hits.firstOrNull { it.name == "Transport" }
        return if (transport != null) transport else hits.firstOrNull()
    }

    private const val THOUSAND = 1000
    private const val TWENTY = 20
    private const val HUNDRED = 100
    private const val HUNDRED_L = 100L
    private const val RUN_ORDER = 1000

    private val CURRENCY =
        setOf("₹", "rs", "inr", "rupees", "rupee", "rupaye", "rupiye", "rupay", "rupaiye", "rupya", "रुपये", "रुपए", "रुपया", "रु", "रू")
            .map(TextNorm::word)
            .toSet()

    private val QUANTITY_UNITS =
        setOf(
            "kg",
            "kilo",
            "kilos",
            "gram",
            "grams",
            "gm",
            "litre",
            "liter",
            "ltr",
            "liters",
            "litres",
            "ml",
            "packet",
            "packets",
            "piece",
            "pieces",
            "pcs",
            "dozen",
            "darjan",
            "peti",
            "baje",
            "am",
            "pm",
            "log",
            "din",
            "dino",
            "days",
            "day",
            "hafte",
            "week",
            "weeks",
            "mahine",
            "month",
            "months",
            "saal",
            "year",
            "years",
            "किलो",
            "ग्राम",
            "लीटर",
            "पैकेट",
            "दर्जन",
            "बजे",
            "दिन",
        ).map(TextNorm::word).toSet()

    private val DAY_WORDS = setOf("din", "dino", "days", "day", "दिन").map(TextNorm::word).toSet()
    private val AGO_WORDS = setOf("pehle", "pahle", "ago", "purv", "पहले").map(TextNorm::word).toSet()
    private val TODAY = setOf("aaj", "aj", "today", "आज").map(TextNorm::word).toSet()

    // Expenses are in the past, so "kal" means yesterday here.
    private val YESTERDAY = setOf("kal", "yesterday", "कल").map(TextNorm::word).toSet()
    private val DAY_BEFORE = setOf("parso", "parson", "परसों", "परसो").map(TextNorm::word).toSet()

    private val FILLERS =
        setOf(
            "ka",
            "ki",
            "ke",
            "ko",
            "ne",
            "se",
            "mein",
            "me",
            "pe",
            "par",
            "liye",
            "liya",
            "diya",
            "diye",
            "di",
            "dia",
            "kiya",
            "kar",
            "kari",
            "kiye",
            "ho",
            "gaya",
            "gayi",
            "hua",
            "hui",
            "hue",
            "aur",
            "the",
            "a",
            "an",
            "of",
            "for",
            "on",
            "at",
            "in",
            "to",
            "spent",
            "paid",
            "pay",
            "bought",
            "buy",
            "kharcha",
            "kharch",
            "expense",
            "add",
            "entry",
            "only",
            "bas",
            "mera",
            "meri",
            "humne",
            "maine",
            "hamne",
            "kharida",
            "khareeda",
            "was",
            "is",
            "and",
            "my",
            "i",
            "ek_",
            "ka_",
            "का",
            "की",
            "के",
            "को",
            "ने",
            "से",
            "में",
            "पर",
            "लिए",
            "दिया",
            "दिए",
            "किया",
            "गया",
            "खर्च",
            "और",
            "मैंने",
            "हमने",
            "है",
            "था",
            "थे",
        ).map(TextNorm::word).toSet()

    private val MODE_WORDS =
        buildMap {
            listOf("upi", "gpay", "googlepay", "phonepe", "paytm", "bhim").forEach { put(it, PayMode.UPI) }
            listOf("cash", "nakad", "नकद", "कैश").forEach { put(TextNorm.word(it), PayMode.CASH) }
            listOf("card", "कार्ड").forEach { put(TextNorm.word(it), PayMode.CARD) }
        }

    private val CATEGORY_WORDS: Map<String, String> =
        buildMap {
            fun add(
                name: String,
                vararg words: String,
            ) = words.forEach { put(TextNorm.word(it), name) }
            add("Milk", "doodh", "milk", "dairy", "dahi", "curd", "दूध", "दही")
            add(
                "Vegetables",
                "sabzi",
                "sabji",
                "subzi",
                "vegetable",
                "vegetables",
                "veggies",
                "aloo",
                "pyaz",
                "tamatar",
                "phal",
                "fruits",
                "fruit",
                "सब्जी",
                "सब्ज़ी",
                "फल",
            )
            add(
                "Groceries",
                "kirana",
                "grocery",
                "groceries",
                "ration",
                "atta",
                "dal",
                "chawal",
                "rice",
                "oil",
                "masala",
                "sugar",
                "cheeni",
                "namak",
                "किराना",
                "राशन",
                "आटा",
                "दाल",
                "चावल",
            )
            add("Rent", "rent", "kiraya", "kirayaa", "किराया")
            add("Electricity", "bijli", "electricity", "बिजली")
            add("Water", "paani", "pani", "water", "पानी")
            add("Gas/LPG", "gas", "cylinder", "lpg", "silender", "गैस", "सिलेंडर")
            add("Mobile/Internet", "mobile", "recharge", "internet", "wifi", "broadband", "data", "मोबाइल", "रिचार्ज", "इंटरनेट")
            add("Maid/Help", "maid", "bai", "kaamwali", "kamwali", "cook", "naukrani", "बाई", "कामवाली", "नौकरानी")
            add("Fuel", "petrol", "diesel", "fuel", "cng", "पेट्रोल", "डीजल", "डीज़ल")
            add(
                "Transport",
                "auto",
                "rickshaw",
                "bus",
                "metro",
                "cab",
                "ola",
                "uber",
                "taxi",
                "train",
                "ticket",
                "ऑटो",
                "रिक्शा",
                "बस",
                "मेट्रो",
                "टैक्सी",
                "ट्रेन",
                "टिकट",
            )
            add(
                "Food Outside",
                "restaurant",
                "hotel",
                "swiggy",
                "zomato",
                "pizza",
                "burger",
                "chai",
                "coffee",
                "cafe",
                "dinner",
                "lunch",
                "breakfast",
                "biryani",
                "samosa",
                "momo",
                "momos",
                "snacks",
                "nashta",
                "icecream",
                "चाय",
                "कॉफी",
                "नाश्ता",
                "खाना",
                "रेस्टोरेंट",
                "पिज़्ज़ा",
                "समोसा",
            )
            add(
                "Shopping",
                "shopping",
                "kapde",
                "clothes",
                "shoes",
                "joote",
                "amazon",
                "flipkart",
                "myntra",
                "dress",
                "saree",
                "शॉपिंग",
                "कपड़े",
                "जूते",
                "साड़ी",
            )
            add(
                "Medical",
                "dawai",
                "dawa",
                "medicine",
                "medicines",
                "doctor",
                "hospital",
                "dentist",
                "medical",
                "chemist",
                "tablets",
                "injection",
                "दवाई",
                "दवा",
                "डॉक्टर",
                "अस्पताल",
            )
            add(
                "Education",
                "school",
                "fees",
                "fee",
                "tuition",
                "college",
                "books",
                "kitab",
                "stationery",
                "coaching",
                "स्कूल",
                "फीस",
                "किताब",
                "ट्यूशन",
                "कॉलेज",
            )
            add(
                "Festivals/Gifts",
                "gift",
                "tohfa",
                "diwali",
                "holi",
                "rakhi",
                "shaadi",
                "wedding",
                "birthday",
                "puja",
                "mithai",
                "pataka",
                "तोहफा",
                "दिवाली",
                "होली",
                "राखी",
                "शादी",
                "मिठाई",
                "पूजा",
            )
            add("EMI", "emi", "kist", "installment", "ईएमआई", "किस्त")
            add(
                "Entertainment",
                "movie",
                "film",
                "cinema",
                "netflix",
                "hotstar",
                "game",
                "party",
                "trip",
                "picnic",
                "concert",
                "spotify",
                "फिल्म",
                "सिनेमा",
                "पार्टी",
            )
        }
}
