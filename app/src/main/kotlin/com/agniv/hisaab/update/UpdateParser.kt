package com.agniv.hisaab.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A newer Kinly release on GitHub, with the APK to download. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val pageUrl: String,
    val notes: String,
)

/**
 * Reads the GitHub "list releases" answer and picks the newest real release that ships an APK.
 * Pre-releases count (all test builds are pre-releases); drafts never do.
 * Only APKs from this repository's own release downloads are accepted, so a changed answer cannot
 * point the phone at some other file.
 */
object UpdateParser {
    const val APK_PREFIX = "https://github.com/AGNIV33/Money-Management-APP/releases/download/"
    private const val NOTES_MAX = 1_500
    private val json = Json { ignoreUnknownKeys = true }

    fun newerRelease(
        body: String,
        currentVersion: String,
    ): UpdateInfo? {
        val releases = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        return releases
            .mapNotNull { (it as? JsonObject)?.let(::toInfo) }
            .filter { isNewer(it.versionName, currentVersion) }
            .maxWithOrNull { a, b -> compareVersions(a.versionName, b.versionName) }
    }

    private fun toInfo(release: JsonObject): UpdateInfo? {
        val draft = release["draft"]?.jsonPrimitive?.booleanOrNull == true
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull
        val apk =
            (release["assets"] as? JsonArray)
                ?.mapNotNull { it.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull }
                ?.firstOrNull { it.startsWith(APK_PREFIX) && it.endsWith(".apk") }
        if (draft || tag == null || apk == null) return null
        return UpdateInfo(
            versionName = tag.removePrefix("v"),
            apkUrl = apk,
            pageUrl = release["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            notes =
                release["body"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    .orEmpty()
                    .take(NOTES_MAX),
        )
    }

    /** True when [candidate] (like "0.0.3") is a higher version than [current] (like "0.0.2"). */
    fun isNewer(
        candidate: String,
        current: String,
    ): Boolean = compareVersions(candidate, current) > 0

    private fun compareVersions(
        a: String,
        b: String,
    ): Int {
        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    // "1.2.3-beta" -> [1, 2, 3]; anything unreadable counts as 0.
    private fun parts(version: String): List<Int> =
        version
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
