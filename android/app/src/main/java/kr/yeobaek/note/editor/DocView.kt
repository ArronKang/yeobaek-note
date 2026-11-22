package kr.yeobaek.note.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import kr.yeobaek.note.Y as YY
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.data.Page
import kr.yeobaek.note.data.Stroke
import kr.yeobaek.note.data.Store
import kr.yeobaek.note.data.TextBox
import kr.yeobaek.note.ink.Ink
import kr.yeobaek.note.ink.Paper
import kr.yeobaek.note.ink.PaperColors
import kr.yeobaek.note.ink.Templates
import kr.yeobaek.note.pdf.PdfSource
import kr.yeobaek.note.quiz.Fonts
import kr.yeobaek.note.quiz.Quiz
import kr.yeobaek.note.quiz.QuizLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 필기 엔진 (engine.js + pull.js): 페이지 배치, 손가락 관성 스크롤, 고무줄, 핀치 줌,
 * 펜 필기·지우개·올가미·공간 삽입·텍스트, 문제지 톡/꾹 누르기.
 * 손가락으로 끌면 페이지 이동, 펜(과 마우스)은 필기. 손가락 필기는 설정에서 켤 때만.
 */
@SuppressLint("ViewConstructor")
class DocView(ctx: Context, val doc: Doc, private val host: Host) : View(ctx) {

    interface Host {
        fun onPage(n: Int, changed: Boolean) {}
        fun onTools() {}
        fun onAnyDown() {}
        fun quizCard(pi: Int, idx: Int, x: Float, y: Float, press: Boolean) {}
        fun cardDrag(x: Float, y: Float) {}
        fun cardUp(x: Float, y: Float, pen: Boolean) {}
        fun cardHover(x: Float, y: Float) {}
        fun cardHoverExit() {}
        fun editText(pi: Int, t: TextBox, isNew: Boolean) {}
        fun quizChanged() {}
        fun toast(s: String) {}
        fun onPull(active: Boolean) {}
    }

    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens
    private val ACT_W get() = dp(794f)

    /* =============== 배치 =============== */
    var zoom: Any = "width"            // width | fit | Float(실제 크기 배율)
        private set
    var W = 0f; private set
    var topPad = dp(96f)
        set(v) { if (field != v) { field = v; if (width > 0) relayout(null) } }
    private val bottomPad get() = dp(120f)
    private val gap get() = dp(14f)
    private var tops = FloatArray(0)
    private var contentH = 0f
    var sx = 0f; private set
    var sy = 0f; private set
    var pageIdx = 0; private set

    fun pageH(i: Int) = W * doc.pages[i].ratio
    fun pageLeft(): Float = if (W < width) (width - W) / 2f else -sx
    fun pageTop(i: Int): Float = tops[i] - sy - pullV
    private val addBtnH get() = 0f   // 웹 최종 디자인: + 버튼 없이 고무줄로만 새 페이지
    private val maxSy get() = max(0f, contentH - height)
    private val maxSx get() = max(0f, W - width)

    private fun computeW(): Float {
        val cw = width.toFloat(); val ch = height - topPad - dp(24f)
        val first = doc.pages.firstOrNull()?.ratio ?: YY.RATIO
        val w = when (val z = zoom) {
            "fit" -> min(cw - dp(8f), ch / first)
            "width" -> min(cw, dp(1600f))
            else -> ACT_W * (z as Float)
        }
        return w.coerceIn(dp(280f), dp(2400f))
    }

    private fun layoutTops() {
        tops = FloatArray(doc.pages.size)
        var y = topPad
        doc.pages.forEachIndexed { i, p -> tops[i] = y; y += W * p.ratio + gap }
        contentH = y - gap + gap + addBtnH + bottomPad
    }

    /** 확대가 바뀌어도 화면 가운데(또는 anchor) 자리를 지켜요 */
    fun relayout(anchor: FloatArray?) {
        if (width == 0) return
        val cx = anchor?.get(0) ?: (width / 2f); val cy = anchor?.get(1) ?: (height / 2f)
        val ax = if (W > 0) (sx + cx) / max(1f, max(W, width.toFloat())) else 0f
        val ay = if (contentH > 0) (sy + cy) / max(1f, contentH) else 0f
        val oldW = W
        W = computeW(); layoutTops()
        if (oldW > 0) { sx = ax * max(W, width.toFloat()) - cx; sy = ay * contentH - cy }
        clampScroll()
        if (oldW != W) dropCaches()
        updatePageIdx(); invalidate(); host.onTools()
    }

    fun setZoom(z: Any, anchor: FloatArray? = null) {
        zoom = if (z is Float) z.coerceIn(0.4f, 3f) else z
        relayout(anchor)
    }
    fun zoomRel(): Float = W / ACT_W

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { relayout(null) }

    private fun clampScroll() { sy = sy.coerceIn(0f, maxSy); sx = sx.coerceIn(0f, maxSx) }

    /** 리스트가 바뀐 뒤 (페이지 추가·삭제·순서) */
    fun rebuild() { layoutTops(); clampScroll(); dropCaches(); updatePageIdx(); invalidate(); host.onTools() }

    fun scrollToPage(i: Int, smooth: Boolean = true, offsetU: Float = 0f) {
        if (i !in doc.pages.indices) return
        val target = (tops[i] + offsetU * W - topPad + dp(8f)).coerceIn(0f, maxSy)
        if (!smooth) { sy = target; afterScroll(); return }
        animateScroll(target)
    }

    private fun animateScroll(target: Float) {
        stopGlide()
        val from = sy; val t0 = SystemClock.uptimeMillis(); val dur = 420f
        glideRun = object : Runnable { override fun run() {
            val t = ((SystemClock.uptimeMillis() - t0) / dur).coerceIn(0f, 1f)
            val e = 1 - (1 - t).pow(4)          // 감속 곡선
            sy = from + (target - from) * e; afterScroll()
            if (t < 1f) postOnAnimation(this) else glideRun = null
        } }
        postOnAnimation(glideRun)
    }

    private fun afterScroll() { clampScroll(); updatePageIdx(); invalidate() }

    private var peekRun: Runnable? = null
    private fun updatePageIdx() {
        if (tops.isEmpty()) return
        val mid = (height + topPad) / 2f
        var n = 0
        for (i in doc.pages.indices) { n = i; if (pageTop(i) + pageH(i) > mid) break }
        val changed = n != pageIdx; pageIdx = n
        host.onPage(n, changed)
        // 노트 맨 아래에 처음 닿으면 고무줄 단서 (멈춘 뒤에)
        if (doc.type == "note" && sy >= maxSy - 2 && maxSy > 0 && Store.flag("pullhint") == null) {
            peekRun?.let { removeCallbacks(it) }
            peekRun = Runnable { if (sy >= maxSy - 2 && scrolling == null && glideRun == null) pullPeek() }.also { postDelayed(it, 500) }
        }
    }

    /* =============== 페이지 그림 캐시 =============== */
    private class Cache(val bm: Bitmap, val rs: Float, var dirty: Boolean = true)
    private val caches = HashMap<Int, Cache>()
    private val pdfBm = HashMap<Int, Bitmap>()
    private val pdfJobs = HashMap<Int, PdfSource.Job>()
    private val pdfW = HashMap<Int, Int>()

