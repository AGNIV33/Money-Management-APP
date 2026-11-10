@file:Suppress("MagicNumber") // a table of number words: the numbers are the data

package com.agniv.hisaab.core.ml.voice

import java.math.BigDecimal
import java.text.Normalizer

/** Text clean-up shared by the voice parser: lower case, Devanagari digits and nukta/chandrabindu variants unified. */
internal object TextNorm {
    private const val DEVANAGARI_ZERO = '०'
    private const val NUKTA = '़'
    private const val CHANDRABINDU = 'ँ'
    private const val ANUSVARA = 'ं'

    fun word(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        val cleaned =
            decomposed
                .filter { it != NUKTA }
                .replace(CHANDRABINDU, ANUSVARA)
                .map { if (it in DEVANAGARI_ZERO..'९') '0' + (it - DEVANAGARI_ZERO) else it }
                .joinToString("")
        return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
    }
}

/** What a spoken number word is: a plain value, or a multiplier like sau / hazaar / lakh. */
internal sealed interface NumWord {
    data class Unit(
        val value: BigDecimal,
    ) : NumWord

    data class Mult(
        val value: Long,
    ) : NumWord
}

/**
 * Hindi, Hinglish (Latin spelling) and English number words. Keys are passed through [TextNorm.word]
 * so natural spellings can be written here. dedh / dhai / sawa count as values that a multiplier then
 * scales ("dedh sau" = 1.5 x 100).
 */
internal object NumberWords {
    private fun d(s: String) = BigDecimal(s)

