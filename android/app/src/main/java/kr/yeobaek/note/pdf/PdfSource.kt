package kr.yeobaek.note.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** PDF 렌더링: 안드로이드 PdfRenderer (한 번에 한 쪽만 열 수 있어서 전용 스레드 하나로) */
class PdfSource private constructor(val file: File, private val pfd: ParcelFileDescriptor, private val r: PdfRenderer) {
    val count = r.pageCount
    val ratios = FloatArray(count)
    val widths = FloatArray(count)
    private val exec = Executors.newSingleThreadExecutor { Thread(it, "pdf").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    init {
        for (i in 0 until count) r.openPage(i).use { widths[i] = it.width.toFloat(); ratios[i] = if (it.width > 0) it.height.toFloat() / it.width else 1.4142f }
    }

    class Job { val token = AtomicInteger(0); fun cancel() { token.incrementAndGet() } }

    /** 너비 wPx로 i쪽을 그려서 cb로 (메인 스레드). job을 취소하면 버려요 */
    fun render(i: Int, wPx: Int, cb: (Bitmap) -> Unit): Job {
        val job = Job(); val t = job.token.get()
        exec.execute {
            if (closed || job.token.get() != t) return@execute
            val bm = runCatching { renderNow(i, wPx) }.getOrNull() ?: return@execute
            main.post { if (job.token.get() == t && !closed) cb(bm) else bm.recycle() }
        }
        return job
    }

    /** 확대했을 때: 쪽 너비 pageW(픽셀)로 볼 때의 clip 부분만 선명하게 */
    fun renderRegion(i: Int, pageW: Float, clip: android.graphics.RectF, cb: (Bitmap) -> Unit): Job {
        val job = Job(); val t = job.token.get()
        exec.execute {
            if (closed || job.token.get() != t) return@execute
            val bm = runCatching {
                val w = clip.width().toInt().coerceIn(1, 4096); val h = clip.height().toInt().coerceIn(1, 4096)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE)
                val k = pageW / widths[i].coerceAtLeast(1f)
                val m = android.graphics.Matrix().apply { setScale(k, k); postTranslate(-clip.left, -clip.top) }
                r.openPage(i).use { it.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                b
            }.getOrNull() ?: return@execute
            main.post { if (job.token.get() == t && !closed) cb(bm) else bm.recycle() }
        }
        return job
    }

    /** 이 스레드에서 바로 (내보내기·표지용) */
    fun renderSync(i: Int, wPx: Int): Bitmap? = runCatching { exec.submit<Bitmap> { renderNow(i, wPx) }.get() }.getOrNull()

    private fun renderNow(i: Int, wPx: Int): Bitmap {
        val w = wPx.coerceIn(16, 4096); val h = (w * ratios[i]).toInt().coerceIn(16, 6000)
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bm.eraseColor(Color.WHITE)
        r.openPage(i).use { it.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        return bm
    }

    /** 본문 검색 (안드로이드 15 이상). 결과: 쪽 번호 + 쪽 너비 기준 사각형 */
    fun search(q: String, cb: (List<Pair<Int, RectF>>) -> Unit) {
        if (Build.VERSION.SDK_INT < 35 || q.isBlank()) { cb(emptyList()); return }
        exec.execute {
            val out = ArrayList<Pair<Int, RectF>>()
            runCatching {
                for (i in 0 until count) r.openPage(i).use { pg ->
                    val W = pg.width.toFloat()
                    pg.searchText(q).forEach { m -> m.bounds.firstOrNull()?.let { b -> out.add(i to RectF(b.left / W, b.top / W, b.right / W, b.bottom / W)) } }
                }
            }
            main.post { cb(out) }
        }
    }

    val canSearch get() = Build.VERSION.SDK_INT >= 35

    fun close() {
        closed = true
        exec.execute { runCatching { r.close(); pfd.close() } }
        exec.shutdown()
    }

    companion object {
        fun open(f: File): PdfSource? = runCatching {
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfSource(f, pfd, PdfRenderer(pfd))
        }.getOrNull()

        /** 첫 쪽 표지 그림 */
        fun cover(src: PdfSource, out: File): Boolean = runCatching {
            val bm = src.renderSync(0, 360) ?: return false
            FileOutputStream(out).use { bm.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bm.recycle(); true
        }.getOrDefault(false)
    }
}
