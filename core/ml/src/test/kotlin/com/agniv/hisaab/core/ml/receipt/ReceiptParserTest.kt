package com.agniv.hisaab.core.ml.receipt

import com.agniv.hisaab.core.ml.voice.Confidence
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** Text the way ML Kit returns it from phone photos of bills: one line per row, noisy, mixed. */
class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun scan(text: String) = ReceiptParser.parse(text.trimIndent(), today)

    @Test
    fun `supermarket bill uses the grand total, not the sub total or gst`() {
        val r =
            scan(
                """
                DMART
                Avenue Supermarts Ltd
                GSTIN 27AABCA1234B1Z5
                Date: 08/11/2026  Time 18:42
                Milk 2 x 28.00        56.00
                Atta 5kg              245.00
                SUB TOTAL             301.00
                GST                    15.05
                GRAND TOTAL           316.05
                """,
            )
        assertThat(r.amount?.paise).isEqualTo(31605)
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 11, 8))
        assertThat(r.merchant).isEqualTo("DMART")
        assertThat(r.categoryName).isEqualTo("Groceries")
        assertThat(r.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `total on the next line`() {
        val r = scan("Sharma Sweets\n12-11-25\nTOTAL\n₹ 1,250.00\nThank you")
        assertThat(r.amount?.paise).isEqualTo(125000)
        assertThat(r.date).isEqualTo(LocalDate.of(2025, 11, 12))
        assertThat(r.categoryName).isEqualTo("Food Outside")
    }

    @Test
    fun `rupee marked amounts when there is no total word`() {
        val r = scan("Raju Medical Store\nDate 3 Nov 2026\nParacetamol Rs. 32\nCough syrup Rs 118\nRs. 150/-")
        assertThat(r.amount?.paise).isEqualTo(15000)
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 11, 3))
        assertThat(r.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `petrol pump bill`() {
        val r = scan("INDIAN OIL\nSANJAY FILLING STATION\n2026-11-09\nPetrol 5.12 L\nRate 104.21\nAmount 533.60\nTotal Amount 533.60")
        assertThat(r.amount?.paise).isEqualTo(53360)
        assertThat(r.date).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(r.categoryName).isEqualTo("Fuel")
    }

    @Test
    fun `restaurant bill with service charge, net payable wins`() {
        val r =
            scan(
                """
                Spice Garden Restaurant
                Bill No 4821
                Paneer Tikka 280
                Dal Makhani 240
                Total 520
                Service Charge 26
                CGST 13.00
                SGST 13.00
                Net Payable 572.00
                """,
            )
        assertThat(r.amount?.paise).isEqualTo(57200)
        assertThat(r.merchant).isEqualTo("Spice Garden Restaurant")
        assertThat(r.categoryName).isEqualTo("Food Outside")
    }

    @Test
    fun `fallback to the biggest two decimal number`() {
        val r = scan("Gupta Stores\n12/10/2026\nSoap 45.00\nOil 189.50\nPhone 9876543210")
        assertThat(r.amount?.paise).isEqualTo(18950)
        assertThat(r.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `future and impossible dates are ignored and day comes first`() {
        assertThat(scan("Shop\nTotal 100\n31/02/2026 then 05/12/2026 then 04-11-2026").date).isEqualTo(LocalDate.of(2026, 11, 4))
        assertThat(scan("Shop\nTotal 100\n01/02/2026").date).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(scan("Shop\nTotal 100").date).isNull()
    }

    @Test
    fun `merchant skips invoice and gst lines`() {
        assertThat(scan("TAX INVOICE\nGSTIN 29ABCDE1234F1Z5\nNew Kirana Bazaar\nTotal 99").merchant).isEqualTo("New Kirana Bazaar")
    }

    @Test
    fun `no readable amount gives low confidence`() {
        val r = scan("blurry\nnothing here")
        assertThat(r.amount).isNull()
        assertThat(r.confidence).isEqualTo(Confidence.LOW)
        assertThat(scan("").amount).isNull()
    }

    @Test
    fun `huge or zero totals are refused`() {
        assertThat(scan("Shop\nTotal 0.00").amount).isNull()
        assertThat(scan("Shop\nTotal 99999999999.00").amount).isNull()
    }
}
