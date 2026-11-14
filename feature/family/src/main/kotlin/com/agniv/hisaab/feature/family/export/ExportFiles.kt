package com.agniv.hisaab.feature.family.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.domain.export.ExpenseExport
import com.agniv.hisaab.core.domain.export.ExportLabels
import com.agniv.hisaab.core.domain.export.ExportRow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** What goes at the top of the PDF, already in the user's language. */
data class PdfTitle(
    val title: String,
    val range: String,
    val totalLine: String,
    val footer: String,
)

/**
 * Writes export files into the app's private cache (shared only through the FileProvider "exports" path).
 * The PDF uses Android's own PdfDocument: no PDF library, so nothing is added to the app size.
 */
class ExportFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun freshFile(name: String): File {
            val dir = File(context.cacheDir, "exports")
            // Old exports are personal data: keep only the newest file on the phone.
            dir.listFiles()?.forEach { it.delete() }
            dir.mkdirs()
            return File(dir, name)
        }

        fun csv(
            rows: List<ExportRow>,
            labels: ExportLabels,
            from: LocalDate,
            to: LocalDate,
        ): File = freshFile("kinly-expenses-$from-to-$to.csv").apply { writeText(ExpenseExport.csv(rows, labels)) }

        fun pdf(
            rows: List<ExportRow>,
            labels: ExportLabels,
            title: PdfTitle,
            from: LocalDate,
            to: LocalDate,
        ): File {
            val file = freshFile("kinly-expenses-$from-to-$to.pdf")
            val doc = PdfDocument()
            try {
                PdfPages(doc, labels, title).draw(rows)
                file.outputStream().use { doc.writeTo(it) }
            } finally {
                doc.close()
            }
            return file
        }
    }

/** A4 pages with a simple table; a new page starts whenever the current one is full. */
@Suppress("MagicNumber") // page geometry in PostScript points
private class PdfPages(
    private val doc: PdfDocument,
    private val labels: ExportLabels,
    private val title: PdfTitle,
) {
    private val width = 595
    private val height = 842
    private val margin = 36f
    private val lineHeight = 15f
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
    private val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
    private val big = Paint(bold).apply { textSize = 16f }
    private val small = Paint(text).apply { textSize = 8f }

    // Column x positions: date, category, paid by, note, amount (right edge).
    private val colDate = margin
    private val colCategory = margin + 62f
    private val colPaidBy = margin + 170f
    private val colNote = margin + 260f
    private val colAmountRight = width - margin

    private var pageNo = 0
    private var page: PdfDocument.Page? = null
    private var y = 0f

    fun draw(rows: List<ExportRow>) {
        newPage(first = true)
        rows.forEach { row ->
            if (y > height - margin - lineHeight * 2) newPage(first = false)
            val canvas = checkNotNull(page).canvas
            canvas.drawText(row.date.toString(), colDate, y, text)
            canvas.drawText(fit(row.category, colPaidBy - colCategory - 6f, text), colCategory, y, text)
            canvas.drawText(fit(row.paidBy, colNote - colPaidBy - 6f, text), colPaidBy, y, text)
            val note = (if (row.onlyMe) "(${labels.onlyMe}) " else "") + row.note
            canvas.drawText(fit(note, colAmountRight - 80f - colNote, text), colNote, y, text)
            val amount = row.amount.formatted()
            canvas.drawText(amount, colAmountRight - text.measureText(amount), y, text)
            y += lineHeight
        }
        finishPage()
    }

    private fun newPage(first: Boolean) {
        finishPage()
        pageNo += 1
        val p = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        page = p
        val canvas = p.canvas
        y = margin + 16f
        if (first) {
            canvas.drawText(title.title, margin, y, big)
            y += 20f
            canvas.drawText(title.range, margin, y, text)
            y += lineHeight
            canvas.drawText(title.totalLine, margin, y, bold)
            y += lineHeight * 1.5f
        }
        canvas.drawText(labels.date, colDate, y, bold)
        canvas.drawText(labels.category, colCategory, y, bold)
        canvas.drawText(labels.paidBy, colPaidBy, y, bold)
        canvas.drawText(labels.note, colNote, y, bold)
        canvas.drawText(labels.amount, colAmountRight - bold.measureText(labels.amount), y, bold)
        y += 4f
        canvas.drawLine(margin, y, colAmountRight, y, small)
        y += lineHeight
    }

    private fun finishPage() {
        val p = page ?: return
        val footer = "${title.footer}  ·  $pageNo"
        p.canvas.drawText(footer, margin, height - margin / 2, small)
        doc.finishPage(p)
        page = null
    }

    /** Cuts text that would run into the next column and adds an ellipsis. */
    private fun fit(
        s: String,
        maxWidth: Float,
        paint: Paint,
    ): String {
        if (paint.measureText(s) <= maxWidth) return s
        val n = paint.breakText(s, true, maxWidth - paint.measureText("…"), null)
        return s.take(n) + "…"
    }
}