    private val unitWords: Map<String, BigDecimal> =
        buildMap {
            fun put(
                value: Int,
                vararg words: String,
            ) = words.forEach { put(TextNorm.word(it), BigDecimal(value)) }
            // Latin 1-20
            put(1, "ek", "one")
            put(2, "do", "two")
            put(3, "teen", "three")
            put(4, "char", "chaar", "four")
            put(5, "paanch", "panch", "paach", "five")
            put(6, "chhe", "chhah", "chah", "six")
            put(7, "saat", "seven")
            put(8, "aath", "eight")
            put(9, "nau", "nao", "nine")
            put(10, "das", "dus", "ten")
            put(11, "gyarah", "gyaarah", "giyarah", "eleven")
            put(12, "barah", "baarah", "twelve")
            put(13, "terah", "thirteen")
            put(14, "chaudah", "fourteen")
            put(15, "pandrah", "pandra", "fifteen")
            put(16, "solah", "sola", "sixteen")
            put(17, "satrah", "satra", "seventeen")
            put(18, "atharah", "athara", "eighteen")
            put(19, "unnis", "unees", "nineteen")
            put(20, "bees", "twenty")
            // Latin 21-99 (Hindi counting is irregular, so every number has its own word)
            put(21, "ikkis", "ikkees", "ekkis")
            put(22, "baais", "bais", "baaees")
            put(23, "teis", "teyis")
            put(24, "chaubis", "chaubees")
            put(25, "pachchis", "pacchis", "pachis", "pachees")
            put(26, "chhabbis", "chabbis")
            put(27, "sattais", "sattaees")
            put(28, "atthais", "athais", "atthaees")
            put(29, "unatis", "unattis", "unatees")
            put(30, "tees", "thirty")
            put(31, "ikattis", "ikatis")
            put(32, "battis", "batis")
            put(33, "taintis", "tentis")
            put(34, "chauntis", "chautis")
            put(35, "paintis", "paitis", "pentis")
            put(36, "chhattis", "chattis")
            put(37, "saintis", "saitis")
            put(38, "adtis", "artis", "adatis")
            put(39, "unchalis", "untalis")
            put(40, "chalis", "chaalis", "forty")
            put(41, "iktalis", "ektalis")
            put(42, "bayalis", "byalis")
            put(43, "tentalis", "taitalis")
            put(44, "chauvalis", "chavalis")
            put(45, "paintalis", "pentalis", "paitalis")
            put(46, "chhiyalis", "chiyalis")
            put(47, "saintalis", "saitalis")
            put(48, "adtalis", "artalis")
            put(49, "unchas", "unanchas")
            put(50, "pachas", "pachaas", "fifty")
            put(51, "ikyavan", "ikyawan")
            put(52, "bavan", "baavan", "bawan")
            put(53, "tirpan", "tirpana")
            put(54, "chauwan", "chauvan")
            put(55, "pachpan", "pachapan")
            put(56, "chhappan", "chappan")
            put(57, "sattavan", "satavan", "sattawan")
            put(58, "atthavan", "athavan", "atthawan")
            put(59, "unsath", "unasath")
            put(60, "sixty")
            put(61, "iksath", "ekasath")
            put(62, "baasath", "basath")
            put(63, "tirsath", "tirasath")
            put(64, "chausath", "chaunsath")
            put(65, "painsath", "paisath")
            put(66, "chhiyasath", "chiyasath")
            put(67, "adsath", "arsath")
            put(68, "athsath")
            put(69, "unhattar", "unhatar")
            put(70, "sattar", "satar", "seventy")
            put(71, "ikahattar", "ikhattar")
            put(72, "bahattar", "bahatar")
            put(73, "tihattar", "tihatar")
            put(74, "chauhattar", "chauhatar")
            put(75, "pachhattar", "pachattar", "pichhattar")
            put(76, "chhihattar", "chihattar")
            put(77, "satattar", "sathattar")
            put(78, "athattar", "athhattar")
            put(79, "unasi", "unyasi")
            put(80, "assi", "assee", "ashi", "eighty")
            put(81, "ikyasi", "ekyasi")
            put(82, "bayasi", "byasi")
            put(83, "tirasi", "tirasee")
            put(84, "chaurasi", "chorasi")
            put(85, "pachasi", "pichasi")
            put(86, "chhiyasi", "chiyasi")
            put(87, "sattasi", "satasi")
            put(88, "atthasi", "athasi")
            put(89, "nawasi", "navasi")
            put(90, "nabbe", "nabbay", "ninety")
            put(91, "ikyanave", "ekyanve")
            put(92, "banave", "baanave", "baanve")
            put(93, "tiranave", "tiranve")
            put(94, "chauranave", "chauranve")
            put(95, "pachanave", "pachanve", "panchanve")
            put(96, "chhiyanave", "chiyanve")
            put(97, "sattanave", "sataanve")
            put(98, "atthanave", "athanve")
            put(99, "ninyanave", "ninanve")
            // Devanagari 1-99
            listOf(
                "एक",
                "दो",
                "तीन",
                "चार",
                "पाँच",
                "छह",
                "सात",
                "आठ",
                "नौ",
                "दस",
                "ग्यारह",
                "बारह",
                "तेरह",
                "चौदह",
                "पंद्रह",
                "सोलह",
                "सत्रह",
                "अठारह",
                "उन्नीस",
                "बीस",
                "इक्कीस",
                "बाईस",
                "तेईस",
                "चौबीस",
                "पच्चीस",
                "छब्बीस",
                "सत्ताईस",
                "अट्ठाईस",
                "उनतीस",
                "तीस",
                "इकतीस",
                "बत्तीस",
                "तैंतीस",
                "चौंतीस",
                "पैंतीस",
                "छत्तीस",
                "सैंतीस",
                "अड़तीस",
                "उनतालीस",
                "चालीस",
                "इकतालीस",
                "बयालीस",
                "तैंतालीस",
                "चौवालीस",
                "पैंतालीस",
                "छियालीस",
                "सैंतालीस",
                "अड़तालीस",
                "उनचास",
                "पचास",
                "इक्यावन",
                "बावन",
                "तिरपन",
                "चौवन",
                "पचपन",
                "छप्पन",
                "सत्तावन",
                "अट्ठावन",
                "उनसठ",
                "साठ",
                "इकसठ",
                "बासठ",
                "तिरसठ",
                "चौंसठ",
                "पैंसठ",
                "छियासठ",
                "सड़सठ",
                "अड़सठ",
                "उनहत्तर",
                "सत्तर",
                "इकहत्तर",
                "बहत्तर",
                "तिहत्तर",
                "चौहत्तर",
                "पचहत्तर",
                "छिहत्तर",
                "सतहत्तर",
                "अठहत्तर",
                "उन्यासी",
                "अस्सी",
                "इक्यासी",
                "बयासी",
                "तिरासी",
                "चौरासी",
                "पचासी",
                "छियासी",
                "सत्तासी",
                "अट्ठासी",
                "नवासी",
                "नब्बे",
                "इक्यानवे",
                "बानवे",
                "तिरानवे",
                "चौरानवे",
                "पंचानवे",
                "छियानवे",
                "सत्तानवे",
                "अट्ठानवे",
                "निन्यानवे",
            ).forEachIndexed { i, w -> put(TextNorm.word(w), BigDecimal(i + 1)) }
            // Common alternative Devanagari spellings
            listOf(
                "पांच" to 5,
                "छः" to 6,
                "छे" to 6,
                "पन्द्रह" to 15,
                "उन्निस" to 19,
                "अठाईस" to 28,
                "अठावन" to 58,
                "तिरेपन" to 53,
                "चवालीस" to 44,
            ).forEach { (w, v) -> put(TextNorm.word(w), BigDecimal(v)) }
            // 1.5, 2.5, 1.25
            put(TextNorm.word("dedh"), d("1.5"))
            put(TextNorm.word("डेढ़"), d("1.5"))
            put(TextNorm.word("dhai"), d("2.5"))
            put(TextNorm.word("dhaai"), d("2.5"))
            put(TextNorm.word("ढाई"), d("2.5"))
            put(TextNorm.word("sawa"), d("1.25"))
            put(TextNorm.word("sava"), d("1.25"))
            put(TextNorm.word("सवा"), d("1.25"))
        }

    private val multWords: Map<String, Long> =
        buildMap {
            listOf("sau", "hundred", "सौ").forEach { put(TextNorm.word(it), 100L) }
            listOf("hazaar", "hazar", "hajar", "hajaar", "thousand", "हज़ार", "हजार").forEach { put(TextNorm.word(it), 1_000L) }
            listOf("lakh", "lac", "lakhs", "lacs", "लाख").forEach { put(TextNorm.word(it), 100_000L) }
            listOf("crore", "karod", "करोड़", "करोड").forEach { put(TextNorm.word(it), 10_000_000L) }
        }

    /** Null when [word] is not a number word. [word] must already be normalised. */
    fun lookup(word: String): NumWord? =
        multWords[word]?.let { NumWord.Mult(it) } ?: unitWords[word]?.let { NumWord.Unit(it) }
}
