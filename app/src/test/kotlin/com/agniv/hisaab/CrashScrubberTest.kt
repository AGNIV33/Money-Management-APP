package com.agniv.hisaab

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrashScrubberTest {
    @Test
    fun keepsPlainTechnicalText() {
        val text = "Attempt to invoke virtual method on a null object reference at line 42"
        assertThat(CrashScrubber.scrub(text)).isEqualTo(text)
    }

    @Test
    fun removesEmailsAndPhoneNumbers() {
        assertThat(CrashScrubber.scrub("user agniv.test+1@example.co.in failed"))
            .isEqualTo("user [email] failed")
        assertThat(CrashScrubber.scrub("call 98765 43210 or +91-98765-43210"))
            .isEqualTo("call [number] or +[number]")
    }

    @Test
    fun removesAmountsAccountNumbersAndIds() {
        assertThat(CrashScrubber.scrub("amount ₹1,25,000.50 too big")).isEqualTo("amount [amount] too big")
        assertThat(CrashScrubber.scrub("paise=12500050")).isEqualTo("paise=[number]")
        assertThat(CrashScrubber.scrub("a/c 1234 5678 9012")).isEqualTo("a/c [number]")
        assertThat(CrashScrubber.scrub("row 3f2b8c1e-7d4a-4b6e-9c2f-1a2b3c4d5e6f missing"))
            .isEqualTo("row [id] missing")
    }

    @Test
    fun removesQuotedUserTextAndUrls() {
        assertThat(CrashScrubber.scrub("""UNIQUE failed for note "Dinner with Riya" """))
            .isEqualTo("UNIQUE failed for note [text] ")
        assertThat(CrashScrubber.scrub("GET https://x.supabase.co/rest/v1/expenses?family_id=eq.5 timed out"))
            .isEqualTo("GET [url] timed out")
    }

    @Test
    fun shortNumbersStay() {
        assertThat(CrashScrubber.scrub("Index 12 out of bounds for length 3")).isEqualTo("Index 12 out of bounds for length 3")
    }

    @Test
    fun nullStaysNullAndLongTextIsCut() {
        assertThat(CrashScrubber.scrub(null)).isNull()
        assertThat(CrashScrubber.scrub("a".repeat(2_000))).hasLength(500)
    }
}