    private fun dropCaches() {
        caches.values.forEach { it.bm.recycle() }; caches.clear()
    }
    fun dirty(pi: Int) { caches[pi]?.dirty = true; invalidate() }
    fun dirtyAll() { caches.values.forEach { it.dirty = true }; invalidate() }
    fun themeChanged() { QuizLayout.clearCache(); textCache.clear(); dirtyAll() }

    private fun rsFor(i: Int): Float = min(1f, min(3600f / W, 4096f / pageH(i)))

    private fun ensureCache(i: Int): Cache {
        val rs = rsFor(i)
        val bw = (W * rs).roundToInt().coerceAtLeast(1); val bh = (pageH(i) * rs).roundToInt().coerceAtLeast(1)
        var c = caches[i]
        if (c == null || c.bm.width != bw || c.bm.height != bh) {
            c?.bm?.recycle()
            c = Cache(Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888), rs); caches[i] = c
        }
        if (c.dirty) {
            renderPage(Canvas(c.bm), i, c.bm.width.toFloat(), c.rs, exclude = sel?.takeIf { it.pi == i }?.strokes); c.dirty = false
            cur?.takeIf { it.pi == i }?.let { it.liveBaked = false; it.baked = 0 }
        }
        if (doc.type == "pdf") requestPdf(i, bw)
        return c
    }

    private fun requestPdf(i: Int, wPx: Int) {
        val src = doc.pdf ?: return
        if (pdfW[i] == wPx && (pdfBm[i] != null || pdfJobs[i] != null)) return
        pdfJobs[i]?.cancel(); pdfW[i] = wPx
        pdfJobs[i] = src.render(i, wPx) { bm ->
            pdfJobs.remove(i); pdfBm.put(i, bm)?.recycle(); dirty(i)
        }
    }

    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * 페이지 한 장: 종이 → 양식 → PDF → 문제지 → 필기 → 글상자.
     * @param K 그림 너비(픽셀) @param px 화면 1px당 픽셀
     */
    fun renderPage(cv: Canvas, i: Int, K: Float, px: Float, exclude: List<Stroke>? = null, pdf: Bitmap? = pdfBm[i], forExport: Boolean = false) {
        val pg = doc.pages[i]; val c = Paper.c(); val H = K * pg.ratio
        if (doc.type == "pdf") { cv.drawColor(android.graphics.Color.WHITE); pdf?.let { tp.reset(); tp.isFilterBitmap = true; if (c.dark && !forExport) tp.colorFilter = DIM; cv.drawBitmap(it, null, RectF(0f, 0f, K, H), tp) } }
        else cv.drawColor(c.paper)
        if (doc.type == "note") Templates.draw(cv, pg.tpl, K, H, max(1f, dens * px), c)
        if (doc.type == "quiz") {
            cv.save(); val s = K / (QuizLayout.PW * QuizLayout.L); cv.scale(s, s)
            QuizLayout.draw(cv, doc, i, if (cardIdx >= 0 && cardPage == i) cardIdx else -1, anims, SystemClock.uptimeMillis())
            cv.restore()
        }
        val dark = c.dark && !(doc.type == "pdf")
        for (st in pg.strokes) if (exclude == null || st !in exclude) Ink.draw(cv, st, K, dark)
        for (t in pg.texts) if (t !== editing) drawText(cv, t, K, c)
    }

    private val DIM = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(.88f, .88f, .88f, 1f) })
    private val textCache = HashMap<TextBox, Pair<String, StaticLayout>>()
    private fun drawText(cv: Canvas, t: TextBox, K: Float, c: PaperColors) {
        if (t.t.isBlank()) return
        val col = if (c.dark && t.c.equals("#1A1A20", true)) 0xFFF2F2F6.toInt() else Ink.parse(t.c)
        val sig = "${t.t}|$K|$col|${t.w}|${t.fs}"
        val l = textCache[t]?.takeIf { it.first == sig }?.second ?: run {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = t.fs * K; color = col; typeface = Fonts.w(400) }
            StaticLayout.Builder.obtain(t.t, 0, t.t.length, p, max(1, (t.w * K).toInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(t.fs * K * 0.2f, 1f).setIncludePad(false).build().also { textCache[t] = sig to it }
        }
        cv.save(); cv.translate(t.x * K + 4f * K / W * dens, t.y * K + 2f * K / W * dens); l.draw(cv); cv.restore()
    }

    /** 사이드바 썸네일 */
    fun thumb(i: Int, w: Int): Bitmap {
        val h = (w * doc.pages[i].ratio).roundToInt()
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pb = pdfBm[i]
        renderPage(Canvas(bm), i, w.toFloat(), w / W.coerceAtLeast(1f), pdf = pb)
        return bm
    }

    /* =============== 그리기 =============== */
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(cv: Canvas) {
        val c = Paper.c()
        cv.drawColor(c.desk)
        if (tops.size != doc.pages.size) layoutTops()
        cv.save()
        pinch?.let { pc -> if (pc.moved) { cv.translate(pc.tx, pc.ty); cv.scale(pc.s, pc.s, pc.mx, pc.my) } }
        val left = pageLeft()
        val visLo = -height * 0.6f; val visHi = height * 1.6f
        val keep = HashSet<Int>()
        for (i in doc.pages.indices) {
            val top = pageTop(i); val h = pageH(i)
            if (top + h < visLo || top > visHi) continue
            keep.add(i)
            val cache = ensureCache(i)
            cv.save()
            if (bornPage == i) { val t = ((SystemClock.uptimeMillis() - bornT) / 500f).coerceIn(0f, 1f); val s = .97f + .03f * springEase(t); cv.scale(s, s, left + W / 2, top + h / 2); if (t < 1f) postInvalidateOnAnimation() else bornPage = -1 }
            val r = RectF(left, top, left + W, top + h)
            p.reset(); p.isFilterBitmap = true
            cv.drawBitmap(cache.bm, null, r, p)
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = dp(.5f); p.color = if (c.dark) 0x12FFFFFF else 0x14212529
            cv.drawRoundRect(r, dp(4f), dp(4f), p)
            drawOverlay(cv, i, left, top)
            cv.restore()
        }
        // (페이지 추가 버튼은 숨김: 고무줄로 당겨서)
        if (false && doc.type == "note" && doc.pages.isNotEmpty()) {
            val y = tops.last() + pageH(doc.pages.size - 1) + gap - sy - pullV
            val bw = min(dp(300f), W); val bx = left + (W - bw) / 2
            addRect.set(bx, y, bx + bw, y + dp(44f))
            if (y < height) {
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = if (c.dark) 0xFF45474E.toInt() else 0xFFCED4DA.toInt()
                p.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
                cv.drawRoundRect(addRect, dp(12f), dp(12f), p); p.pathEffect = null
                p.strokeWidth = dp(1.75f); p.strokeCap = Paint.Cap.ROUND; p.color = if (c.dark) 0xFFA4A7B0.toInt() else 0xFF6C757D.toInt()
                val cx = addRect.centerX(); val cy = addRect.centerY(); val a = dp(7f)
                cv.drawLine(cx - a, cy, cx + a, cy, p); cv.drawLine(cx, cy - a, cx, cy + a, p)
            }
        }
        cv.restore()
        drawPull(cv, c)
        // 안 보이는 페이지는 메모리에서 내림
        val drop = caches.keys.filter { it !in keep }
        drop.forEach { caches.remove(it)?.bm?.recycle() }
        if (doc.type == "pdf") pdfBm.keys.filter { it !in keep && abs(it - pageIdx) > 3 }.forEach { pdfBm.remove(it)?.recycle(); pdfW.remove(it); pdfJobs.remove(it)?.cancel() }
        if (anims.isNotEmpty()) {
            val now = SystemClock.uptimeMillis()
            anims.removeAll { now - it.t0 > 700 }
            dirty(cardPageAnim); if (anims.isNotEmpty()) postInvalidateOnAnimation()
        }
    }
    private val addRect = RectF()

    private fun drawOverlay(cv: Canvas, i: Int, left: Float, top: Float) {
        val c = Paper.c(); val K = W; val dark = c.dark && doc.type != "pdf"
        cv.save(); cv.translate(left, top)
        // 검색 결과
        search?.let { s -> s.list.forEachIndexed { k, (pi, r) -> if (pi == i) {
            p.reset(); p.color = 0x59FFD60A; cv.drawRect(r.left * K, r.top * K, r.right * K, r.bottom * K, p)
            if (k == s.cur) { p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = c.acc; cv.drawRect(r.left * K, r.top * K, r.right * K, r.bottom * K, p) }
        } } }
        val cu = cur
        if (cu != null && cu.pi == i) {
            cu.st?.let { st -> if (st.t == "hl" || !cu.liveBaked) Ink.draw(cv, st, K, dark) else Ink.draw(cv, st, K, dark, from = cu.baked) }
            cu.pred?.let { Ink.draw(cv, it, K, dark) }
            cu.lasso?.let { pts ->
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.6f); p.color = c.acc; p.strokeJoin = Paint.Join.ROUND
                p.pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
                path.reset(); pts.forEachIndexed { k, q -> if (k == 0) path.moveTo(q[0] * K, q[1] * K) else path.lineTo(q[0] * K, q[1] * K) }
                cv.drawPath(path, p); p.pathEffect = null
            }
            cu.space?.let { sp ->
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.6f); p.color = c.acc
                p.pathEffect = DashPathEffect(floatArrayOf(dp(10f), dp(6f)), 0f)
                cv.drawLine(0f, sp.y0 * K, K, sp.y0 * K, p); p.pathEffect = null
            }
        }
        sel?.takeIf { it.pi == i }?.let { s ->
            s.strokes.forEach { Ink.draw(cv, it, K, dark) }
            val b = s.box; val x = b[0] * K; val y = b[1] * K; val w = (b[2] - b[0]) * K; val h = (b[3] - b[1]) * K
            p.reset(); p.color = (c.acc and 0x00FFFFFF) or 0x14000000; cv.drawRect(x, y, x + w, y + h, p)
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.6f); p.color = c.acc
            p.pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), -dashT * dp(20f)); cv.drawRect(x, y, x + w, y + h, p); p.pathEffect = null
            for ((cx, cy) in listOf(x to y, x + w to y, x to y + h, x + w to y + h)) {
                p.style = Paint.Style.FILL; p.color = android.graphics.Color.WHITE; cv.drawCircle(cx, cy, dp(5f), p)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.8f); p.color = c.acc; cv.drawCircle(cx, cy, dp(5f), p)
            }
        }
        cv.restore()
    }
    private var dashT = 0f

    /* =============== 기록(실행취소) =============== */
    class Entry(val pi: Int, val undo: () -> Unit, val redo: () -> Unit)
    val hist = ArrayList<Entry>(); val redoList = ArrayList<Entry>()

    private fun pushHist(pi: Int, undo: () -> Unit, redo: () -> Unit) {
        hist.add(Entry(pi, undo, redo)); redoList.clear(); queueSave(); host.onTools()
    }
    fun undo() { val e = hist.removeLastOrNull() ?: return; clearSel(true); e.undo(); redoList.add(e); textCache.clear(); dirty(e.pi); queueSave(); host.onTools() }
    fun redo() { val e = redoList.removeLastOrNull() ?: return; clearSel(true); e.redo(); hist.add(e); textCache.clear(); dirty(e.pi); queueSave(); host.onTools() }

    /* =============== 저장 =============== */
    private val main = Handler(Looper.getMainLooper())
    private val saveRun = Runnable { Docs.save(doc) }
    fun queueSave() { main.removeCallbacks(saveRun); main.postDelayed(saveRun, YY.settings.autosave.toLong().coerceAtLeast(0)) }
    fun saveNow() { main.removeCallbacks(saveRun); Docs.save(doc) }

    /* =============== 올가미 선택 =============== */
    class Sel(val pi: Int, var strokes: MutableList<Stroke>, var box: FloatArray)
    var sel: Sel? = null; private set

    fun clearSel(silent: Boolean = false) {
        val old = sel ?: return; sel = null; dirty(old.pi)
        if (!silent) host.onTools()
    }
    private fun setSel(s: Sel?) { val old = sel; sel = s; old?.let { dirty(it.pi) }; s?.let { dirty(it.pi) }; host.onTools() }

    fun deleteSel() {
        val s = sel ?: return; val pg = doc.pages[s.pi]
        val items = s.strokes.mapNotNull { st -> pg.strokes.indexOf(st).takeIf { it >= 0 }?.let { st to it } }.sortedByDescending { it.second }
        items.forEach { pg.strokes.removeAt(it.second) }
        sel = null
        val asc = items.reversed()
        pushHist(s.pi, { asc.forEach { (st, i) -> pg.strokes.add(min(i, pg.strokes.size), st) } }, { asc.forEach { (st, _) -> pg.strokes.remove(st) } })
        dirty(s.pi)
    }
    fun copySel() {
        val s = sel ?: return; val pg = doc.pages[s.pi]
        val clones = s.strokes.map { it.copyDeep() }.toMutableList()
        Ink.move(clones, 0.03f, 0.03f)
        dirty(s.pi)
        pg.strokes.addAll(clones)
        sel = Sel(s.pi, clones, Ink.bbox(clones))
        pushHist(s.pi, { pg.strokes.removeAll(clones.toSet()) }, { pg.strokes.addAll(clones) })
        dirty(s.pi)
    }
    fun recolorSel(color: String) {
        val s = sel ?: return; val list = s.strokes.toList(); val old = list.map { it.c }
        list.forEach { it.c = color }
        pushHist(s.pi, { list.forEachIndexed { i, st -> st.c = old[i] } }, { list.forEach { it.c = color } })
        invalidate()
    }

    /* =============== 페이지 =============== */
    fun addPage(tpl: String? = null, stay: Boolean = false): Int {
        val last = doc.pages.lastOrNull()
        doc.pages.add(Docs.makePage(tpl ?: last?.tpl ?: "lined"))
        layoutTops(); queueSave(); host.onTools()
        val i = doc.pages.size - 1
        if (!stay) scrollToPage(i)
        invalidate(); return i
    }
    fun setTemplate(i: Int, tpl: String) { doc.pages[i].tpl = tpl; dirty(i); queueSave() }
    fun pagesChanged() { hist.clear(); redoList.clear(); sel = null; rebuild(); queueSave() }

    /* =============== 문제지 =============== */
    private val anims = ArrayList<QuizLayout.Anim>()
    private var cardPageAnim = -1
    var cardIdx = -1; private set
    var cardPage = -1; private set
    fun setCard(pi: Int, idx: Int) { val old = cardPage; cardPage = pi; cardIdx = idx; if (old >= 0) dirty(old); if (pi >= 0) dirty(pi) }

    /** 채점 표시를 긋는 움직임 */
    fun animate(idx: Int, kinds: List<String>) {
        if (doc.type != "quiz") return
        val pi = doc.pages.indexOfFirst { it.qi?.contains(idx) == true }.takeIf { it >= 0 } ?: return
        val now = SystemClock.uptimeMillis()
        kinds.forEach { anims.add(QuizLayout.Anim(idx, it, now)) }
        cardPageAnim = pi; dirty(pi)
    }
    fun animateScore() { if (doc.type == "quiz" && doc.pages.isNotEmpty()) { anims.add(QuizLayout.Anim(-1, "score", SystemClock.uptimeMillis())); cardPageAnim = 0; dirty(0) } }
    fun quizRepaint() { QuizLayout.clearCache(); dirtyAll() }

    /** 문항이 있는 페이지로 */
    fun goToProblem(idx: Int) {
        val pi = doc.pages.indexOfFirst { it.qi?.contains(idx) == true }; if (pi < 0) return
        val top = QuizLayout.blockTop(doc, pi, idx) ?: 0f
        val target = (tops[pi] + top / QuizLayout.PW * W - height / 2f + dp(60f)).coerceIn(0f, maxSy)
        animateScroll(target)
    }

    /* =============== PDF 검색 =============== */
    class Search(val q: String, val list: List<Pair<Int, RectF>>, var cur: Int, var done: Boolean)
    var search: Search? = null; private set
    fun runSearch(q: String, done: () -> Unit) {
        val src = doc.pdf ?: return
        if (q.isBlank()) { search = null; invalidate(); done(); return }
        search = Search(q, emptyList(), 0, false); done()
        src.search(q) { res -> search = Search(q, res, 0, true); if (res.isNotEmpty()) searchGo(0); invalidate(); done() }
    }
    fun searchGo(i: Int) {
        val s = search ?: return; if (s.list.isEmpty()) return
        s.cur = (i + s.list.size) % s.list.size
        val (pi, r) = s.list[s.cur]
        animateScroll((tops[pi] + r.top * W - height / 2f).coerceIn(0f, maxSy)); invalidate()
    }

    /* =============== 손가락 관성 스크롤 =============== */
    private var glideRun: Runnable? = null
    fun stopGlide() { glideRun?.let { removeCallbacks(it) }; glideRun = null }

    private fun glide(vx0: Float, vy0: Float) {
        stopGlide()
        var vx = vx0; var vy = vy0
        if (hypot(vx, vy) < 0.12f * dens) return
        var last = SystemClock.uptimeMillis()
        glideRun = object : Runnable { override fun run() {
            val now = SystemClock.uptimeMillis(); val dt = min(32L, now - last).toFloat().coerceAtLeast(1f); last = now
            val k = 0.9965f.pow(dt)              // 마찰: 부드럽게 감속
            vx *= k; vy *= k
            val t0 = sy
            sx += vx * dt; sy += vy * dt; clampScroll()
            // 벽에 부딪힘: 남은 속도만큼 눌렸다가 튕겨 나옴
            if (abs(vy) > 0.4f * dens && abs(sy - t0) < abs(vy * dt) * 0.5f) { glideRun = null; bounce(sign(vy) * min(90f, abs(vy) / dens * 45f)); afterScroll(); return }
            afterScroll()
            if (hypot(vx, vy) < 0.02f * dens) { glideRun = null; return }
            postOnAnimation(this)
        } }
        postOnAnimation(glideRun)
    }

    /* =============== 고무줄 (pull.js) =============== */
    private var pullRaw = 0f
    private var pullV = 0f
    private var pullArmed = false
    private var bouncing = false
    private var springRun: Runnable? = null
    private val ARM get() = dp(150f)

    private fun band(raw: Float, D: Float): Float { val a = abs(raw); return sign(raw) * D * (1 - 1 / (a * 0.55f / D + 1)) }

    private fun pull(raw: Float) {
        stopSpring(); bouncing = false
        pullRaw = raw
        pullV = if (raw != 0f) band(raw, height * 0.6f) else 0f
        paintPull()
    }
    private fun paintPull() {
        val canAdd = doc.type == "note" && pullV > 0 && !bouncing
        val armed = canAdd && pullV / ARM >= 1f
        if (armed != pullArmed) { pullArmed = armed; if (armed) YY.haptic(12) }
        host.onPull(pullV != 0f)
        invalidate()
    }
    private fun drawPull(cv: Canvas, c: PaperColors) {
        if (!(doc.type == "note" && pullV > 0 && !bouncing)) return
        val k = min(1f, pullV / ARM)
        val room = bottomPad + pullV - dp(24f)
        val left = pageLeft()
        val bottom = height - dp(16f)
        val fr = RectF(left, bottom - max(0f, room), left + W, bottom)
        p.reset(); p.isAntiAlias = true
        val alpha = (min(1f, k * 1.6f) * 255).toInt()
        if (pullArmed) {
            p.color = c.paper; p.alpha = alpha; cv.drawRoundRect(fr, dp(4f), dp(4f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(.5f); p.color = if (c.dark) 0x1AFFFFFF else 0x1F212529; cv.drawRoundRect(fr, dp(4f), dp(4f), p)
        } else {
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = if (c.dark) 0xFF45474E.toInt() else 0xFFCED4DA.toInt(); p.alpha = alpha
            p.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f); cv.drawRoundRect(fr, dp(4f), dp(4f), p); p.pathEffect = null
        }
        // + 와 둘레 고리
        val cy = height - max(dp(8f), room / 2 - dp(20f)) - dp(20f); val cx = left + W / 2
        val s = (0.45f + 0.55f * k) * (if (pullArmed) 1.14f else 1f)
        cv.save(); cv.translate(cx, cy); cv.scale(s, s)
        val r = dp(17f)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
        if (pullArmed) { p.style = Paint.Style.FILL; p.color = if (c.dark) 0x1CFFFFFF else 0x1A212529; cv.drawCircle(0f, 0f, r, p); p.style = Paint.Style.STROKE }
        p.color = if (c.dark) 0x1AFFFFFF else 0x1F212529; cv.drawCircle(0f, 0f, r, p)
        p.color = c.acc; p.strokeCap = Paint.Cap.ROUND
        cv.drawArc(RectF(-r, -r, r, r), -90f, 360f * k, false, p)
        p.color = if (pullArmed) c.acc else (if (c.dark) 0xFFA4A7B0.toInt() else 0xFF6C757D.toInt())
        val a = dp(7f); cv.drawLine(-a, 0f, a, 0f, p); cv.drawLine(0f, -a, 0f, a, p)
        cv.restore()
    }

    // 용수철: 살짝 지나쳤다가 제자리 (질량 1, 강성 260, 감쇠 24)
    private fun spring(x0: Float, v0: Float, done: (() -> Unit)?) {
        stopSpring()
        var x = x0; var v = v0; var last = SystemClock.uptimeMillis()
        springRun = object : Runnable { override fun run() {
            val now = SystemClock.uptimeMillis(); val dt = min(32L, now - last).coerceAtLeast(1L) / 1000f; last = now
            val a = -260f * x - 24f * v; v += a * dt; x += v * dt
            if (abs(x) < 0.4f * dens && abs(v) < 4f * dens) { pullV = 0f; paintPull(); springRun = null; done?.invoke(); return }
            pullV = x; paintPull(); postOnAnimation(this)
        } }
        postOnAnimation(springRun)
    }
    private fun stopSpring() { springRun?.let { removeCallbacks(it) }; springRun = null }

    private fun pullRelease() {
        val v = pullV; pullRaw = 0f
        if (pullArmed && doc.type == "note") {
            // 틀이 있던 자리에 진짜 페이지가 생김
            val frameTop = height - dp(16f) - (bottomPad + pullV - dp(24f))
            pullArmed = false; pullV = 0f; paintPull()
            val i = addPage(stay = true)
            sy = (tops[i] - frameTop).coerceIn(0f, maxSy); afterScroll()
            bornPage = i; bornT = SystemClock.uptimeMillis(); invalidate()
            YY.haptic(10); return
        }
        spring(v, 0f, null)
    }
    private var bornPage = -1; private var bornT = 0L
    private fun springEase(t: Float): Float { if (t >= 1f) return 1f; return 1 - (Math.exp(-6.0 * t) * Math.cos(10.0 * t)).toFloat() }

    // 처음 노트 맨 아래에 닿았을 때 한 번: 틀이 살짝 딸려 나왔다 들어감
    private fun pullPeek() {
        if (scrolling != null || springRun != null || Store.flag("pullhint") != null) return
        Store.setFlag("pullhint", "1"); bouncing = false; spring(0f, 520f * dens, null)
    }
    private fun bounce(push: Float) {
        if (scrolling != null) return
        pullArmed = false; bouncing = true
        spring(0f, push * 9f * dens) { bouncing = false }
    }

    /* =============== 입력 =============== */
    private class Scroll(val id: Int, val x0: Float, val y0: Float, val ts: Long, val l0: Float, val t0: Float) {
        val hist = ArrayList<FloatArray>()
    }
    private var scrolling: Scroll? = null

    private class Pinch(val d0: Float, val t0: Long, var maxN: Int, val mx: Float, val my: Float, val pi: Int, val fx: Float, val fy: Float) {
        var moved = false; var s = 1f; var tx = 0f; var ty = 0f
    }
    private var pinch: Pinch? = null
    private val touches = HashMap<Int, FloatArray>()

    private class SpaceS(val y0: Float, val strokes: List<Stroke>, val texts: List<TextBox>) { var dy = 0f }
    private class Cur(val pi: Int, val id: Int, val mode: String, val touch: Boolean, val t0: Long, val x0: Float, val y0: Float) {
        var st: Stroke? = null
        val removed = ArrayList<Pair<Stroke, Int>>(); val added = ArrayList<Stroke>()
        var lasso: MutableList<FloatArray>? = null
        var move: FloatArray? = null; var mdx = 0f; var mdy = 0f
        var scaleO: FloatArray? = null; var scaleD0 = 0f; var scaleTot = 1f
        var space: SpaceS? = null
        var snapped = false; var snapOld: MutableList<FloatArray>? = null
        var pred: Stroke? = null
        var liveBaked = false; var baked = 0
        var erased = false
    }
    private var cur: Cur? = null

    var penDown = false; private set
    private var lastPen = -1_000_000L
    private val palmWin get() = when (YY.settings.palm) { "weak" -> 250; "strong" -> 2500; else -> 900 }
    private fun penRecent() = penDown || SystemClock.uptimeMillis() - lastPen < palmWin
    private fun isPalm(ev: MotionEvent, i: Int) = ev.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER && max(ev.getTouchMajor(i), ev.getTouchMinor(i)) > dp(46f)

    private var predictor: MotionEventPredictor? = null

    // 꾹 누르기
    private var lpId = -1; private var lpX = 0f; private var lpY = 0f
    private val lpRun = Runnable { fireLongPress() }
    private var cardDragId = -1
    private var cardPenHover = false

    /** 화면 좌표 → (페이지, 정규화 좌표) */
    private fun hitPage(x: Float, y: Float): Int {
        val left = pageLeft()
        if (x < left || x > left + W) return -1
        for (i in doc.pages.indices) { val t = pageTop(i); if (y >= t && y <= t + pageH(i)) return i }
        return -1
    }
    private fun nearestPage(y: Float): Int {
        var best = pageIdx; var bd = Float.MAX_VALUE
        for (i in doc.pages.indices) { val t = pageTop(i); val d = if (y < t) t - y else if (y > t + pageH(i)) y - t - pageH(i) else 0f; if (d < bd) { bd = d; best = i } }
        return best
    }
    private fun norm(pi: Int, x: Float, y: Float, pr: Float = .5f) = floatArrayOf((x - pageLeft()) / W, (y - pageTop(pi)) / W, (pr * 100).roundToInt() / 100f)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val ai = ev.actionIndex
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(ev, ai)
            MotionEvent.ACTION_MOVE -> moveAll(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(ev, ai, false)
            MotionEvent.ACTION_CANCEL -> { for (i in 0 until ev.pointerCount) up(ev, i, true) }
        }
        return true
    }

    private fun toolOf(ev: MotionEvent, i: Int): String = when (ev.getToolType(i)) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> "pen"
        MotionEvent.TOOL_TYPE_MOUSE -> "mouse"
        else -> "touch"
    }

    private fun haltFingers() {
        stopGlide(); scrolling = null
        pinch = null; touches.clear()
        if (pullRaw != 0f) { pullRaw = 0f; spring(pullV, 0f, null) }
    }

    private fun down(ev: MotionEvent, i: Int) {
        val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i); val tool = toolOf(ev, i)
        host.onAnyDown()
        if (tool == "pen") {
            penDown = true; lastPen = SystemClock.uptimeMillis(); haltFingers()
            // 펜보다 먼저 닿은 손바닥이 그린 획은 지움
            cur?.takeIf { it.touch }?.let { c0 -> cur = null; c0.st?.let { doc.pages[c0.pi].strokes.remove(it) }; dirty(c0.pi) }
        }
        if (tool == "touch") {
            if (penRecent() || isPalm(ev, i)) return
            touches[id] = floatArrayOf(x, y)
            if (touches.size >= 2) { startPinch(); return }
            if (!YY.settings.finger) {
                stopGlide(); stopSpring()
                if (pinch != null) return
                scrolling = Scroll(id, x, y, SystemClock.uptimeMillis(), sx, sy).also { it.hist.add(floatArrayOf(x, y, SystemClock.uptimeMillis().toFloat())) }
                startLP(id, x, y)
                return
            }
        }
        if (cur != null) return
        // 노트 아래 + 버튼
        
        var pi = hitPage(x, y)
        var mode = YY.mode
        if (tool == "pen" && (ev.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY)) != 0) mode = if (YY.settings.penButton == "lasso") "lasso" else "eraser"
        if (ev.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER) mode = "eraser"
        if (pi < 0) { if (mode == "lasso" || mode == "eraser") pi = nearestPage(y) else return }
        val pg = doc.pages[pi]
        val pr = Ink.pressure(tool == "pen", ev.getPressure(i))
        val pt = norm(pi, x, y, pr)
        if (mode == "text") {
            // 기존 글상자를 누르면 고치기, 빈 곳이면 새로
            val hitT = pg.texts.lastOrNull { t -> pt[0] >= t.x && pt[0] <= t.x + t.w && pt[1] >= t.y && pt[1] <= t.y + t.fs * 2.2f * max(1, t.t.count { it == '\n' } + 1) }
            if (hitT != null) host.editText(pi, hitT, false)
            else {
                val pen = YY.curPen()
                val t = TextBox(YY.uid(), min(pt[0], 0.8f), pt[1], 0.4f, 0.028f, if (pen.kind == "pen") pen.color else "#1A1A20", "")
                pg.texts.add(t); host.editText(pi, t, true)
            }
            return
        }
        val c = Cur(pi, id, mode, tool == "touch", SystemClock.uptimeMillis(), x, y); cur = c
        if (mode == "eraser") { eraseAt(c, pt); return }
        if (mode == "lasso") {
            val s = sel
            if (s != null && s.pi == pi) {
                val tol = dp(16f) / W; val b = s.box
                val corners = listOf(floatArrayOf(b[0], b[1]), floatArrayOf(b[2], b[1]), floatArrayOf(b[0], b[3]), floatArrayOf(b[2], b[3]))
                val opp = listOf(floatArrayOf(b[2], b[3]), floatArrayOf(b[0], b[3]), floatArrayOf(b[2], b[1]), floatArrayOf(b[0], b[1]))
                for (k in 0..3) if (Ink.dist(pt, corners[k]) < tol) { c.scaleO = opp[k]; c.scaleD0 = Ink.dist(corners[k], opp[k]); return }
                if (pt[0] >= b[0] && pt[0] <= b[2] && pt[1] >= b[1] && pt[1] <= b[3]) { c.move = pt; return }
            }
            if (sel != null) setSel(null)
            c.lasso = mutableListOf(pt); invalidate(); host.onTools(); return
        }
        if (mode == "space") {
            val y0 = pt[1]
            c.space = SpaceS(y0, pg.strokes.filter { s -> s.p.all { it[1] >= y0 - 0.002f } }, pg.texts.filter { it.y >= y0 - 0.002f })
            invalidate(); return
        }
        val pen = YY.curPen()
        val st = Stroke(pen.kind, pen.color, pen.mm / YY.MM, if (pen.kind == "hl") pen.alpha else 1f, if (pen.kind == "pen" && pen.type != null && pen.type != "ball") pen.type else null, mutableListOf(pt))
        if (pen.kind == "pen") st.live = true
        c.st = st
        if (tool == "pen") { predictor = predictor ?: runCatching { MotionEventPredictor.newInstance(this) }.getOrNull(); predictor?.record(ev) }
        if (pen.kind == "pen") startLP(id, x, y)
        invalidate()
    }

    private fun moveAll(ev: MotionEvent) {
        for (i in 0 until ev.pointerCount) {
            val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i)
            if (toolOf(ev, i) == "pen") lastPen = SystemClock.uptimeMillis()
            if (lpId == id && hypot(x - lpX, y - lpY) > dp(9f)) cancelLP()
            if (cardDragId == id) { host.cardDrag(x, y); continue }
            if (touches.containsKey(id)) { touches[id] = floatArrayOf(x, y); if (pinch != null) { movePinch(); continue } }
            val S = scrolling
            if (S != null && S.id == id) {
                S.hist.add(floatArrayOf(x, y, SystemClock.uptimeMillis().toFloat())); if (S.hist.size > 6) S.hist.removeAt(0)
                val want = S.t0 - (y - S.y0)
                // 끝을 넘어 당기면 고무줄처럼 늘어남
                if (want > maxSy) { sy = maxSy; pull(want - maxSy) }
                else if (want < 0) { sy = 0f; pull(want) }
                else { if (pullRaw != 0f) pull(0f); sy = want }
                sx = (S.l0 - (x - S.x0)).coerceIn(0f, maxSx)
                updatePageIdx(); invalidate()
                continue
            }
            val c = cur ?: continue
            if (c.id != id) continue
            moveCur(ev, i, c)
        }
    }

    private fun moveCur(ev: MotionEvent, i: Int, c: Cur) {
        val pg = doc.pages[c.pi]
        val x = ev.getX(i); val y = ev.getY(i)
        c.move?.let { last ->
            val q = norm(c.pi, x, y); val dx = q[0] - last[0]; val dy = q[1] - last[1]
            val s = sel ?: return; Ink.move(s.strokes, dx, dy)
            s.box = floatArrayOf(s.box[0] + dx, s.box[1] + dy, s.box[2] + dx, s.box[3] + dy)
            c.move = q; c.mdx += dx; c.mdy += dy; invalidate(); return
        }
        c.scaleO?.let { o ->
            val q = norm(c.pi, x, y); val d = Ink.dist(q, o); val tot = (d / c.scaleD0).coerceIn(0.1f, 8f); val k = tot / c.scaleTot
            val s = sel ?: return; Ink.scale(s.strokes, k, o[0], o[1]); c.scaleTot = tot; s.box = Ink.bbox(s.strokes); invalidate(); return
        }
        c.lasso?.let { it.add(norm(c.pi, x, y)); invalidate(); return }
        c.space?.let { sp ->
            val q = norm(c.pi, x, y); val want = max(-0.2f, q[1] - sp.y0); val dy = want - sp.dy
            Ink.move(sp.strokes, 0f, dy); sp.texts.forEach { it.y += dy }; sp.dy = want; textCache.clear(); dirty(c.pi); return
        }
        if (c.mode == "eraser") {
            for (h in 0 until ev.historySize) eraseAt(c, norm(c.pi, ev.getHistoricalX(i, h), ev.getHistoricalY(i, h)))
            eraseAt(c, norm(c.pi, x, y)); return
        }
        val st = c.st ?: return
        if (c.snapped) return
        val isPen = ev.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS
        fun addPt(px: Float, py: Float, pr: Float) {
            val pt = norm(c.pi, px, py, Ink.pressure(isPen, pr)); val last = st.p.last()
            // 압력 흔들림 완화 (지수 평활)
            pt[2] = ((last[2] * 0.6f + pt[2] * 0.4f) * 100).roundToInt() / 100f
            val d = hypot(pt[0] - last[0], pt[1] - last[1])
            if (d < 0.0005f) return
            // 갑자기 멀리 튄 점(센서 노이즈)은 버림
            if (d > 0.12f && st.p.size > 1) return
            st.p.add(pt)
        }
        for (h in 0 until ev.historySize) addPt(ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalPressure(i, h))
        addPt(x, y, ev.getPressure(i))
        // 볼펜·만년필은 지나간 마디를 바로 캐시에 구워서 매 프레임 다시 그리지 않음
        if (st.t == "pen") bakeLive(c, st)
        if (isPen && st.t == "pen") {
            predictor?.record(ev)
            c.pred = predictor?.predict()?.let { pe ->
                val last = st.p.last(); val q = norm(c.pi, pe.x, pe.y, last[2]); pe.recycle()
                if (hypot(q[0] - last[0], q[1] - last[1]) > 0.03f || st.p.size < 2) null
                else Stroke(st.t, st.c, st.s, st.a, st.k, mutableListOf(st.p[st.p.size - 2], last, q)).also { it.live = true }
            }
        }
        invalidate()
        // 모양 보정: 끝에서 잠깐 멈추면 직선·원·사각형으로
        main.removeCallbacks(snapRun)
        if (YY.settings.snap && st.t == "pen" && st.p.size > 8) main.postDelayed(snapRun, 600)
    }

    private val snapRun = Runnable {
        val c = cur ?: return@Runnable; val st = c.st ?: return@Runnable
        val np = Ink.detectShape(st.p) ?: return@Runnable
        c.snapOld = st.p; st.p = np; c.snapped = true; c.liveBaked = false; c.pred = null; dirty(c.pi); YY.haptic(15)
    }

    /** 다음 점이 와서 모양이 정해진 마디(0..n-2)만 캐시에 굽고, 끝 마디는 화면에만 */
    private fun bakeLive(c: Cur, st: Stroke) {
        val cache = caches[c.pi]
        if (cache == null || cache.dirty) { c.liveBaked = false; return }
        val n = st.p.size
        if (n < 2) return
        if (!c.liveBaked) c.baked = 0
        val cv = Canvas(cache.bm); val K = cache.bm.width.toFloat()
        Ink.draw(cv, st, K, Paper.c().dark && doc.type != "pdf", from = c.baked, to = n - 1)
        c.baked = n - 1
        c.liveBaked = true
    }

    private fun up(ev: MotionEvent, i: Int, cancel: Boolean) {
        val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i); val tool = toolOf(ev, i)
        if (tool == "pen") { penDown = false; lastPen = SystemClock.uptimeMillis() }
        if (lpId == id) cancelLP()
        if (cardDragId == id) { cardDragId = -1; host.cardUp(x, y, tool == "pen"); return }
        if (touches.containsKey(id)) {
            touches.remove(id)
            val pc = pinch
            if (pc != null) { if (touches.size < 2) endPinch(pc, cancel); return }
        }
        val S = scrolling
        if (S != null && S.id == id) {
            scrolling = null
            val now = SystemClock.uptimeMillis()
            if (!cancel && now - S.ts < 350 && hypot(x - S.x0, y - S.y0) < dp(10f) && doc.type == "quiz") {
                val pi = hitPage(x, y); if (pi >= 0 && quizTap(pi, x, y)) return
            }
            if (pullRaw != 0f) { pullRelease(); return }
            val hs = S.hist.filter { now - it[2] < 100 }
            if (hs.size >= 2 && !cancel) {
                val a = hs.first(); val b = hs.last(); val dt = max(8f, b[2] - a[2])
                glide(-(b[0] - a[0]) / dt, -(b[1] - a[1]) / dt)
            }
            return
        }
        val c = cur ?: return
        if (c.id != id) return
        main.removeCallbacks(snapRun)
        cur = null
        val pg = doc.pages[c.pi]
        when {
            c.move != null -> { val s = sel; if (s != null && abs(c.mdx) + abs(c.mdy) > 0.0005f) { val l = s.strokes.toList(); val dx = c.mdx; val dy = c.mdy; pushHist(c.pi, { Ink.move(l, -dx, -dy); sel?.let { it.box = Ink.bbox(it.strokes) } }, { Ink.move(l, dx, dy) }) }; invalidate() }
            c.scaleO != null -> { val s = sel; val k = c.scaleTot; val o = c.scaleO!!; if (s != null && abs(k - 1) > 0.002f) { val l = s.strokes.toList(); pushHist(c.pi, { Ink.scale(l, 1 / k, o[0], o[1]) }, { Ink.scale(l, k, o[0], o[1]) }) }; invalidate() }
            c.lasso != null -> {
                val poly = c.lasso!!
                val chosen = if (poly.size >= 4) pg.strokes.filter { s -> s.p.count { Ink.inPoly(it, poly) } / s.p.size.toFloat() >= 0.5f }.toMutableList() else mutableListOf()
                setSel(if (chosen.isNotEmpty()) Sel(c.pi, chosen, Ink.bbox(chosen)) else null)
                if (sel != null) flowDash()
            }
            c.space != null -> {
                val sp = c.space!!; val dy = sp.dy
                if (abs(dy) > 0.0005f) pushHist(c.pi,
                    { Ink.move(sp.strokes, 0f, -dy); sp.texts.forEach { it.y -= dy } },
                    { Ink.move(sp.strokes, 0f, dy); sp.texts.forEach { it.y += dy } })
                textCache.clear(); dirty(c.pi)
            }
            c.mode == "eraser" -> {
                val rem = c.removed.toList(); val add = c.added.toList()
                if (rem.isNotEmpty() || add.isNotEmpty()) {
                    val asc = rem.sortedBy { it.second }
                    pushHist(c.pi,
                        { add.forEach { pg.strokes.remove(it) }; asc.forEach { (st, idx) -> pg.strokes.add(min(idx, pg.strokes.size), st) } },
                        { rem.forEach { pg.strokes.remove(it.first) }; pg.strokes.addAll(add) })
                }
            }
            c.st != null -> {
                val st = c.st!!
                if (!cancel && doc.type == "quiz" && SystemClock.uptimeMillis() - c.t0 < 350 && hypot(x - c.x0, y - c.y0) < dp(8f) && quizTap(c.pi, c.x0, c.y0)) {
                    // 보기 위를 톡: 점은 지우고 고르기로
                    dirty(c.pi)
                } else if (cancel && st.p.size < 3) { dirty(c.pi) }
                else {
                    st.live = false; c.pred = null
                    pg.strokes.add(st)
                    dirty(c.pi)
                    pushHist(c.pi, { pg.strokes.remove(st) }, { pg.strokes.add(st) })
                    if (c.snapped) { val oldP = c.snapOld!!; val newP = st.p; hist.add(Entry(c.pi, { st.p = oldP }, { st.p = newP })) }
                }
            }
        }
        invalidate(); host.onTools()
    }

    private fun flowDash() {
        val t0 = SystemClock.uptimeMillis()
        post(object : Runnable { override fun run() {
            val k = min(1f, (SystemClock.uptimeMillis() - t0) / 400f); dashT = k; invalidate()
            if (k < 1f) postOnAnimation(this) else dashT = 0f
        } })
    }

    private fun eraseAt(c: Cur, pt: FloatArray) {
        val pg = doc.pages[c.pi]
        val r = YY.eraser.mm / YY.MM / 2; val step = max(0.004f, r / 2)
        var any = false
        for (i in pg.strokes.indices.reversed()) {
            val st = pg.strokes[i]
            if (!Ink.hits(st, pt, r)) continue
            any = true
            if (!c.added.remove(st)) c.removed.add(st to i)
            pg.strokes.removeAt(i)
            if (YY.eraser.partial) {
                val D = Ink.densify(st.p, step); val pieces = ArrayList<MutableList<FloatArray>>(); var run = ArrayList<FloatArray>()
                D.forEach { q -> if (Ink.dist(q, pt) <= r + st.s / 2) { if (run.size > 1) pieces.add(run); run = ArrayList() } else run.add(q) }
                if (run.size > 1) pieces.add(run)
                pieces.reversed().forEach { run2 -> val ns = Stroke(st.t, st.c, st.s, st.a, st.k, run2); pg.strokes.add(i, ns); c.added.add(ns) }
            }
        }
        if (any) { if (sel?.pi == c.pi) clearSel(true); dirty(c.pi) }
    }

    /* ---------- 핀치 ---------- */
    private fun startPinch() {
        scrolling = null; stopGlide(); cancelLP()
        // 손가락 필기 중이던 획은 없던 일로
        cur?.takeIf { it.touch }?.let { c0 -> cur = null; dirty(c0.pi) }
        if (pinch != null) { pinch!!.maxN = max(pinch!!.maxN, touches.size); return }
        if (pullRaw != 0f) { pullRaw = 0f; spring(pullV, 0f, null) }
        val a = touches.values.toList()
        val mx = (a[0][0] + a[1][0]) / 2; val my = (a[0][1] + a[1][1]) / 2
        val pi = hitPage(mx, my).takeIf { it >= 0 } ?: pageIdx
        val fx = (mx - pageLeft()) / W; val fy = (my - pageTop(pi)) / pageH(pi)
        pinch = Pinch(max(1f, hypot(a[0][0] - a[1][0], a[0][1] - a[1][1])), SystemClock.uptimeMillis(), touches.size, mx, my, pi, fx, fy)
    }
    private fun movePinch() {
        val pc = pinch ?: return
        if (touches.size < 2 || pc.maxN > 2) return
        val a = touches.values.toList()
        val mx = (a[0][0] + a[1][0]) / 2; val my = (a[0][1] + a[1][1]) / 2
        val minS = 0.4f * ACT_W / W; val maxS = 3f * ACT_W / W
        val s = (hypot(a[0][0] - a[1][0], a[0][1] - a[1][1]) / pc.d0).coerceIn(minS, maxS)
        pc.tx = mx - pc.mx; pc.ty = my - pc.my
        if (abs(s - 1) > 0.03f || hypot(pc.tx, pc.ty) > dp(6f)) pc.moved = true
        pc.s = s; invalidate()
    }
    private fun endPinch(pc: Pinch, cancel: Boolean) {
        pinch = null
        if (pc.moved && pc.maxN == 2) {
            if (abs(pc.s - 1) > 0.03f) { zoom = (W * pc.s / ACT_W).coerceIn(0.4f, 3f); W = computeW(); layoutTops(); dropCaches() }
            // 손가락 사이 자리를 확대 뒤에도 같은 곳에
            val wantX = pc.mx + pc.tx; val wantY = pc.my + pc.ty
            val nowX = pageLeftFor(0f) + pc.fx * W; val nowY = tops[pc.pi] + pc.fy * pageH(pc.pi)
            if (W > width) sx = nowX - wantX
            sy = nowY - wantY; clampScroll(); updatePageIdx(); host.onTools()
            touches.clear()
        } else if (!cancel && !pc.moved && SystemClock.uptimeMillis() - pc.t0 < 300 && !penRecent()) {
            if (pc.maxN == 2) undo() else if (pc.maxN >= 3) redo()
        }
        invalidate()
    }
    private fun pageLeftFor(sxv: Float) = if (W < width) (width - W) / 2f else -sxv

    /* ---------- 꾹 누르기 (문제지: 정답 보기) ---------- */
    private fun startLP(id: Int, x: Float, y: Float) {
        if (doc.type != "quiz") return
        main.removeCallbacks(lpRun); lpId = id; lpX = x; lpY = y
        main.postDelayed(lpRun, 450)
    }
    private fun cancelLP() { main.removeCallbacks(lpRun); lpId = -1 }
    private fun fireLongPress() {
        val id = lpId; lpId = -1; if (id < 0) return
        val pi = hitPage(lpX, lpY); if (pi < 0) return
        val u = (lpX - pageLeft()) / W * QuizLayout.PW; val v = (lpY - pageTop(pi)) / W * QuizLayout.PW
        val hit = QuizLayout.pressHit(doc, pi, u, v) ?: return
        YY.haptic(15)
        // 누르는 동안 찍힌 점·스크롤은 없던 일로
        cur?.takeIf { it.id == id }?.let { c -> cur = null; if (c.liveBaked) dirty(c.pi) }
        if (scrolling?.id == id) scrolling = null
        cardDragId = id
        host.quizCard(pi, hit.idx, lpX, lpY, true)
        invalidate()
    }

    /** 톡: 보기 고르기, 번호 별표 */
    private fun quizTap(pi: Int, x: Float, y: Float): Boolean {
        val u = (x - pageLeft()) / W * QuizLayout.PW; val v = (y - pageTop(pi)) / W * QuizLayout.PW
        val hit = QuizLayout.tapHit(doc, pi, u, v) ?: return false
        if (hit.kind == "no") Quiz.toggleStar(doc, hit.idx)
        else { Quiz.select(doc, hit.idx, hit.n); animate(hit.idx, listOf("mark")) }
        dirty(pi); host.quizChanged(); return true
    }

    /** 펜을 띄운 채 움직임 (정답 카드에서 펜 아래 버튼 강조) */
    override fun onHoverEvent(ev: MotionEvent): Boolean {
        if (ev.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return super.onHoverEvent(ev)
        lastPen = SystemClock.uptimeMillis()
        when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> host.cardHover(ev.x, ev.y)
            MotionEvent.ACTION_HOVER_EXIT -> host.cardHoverExit()
        }
        return true
    }

    /* ---------- 글상자 ---------- */
    var editing: TextBox? = null
        set(v) { val old = field; field = v; textCache.clear(); if (old != null || v != null) dirtyAll() }

    /** 화면 좌표의 글상자 자리 */
    fun textRect(pi: Int, t: TextBox): RectF {
        val l = pageLeft(); val tp = pageTop(pi)
        return RectF(l + t.x * W, tp + t.y * W, l + (t.x + t.w) * W, tp + t.y * W + t.fs * W * 1.6f)
    }
    fun commitText(pi: Int, t: TextBox, isNew: Boolean) {
        val pg = doc.pages[pi]
        if (t.t.isBlank()) { pg.texts.remove(t) }
        else if (isNew) pushHist(pi, { pg.texts.remove(t) }, { if (t !in pg.texts) pg.texts.add(t) })
        editing = null; textCache.clear(); dirty(pi); queueSave()
    }

    /** 탭을 다시 열 때: 보던 확대와 위치로 */
    fun restore(z: Any, y: Float) {
        zoom = z
        post { relayout(null); sy = y; afterScroll() }
    }
    val canUndo get() = hist.isNotEmpty()
    val canRedo get() = redoList.isNotEmpty()

    fun release() {
        saveNow()
        stopGlide(); stopSpring(); main.removeCallbacksAndMessages(null)
        dropCaches(); pdfBm.values.forEach { it.recycle() }; pdfBm.clear(); pdfJobs.values.forEach { it.cancel() }
        predictor = null
    }
}
