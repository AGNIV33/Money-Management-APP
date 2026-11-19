package com.agniv.hisaab.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer Kinly, downloads its APK into the app's private cache and hands it to
 * Android's own installer. Android checks that the APK is signed with the same key as the installed app,
 * so only a real Kinly update can install over it.
 */
class AppUpdater(
    private val context: Context,
) {
    suspend fun check(currentVersion: String): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = get(RELEASES_URL).use { it.inputStream.bufferedReader().readText() }
                UpdateParser.newerRelease(body, currentVersion)
            }
        }

    /** Downloads the APK; [onProgress] gets 0..100, or -1 when the size is unknown. */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(info.apkUrl.startsWith(UpdateParser.APK_PREFIX)) { "unexpected download address" }
                val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "kinly-${info.versionName}.apk")
                get(info.apkUrl).use { connection ->
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER)
                            var done = 0L
                            var lastShown = NOT_SHOWN
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                val percent = if (total > 0) (done * PERCENT / total).toInt() else -1
                                if (percent != lastShown) {
                                    lastShown = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                target
            }
        }

    /** Android 8+ asks the user once to allow installs from Kinly. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(intent)
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        start(intent)
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IOException("no app can open this", e)
        }
    }

    // GitHub sends release downloads through an https redirect; HttpURLConnection follows https -> https.
    private fun get(url: String): Closing {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Kinly-Android")
        val code = connection.responseCode
        if (code !in HTTP_OK_RANGE) {
            connection.disconnect()
            throw IOException("server answered $code")
        }
        return Closing(connection)
    }

    private class Closing(
        val connection: HttpURLConnection,
    ) : AutoCloseable {
        val inputStream get() = connection.inputStream
        val contentLengthLong get() = connection.contentLengthLong

        override fun close() = connection.disconnect()
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/AGNIV33/Money-Management-APP/releases?per_page=10"
        const val UPDATE_DIR = "updates"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val BUFFER = 64 * 1024
        const val PERCENT = 100
        const val NOT_SHOWN = -2
        const val TIMEOUT_MS = 20_000
        val HTTP_OK_RANGE = 200..299
    }
}
