package com.agniv.hisaab.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateParserTest {
    private fun release(
        tag: String,
        draft: Boolean = false,
        apk: String? = "${UpdateParser.APK_PREFIX}$tag/Kinly-$tag.apk",
    ): String {
        val assets = if (apk == null) "[]" else """[{"name":"x.apk","browser_download_url":"$apk"}]"""
        return """{"tag_name":"$tag","draft":$draft,"prerelease":true,"html_url":"https://github.com/r/$tag",""" +
            """"body":"notes $tag","assets":$assets}"""
    }

    private fun list(vararg releases: String) = releases.joinToString(",", "[", "]")

    @Test
    fun picksNewestHigherRelease() {
        val info = UpdateParser.newerRelease(list(release("v0.0.2"), release("v0.0.4"), release("v0.0.3")), "0.0.2")
        assertThat(info?.versionName).isEqualTo("0.0.4")
        assertThat(info?.apkUrl).endsWith("Kinly-v0.0.4.apk")
        assertThat(info?.notes).isEqualTo("notes v0.0.4")
    }

    @Test
    fun noUpdateWhenCurrentIsLatest() {
        assertThat(UpdateParser.newerRelease(list(release("v0.0.2"), release("v0.0.1")), "0.0.2")).isNull()
    }

    @Test
    fun skipsDraftsAndReleasesWithoutApk() {
        val body = list(release("v0.0.5", draft = true), release("v0.0.4", apk = null), release("v0.0.3"))
        assertThat(UpdateParser.newerRelease(body, "0.0.2")?.versionName).isEqualTo("0.0.3")
    }

    @Test
    fun rejectsApkFromAnotherAddress() {
        val body = list(release("v0.0.9", apk = "https://evil.example.com/Kinly.apk"))
        assertThat(UpdateParser.newerRelease(body, "0.0.2")).isNull()
    }

    @Test
    fun comparesVersionsByNumberNotText() {
        assertThat(UpdateParser.isNewer("0.0.10", "0.0.9")).isTrue()
        assertThat(UpdateParser.isNewer("1.0", "0.9.9")).isTrue()
        assertThat(UpdateParser.isNewer("0.0.2", "0.0.2")).isFalse()
        assertThat(UpdateParser.isNewer("v0.1.0", "0.0.9-beta")).isTrue()
    }

    @Test
    fun badAnswerMeansNoUpdate() {
        assertThat(UpdateParser.newerRelease("not json", "0.0.2")).isNull()
        assertThat(UpdateParser.newerRelease("""{"message":"rate limited"}""", "0.0.2")).isNull()
    }
}
