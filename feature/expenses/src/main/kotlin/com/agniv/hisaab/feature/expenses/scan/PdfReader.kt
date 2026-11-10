package com.agniv.hisaab.feature.expenses.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The PDF is protected by a password (common for bank statements); the user must remove it first. */
internal class PdfLockedException : Exception()

/**
 * Reads a PDF by drawing each page as a picture and reading that with [TextScanner]. This works for scanned
 * and for normal downloaded statements alike, because Android has no way to pull the text layer out of a PDF.
 * One page at a time, so memory stays small on 2 GB phones.
 */
internal object PdfReader {
    private const val MAX_PAGES = 12
    private const val TARGET_WIDTH = 1600

    /** [onPage] is told (page number, total pages) before each page is read. */
    suspend fun read(
        context: Context,
        uri: Uri,
        onPage: (Int, Int) -> Unit,
    ): String {
        val fd = withContext(Dispatchers.IO) { context.contentResolver.openFileDescriptor(uri, "r") } ?: return ""
        val renderer =
            try {
                PdfRenderer(fd)
            } catch (e: SecurityException) {
                fd.close()
                throw PdfLockedException().also { it.initCause(e) }
            }
        return try {
            val pages = minOf(renderer.pageCount, MAX_PAGES)
            buildString {
                for (i in 0 until pages) {
                    onPage(i + 1, pages)
                    val bitmap = withContext(Dispatchers.IO) { render(renderer, i) }
                    try {
                        append(TextScanner.read(bitmap)).append('\n')
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            renderer.close()
            closeQuietly(fd)
        }
    }

    private fun render(
        renderer: PdfRenderer,
        index: Int,
    ): Bitmap {
        renderer.openPage(index).use { page ->
            val scale = TARGET_WIDTH.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(TARGET_WIDTH, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            // PDF pages render on a transparent background; text recognition needs white.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun closeQuietly(fd: ParcelFileDescriptor) {
        runCatching { fd.close() }
    }
}
