package com.agniv.hisaab.core.ml.voice

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.PayMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** 50+ phrases the way people actually say them: English, Hinglish, Devanagari, digits and number words. */
class VoiceParserTest {
    private val today = LocalDate.of(2026, 11, 10)

    private data class Case(
        val phrase: String,
        val paise: Long?,
        val daysAgo: Int = 0,
        val category: String? = null,
    )

    private fun rupees(r: Long) = r * 100

    private val cases =
        listOf(
            // digits + Hinglish
            Case("aaj 200 ka doodh", rupees(200), 0, "Milk"),
            Case("200 ka doodh", rupees(200), 0, "Milk"),
            Case("aaj 50 rupaye ki sabzi", rupees(50), 0, "Vegetables"),
            Case("kal 500 petrol", rupees(500), 1, "Fuel"),
            Case("kal 500 ka petrol dalwaya", rupees(500), 1, "Fuel"),
            Case("parso 1200 bijli bill", rupees(1200), 2, "Electricity"),
            Case("rent 12000", rupees(12000), 0, "Rent"),
            Case("12,000 rent diya", rupees(12000), 0, "Rent"),
            Case("₹350 swiggy", rupees(350), 0, "Food Outside"),
            Case("rs 80 chai", rupees(80), 0, "Food Outside"),
            Case("rs. 80 chai", rupees(80), 0, "Food Outside"),
            Case("150 rupees auto", rupees(150), 0, "Transport"),
            Case("auto ka kiraya 60", rupees(60), 0, "Transport"),
            Case("recharge 299", rupees(299), 0, "Mobile/Internet"),
            Case("gas cylinder 1100", rupees(1100), 0, "Gas/LPG"),
            Case("maid ko 3000 diya", rupees(3000), 0, "Maid/Help"),
            Case("school fees 8500", rupees(8500), 0, "Education"),
            Case("dawai 430 ki", rupees(430), 0, "Medical"),
            Case("doctor 600", rupees(600), 0, "Medical"),
            Case("emi 8500", rupees(8500), 0, "EMI"),
            // k / lakh / decimals
            Case("2k rent", rupees(2000), 0, "Rent"),
            Case("2.5k groceries", rupees(2500), 0, "Groceries"),
            Case("1.5 lakh school", rupees(150000), 0, "Education"),
            Case("2 hazaar ka kirana", rupees(2000), 0, "Groceries"),
            Case("5 sau ka petrol", rupees(500), 0, "Fuel"),
            Case("49.50 chai", 4950, 0, "Food Outside"),
            Case("120.5 doodh", 12050, 0, "Milk"),
            // Hindi number words (Latin)
            Case("aaj do sau ka doodh", rupees(200), 0, "Milk"),
            Case("kal teen sau pachas rupaye petrol", rupees(350), 1, "Fuel"),
            Case("dedh hazaar rent", rupees(1500), 0, "Rent"),
            Case("dedh sau ki sabzi", rupees(150), 0, "Vegetables"),
            Case("dhai hazaar ka kirana", rupees(2500), 0, "Groceries"),
            Case("sawa sau ka doodh", rupees(125), 0, "Milk"),
            Case("paanch hazaar do sau", rupees(5200)),
            Case("ek sau pachas rupaye", rupees(150)),
            Case("sau rupaye ki chai", rupees(100), 0, "Food Outside"),
            Case("hazaar rupaye bijli", rupees(1000), 0, "Electricity"),
            Case("ek lakh pachas hazaar", rupees(150000)),
            Case("pachchis rupaye chai", rupees(25), 0, "Food Outside"),
            Case("ikkis sau", rupees(2100)),
            // English number words
            Case("two hundred fifty groceries", rupees(250), 0, "Groceries"),
            Case("twenty five rupees milk", rupees(25), 0, "Milk"),
            Case("fifteen hundred rent", rupees(1500), 0, "Rent"),
            Case("one thousand two hundred and fifty petrol", rupees(1250), 0, "Fuel"),
            Case("yesterday 150 groceries", rupees(150), 1, "Groceries"),
            Case("spent 300 on dinner today", rupees(300), 0, "Food Outside"),
            // Devanagari
            Case("आज 200 का दूध", rupees(200), 0, "Milk"),
            Case("आज दो सौ का दूध", rupees(200), 0, "Milk"),
            Case("कल पाँच सौ रुपये पेट्रोल", rupees(500), 1, "Fuel"),
            Case("परसों डेढ़ हज़ार किराया", rupees(1500), 2, "Rent"),
            Case("तीन सौ पचास रुपये की सब्ज़ी", rupees(350), 0, "Vegetables"),
            Case("बिजली का बिल १२००", rupees(1200), 0, "Electricity"),
            Case("दवाई ४५० रुपए", rupees(450), 0, "Medical"),
            Case("ढाई सौ का खाना", rupees(250), 0, "Food Outside"),
            // dates
            Case("3 din pehle 200 ka doodh", rupees(200), 3, "Milk"),
            Case("200 ka doodh 3 din pehle", rupees(200), 3, "Milk"),
            Case("5 days ago 450 petrol", rupees(450), 5, "Fuel"),
            Case("day before yesterday 90 chai", rupees(90), 2, "Food Outside"),
            // quantities are not prices
            Case("2 kg aloo 80", rupees(80), 0, "Vegetables"),
            Case("5 litre doodh 300 rupaye", rupees(300), 0, "Milk"),
            Case("2 kg tamatar", null, 0, "Vegetables"),
            Case("10 baje chai 40", rupees(40), 0, "Food Outside"),
            // no amount, or nonsense
            Case("doodh", null, 0, "Milk"),
            Case("kuch nahi", null),
            Case("", null),
        )

