package kr.yeobaek.note.export

import android.content.Intent
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.editor.DocView
import kr.yeobaek.note.ink.Paper
import kr.yeobaek.note.quiz.QuizLayout
import java.io.File
import java.io.OutputStream

/**
 * 내보내기: 필기를 합친 PDF.
 * 종이(양식·문항·내 선택)와 필기는 모두 벡터로 그려요 (PdfDocument). PDF 원본 쪽은 1400px 그림 위에 필기를 얹어요.
 */
object Export {
    private const val A4W = 595.28f
    private const val RASTER_W = 1400

    fun fileName(doc: Doc): String = (Y.clean(doc.title).ifBlank { "여백" }).replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".pdf"

    suspend fun build(doc: Doc, view: DocView, out: OutputStream) = withContext(Dispatchers.Main) {
        // 내보낸 종이는 늘 밝은 화면 색으로
        Paper.forceLight = true; QuizLayout.clearCache()
        val pdf = PdfDocument()
        try {
            doc.pages.forEachIndexed { i, pg ->
                val w = A4W; val h = A4W * pg.ratio
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), i + 1).create())
                val cv: Canvas = page.canvas
                val bg = if (doc.type == "pdf") withContext(Dispatchers.IO) { doc.pdf?.renderSync(i, RASTER_W) } else null
                view.renderPage(cv, i, w, 1f / 2f, pdf = bg, forExport = true)
                bg?.recycle()
                pdf.finishPage(page)
            }
            withContext(Dispatchers.IO) { pdf.writeTo(out) }
        } finally {
            pdf.close(); Paper.forceLight = false; QuizLayout.clearCache(); view.themeChanged()
        }
    }

    /** 공유 시트로 */
    suspend fun share(doc: Doc, view: DocView, start: (Intent) -> Unit) {
        val dir = File(Y.app.cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, fileName(doc))
        f.outputStream().use { build(doc, view, it) }
        val uri = FileProvider.getUriForFile(Y.app, Y.app.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(send, null))
    }
}