    @Test
    fun `phrases give the right amount, day and category`() {
        val failures =
            cases.mapNotNull { c ->
                val r = VoiceParser.parse(c.phrase, today)
                val ok = r.amount?.paise == c.paise && r.date == today.minusDays(c.daysAgo.toLong()) && r.categoryName == c.category
                val got = "${r.amount?.paise} / ${r.date} / ${r.categoryName}"
                if (ok) null else "'${c.phrase}' -> $got (wanted ${c.paise} / -${c.daysAgo}d / ${c.category})"
            }
        assertThat(failures).isEmpty()
        assertThat(cases.size).isAtLeast(50)
    }

    @Test
    fun `payment mode words are picked up and not left in the note`() {
        val upi = VoiceParser.parse("150 groceries upi", today)
        assertThat(upi.mode).isEqualTo(PayMode.UPI)
        assertThat(upi.note).isNull()
        assertThat(VoiceParser.parse("cash 200 doodh", today).mode).isEqualTo(PayMode.CASH)
        assertThat(VoiceParser.parse("card se 5000 shopping", today).mode).isEqualTo(PayMode.CARD)
        assertThat(VoiceParser.parse("200 doodh", today).mode).isNull()
    }

    @Test
    fun `note keeps the extra words and drops the matched category word`() {
        assertThat(VoiceParser.parse("aaj 200 ka doodh", today).note).isNull()
        assertThat(VoiceParser.parse("300 swiggy dinner with family", today).note).isEqualTo("Dinner with family")
        assertThat(VoiceParser.parse("2 kg aloo 80", today).note).isEqualTo("2 kg")
    }

    @Test
    fun `confidence says how much to trust it`() {
        assertThat(VoiceParser.parse("aaj 200 ka doodh", today).confidence).isEqualTo(Confidence.HIGH)
        assertThat(VoiceParser.parse("200 rupaye", today).confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(VoiceParser.parse("doodh", today).confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `amount is exact money and absurd values are refused`() {
        assertThat(VoiceParser.parse("chai 120.55", today).amount).isEqualTo(Money.ofRupees(120, 55))
        assertThat(VoiceParser.parse("99999999999 rupaye", today).amount).isNull()
    }

    @Test
    fun `a bare small word is not mistaken for a price`() {
        assertThat(VoiceParser.parse("ek chai", today).amount).isNull()
        assertThat(VoiceParser.parse("do samose 40 rupaye", today).amount).isEqualTo(Money.ofRupees(40))
    }
}
