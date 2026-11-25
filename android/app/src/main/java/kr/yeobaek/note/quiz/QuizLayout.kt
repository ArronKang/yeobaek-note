package kr.yeobaek.note.quiz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.util.Base64
import androidx.core.graphics.PathParser
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Problem
import kr.yeobaek.note.data.QuizSpec
import kr.yeobaek.note.ink.Paper
import kr.yeobaek.note.ink.PaperColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 문제지 배치: 560 × 792 고정 페이지(웹과 같은 단위)에 문항을 놓고, 기기 너비에 맞춰 통째로 확대해요.
 * 안에서는 정밀도를 위해 4배(L)로 배치해요.
 */
object QuizLayout {
    const val PW = 560f
    val PH = (560 * 1.4142f).roundToInt().toFloat()   // 792
    const val PAD_T = 44f; const val PAD_B = 40f; const val PAD_X = 44f
    const val CW = PW - PAD_X * 2                          // 472
    const val L = 4f
    const val LV = 3
    val CIRC = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧")

    fun workH(p: Problem, lv: Int) = if (lv >= 2) max(14f, (p.space * 0.45f).roundToInt().toFloat()) else p.space
    fun gapOf(lv: Int) = if (lv >= 2) 16f else 26f

    fun choiceCols(p: Problem, lv: Int): Int {
        fun vis(m: String) = m.replace(Regex("\\\\(d?frac|left|right|displaystyle|,|;|!|quad)"), "").replace(Regex("\\\\[a-zA-Z]+"), "x").replace(Regex("[{}^_\\s]"), "")
        val lens = p.choices.map { c ->
            if (lv >= 3) Regex("\\$([^$]*)\\$").replace(c) { vis(it.groupValues[1]) }.length
            else Regex("\\$[^$]*\\$").replace(c, "xxx").length
        }
        val m = lens.maxOrNull() ?: 0
        return if (m <= 6) 5 else if (m <= 16) 3 else 1
    }

    /* ---------- 그림 ---------- */
    private val imgCache = HashMap<String, Bitmap?>()
    fun bitmap(src: String): Bitmap? = synchronized(imgCache) {
        imgCache.getOrPut(src.hashCode().toString() + src.length) {
            runCatching {
                val b64 = src.substringAfter("base64,", "")
                if (b64.isEmpty() || src.startsWith("data:image/svg")) null
                else Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }.getOrNull()
        }
    }

    /* ---------- 블록 ---------- */
    class Choice(val n: Int, val x: Float, val y: Float, val w: Float, val h: Float, val text: StaticLayout)

    /** 문항 하나 (좌표는 블록 왼쪽 위 기준, 단위 = 페이지 단위 × L) */
    class Block(val idx: Int, val p: Problem, val width: Float) {
        var h = 0f
        var noL: StaticLayout? = null; var noW = 0f
        var qL: StaticLayout? = null; var qX = 0f; var qY = 0f
        var headH = 0f
        var imgY = 0f; var imgX = 0f
        var choices = ArrayList<Choice>(); var chTop = 0f; var chH = 0f
        var workY = 0f; var workHt = 0f
        var ansX = 0f; var ansY = 0f        // 답 줄 왼쪽 아래
        var ansLabel: StaticLayout? = null
        // 지문
        var phL: StaticLayout? = null; var ptL: StaticLayout? = null; var pPadY = 0f
    }

    class Head(val sub: StaticLayout?, val title: StaticLayout, val meta: StaticLayout, val h: Float)

    private fun lp(u: Float) = u * L

    fun head(spec: QuizSpec, c: PaperColors, width: Float = CW): Head {
        val w = lp(width).toInt()
        val sub = if (spec.subject.isNotBlank()) Tex.layout(kr.yeobaek.note.Y.clean(spec.subject), w, lp(8.5f), c.label, 600, 0f, letter = .08f) else null
        val title = Tex.layout(kr.yeobaek.note.Y.clean(spec.title), w, lp(26f), c.ink, 700, 1.15f, letter = -.02f)
        val n = spec.problems.count { !it.isPassage }
        val meta = Tex.layout("${n}문항", w, lp(9f), c.label, 500, 0f)
        val h = (sub?.height ?: 0) + lp(4f) + title.height + lp(8f) + meta.height + lp(12f) + lp(1.4f)
        return Head(sub, title, meta, h)
    }

    fun block(spec: QuizSpec, idx: Int, lv: Int, c: PaperColors, width: Float = CW): Block {
        val p = spec.problems[idx]
        val b = Block(idx, p, width)
        val W = lp(width)
        if (p.isPassage) {
            val inner = (W - lp(28f)).toInt()
            var y = lp(12f)
            if (p.q.isNotBlank()) { b.phL = Tex.layout(Tex.build(p.q, lp(10.5f), c.ink), inner, lp(10.5f), c.ink, 600, 1.5f); y += b.phL!!.height + lp(6f) }
            if (p.text.isNotBlank()) { b.ptL = Tex.layout(Tex.build(p.text, lp(10.5f), c.ink), inner, lp(10.5f), c.ink, 400, 1.85f, justify = true); y += b.ptL!!.height }
            p.img?.let { b.imgY = y + lp(8f); y = b.imgY + lp(it.h) }
            b.h = y + lp(12f)
            return b
        }
        // 머리: 번호 + 문제
        b.noL = Tex.layout(p.no.toString(), lp(40f).toInt(), lp(16f), c.ink, 700, 1.05f, Layout.Alignment.ALIGN_CENTER)
        b.noW = max(lp(22f), b.noL!!.getLineWidth(0))
        val q = Tex.build(p.q, lp(11f), c.ink)
        if (p.points > 0) Tex.appendLabel(q, " [" + fmtPts(p.points) + "점]", c.label)
        if (p.orig > 0) Tex.appendLabel(q, " (" + p.orig + "번)", c.label)
        b.qX = b.noW + lp(10f); b.qY = lp(1.5f)
        b.qL = Tex.layout(q, (W - b.qX).toInt(), lp(11f), c.ink, 400, 1.75f)
        b.headH = max(lp(16f * 1.05f), b.qY + b.qL!!.height)
        var y = b.headH
        p.img?.let { b.imgX = lp(32f); b.imgY = y + lp(8f); y = b.imgY + lp(it.h) }
        if (p.type == "choice" && p.choices.isNotEmpty()) {
            val cols = choiceCols(p, lv)
            val left = lp(32f); val gw = W - left
            val colW = (gw - lp(8f) * (cols - 1)) / cols
            b.chTop = y + lp(10f)
            var rowY = b.chTop
            p.choices.chunked(cols).forEachIndexed { r, row ->
                val items = row.mapIndexed { ci, t ->
                    val n = r * cols + ci + 1
                    val tw = (colW - lp(3f + 6f + 17f + 5f)).toInt()
                    val tl = Tex.layout(Tex.build(t, lp(11f), c.ink), tw, lp(11f), c.ink, 400, 1.5f)
                    Choice(n, left + ci * (colW + lp(8f)), rowY, colW, max(lp(24f), max(lp(17f), tl.height.toFloat()) + lp(6f)), tl)
                }
                val rh = items.maxOf { it.h }
                items.forEach { b.choices.add(Choice(it.n, it.x, it.y, it.w, rh, it.text)) }
                rowY += rh + lp(4f)
            }
            b.chH = rowY - lp(4f) - b.chTop
            y = b.chTop + b.chH
        }
        b.workY = y; b.workHt = lp(workH(p, lv)); y += b.workHt
        if (p.type == "short") {
            b.ansLabel = Tex.layout("답", lp(20f).toInt(), lp(10f), c.label, 600, 0f)
            b.ansX = W - lp(120f); b.ansY = y
        }
        b.h = y
        return b
    }

    private fun fmtPts(d: Double) = if (d == Math.floor(d)) d.toInt().toString() else d.toString()

    /* ---------- 페이지 나누기 (한 번 계산해서 저장) ---------- */
    fun paginate(spec: QuizSpec, lv: Int = LV): List<Pair<List<Int>, Boolean>> {
        val c = Paper.c()
        val headH = head(spec, c).h / L + 22f
        val hs = spec.problems.indices.map { block(spec, it, lv, c).h / L }
        val avail = PH - PAD_T - PAD_B - 28f; val gap = gapOf(lv)
        val pages = ArrayList<Pair<List<Int>, Boolean>>()
        var cur = ArrayList<Int>(); var head = true; var used = headH
        hs.forEachIndexed { i, bh ->
            if (cur.isNotEmpty() && used + bh > avail) { pages.add(cur to head); cur = ArrayList(); head = false; used = 0f }
            cur.add(i); used += bh + gap
        }
        pages.add(cur to head)
        return pages
    }

    /* ---------- 한 페이지 ---------- */
    class QPage(val head: Head?, val blocks: List<Pair<Block, Float>>, val colTop: Float, val k: Float, val foot: StaticLayout, val pageNo: Int)

    private val pageCache = HashMap<String, QPage>()
    fun clearCache() = synchronized(pageCache) { pageCache.clear() }

    fun page(doc: Doc, pi: Int): QPage {
        val c = Paper.c()
        val spec = doc.quiz!!; val pg = doc.pages[pi]; val lv = doc.lv ?: 1
        val key = "${doc.id}|$pi|${doc.pages.size}|${c.dark}|${pg.qi}|$lv"
        synchronized(pageCache) { pageCache[key]?.let { return it } }
        val hd = if (pg.head == true) head(spec, c) else null
        val colTop = lp(PAD_T) + (hd?.let { it.h + lp(22f) } ?: 0f)
        val foot = Tex.layout("${pi + 1} / ${doc.pages.size}", lp(CW).toInt(), lp(8f), c.label, 400, 0f, Layout.Alignment.ALIGN_CENTER)
        val room = lp(PH - PAD_B) - foot.height - lp(6f) - colTop
        val gap = lp(gapOf(lv))
        fun build(width: Float): List<Pair<Block, Float>> {
            var y = 0f
            return (pg.qi ?: emptyList()).filter { it in spec.problems.indices }.map { i -> val b = block(spec, i, lv, c, width); val r = b to y; y += b.h + gap; r }
        }
        var blocks = build(CW)
        val need = blocks.lastOrNull()?.let { it.second + it.first.h } ?: 0f
        var k = 1f
        // 안전장치: 넘치면 문항 묶음을 살짝 줄여서 한 장에
        if (need > room + 1 && room > 0) { k = max(0.6f, room / need); blocks = build(CW / k) }
        val qp = QPage(hd, blocks, colTop, k, foot, pi)
        synchronized(pageCache) { pageCache[key] = qp }
        return qp
    }

    /* ---------- 그리기 ---------- */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val invert = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        -0.88f, 0f, 0f, 0f, 255f * 0.94f, 0f, -0.88f, 0f, 0f, 255f * 0.94f, 0f, 0f, -0.88f, 0f, 255f * 0.94f, 0f, 0f, 0f, 1f, 0f)))
    private val markO = PathParser.createPathFromPathData("M21 4.5c-9 0-16.5 6.5-16.5 15.8 0 9.4 7.3 15.4 15.9 15.2 9.2-.2 15.6-6.8 15.1-15.6C35.1 11 28.6 4.3 18.8 5.6")
    private val markX = PathParser.createPathFromPathData("M9 33C16 24 24 14 33 5")
    /** 애매: 손으로 그은 세모 */
    private val markT = PathParser.createPathFromPathData("M20.5 6c4.6 8.6 9.3 17.4 14 26.2-9.8.4-19.6.6-29.5.8C9.6 24 15 15 20.5 6")
    private val swoosh = PathParser.createPathFromPathData("M4 12c22-5 52-7 112-4")
    private val starP = PathParser.createPathFromPathData("M12 4l2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8L9.6 9z")

    /** 애니메이션 진행 (0..1): 채점 표시 긋기, 정답 도장 */
    class Anim(val idx: Int, val kind: String, val t0: Long)

    /**
     * 페이지 단위 좌표계(560 너비)가 아니라 L배 좌표계로 그려요. 호출하는 쪽이 canvas.scale(W / (PW·L)) 해 둬요.
     * @param peek 정답 카드가 열린 문항
     */
    fun draw(cv: Canvas, doc: Doc, pi: Int, peek: Int = -1, anims: List<Anim> = emptyList(), now: Long = 0L, thumb: Boolean = false) {
        val c = Paper.c(); val qp = page(doc, pi); val qs = doc.qs!!
        cv.save()
        cv.translate(lp(PAD_X), lp(PAD_T))
        qp.head?.let { hd ->
            var y = 0f
            hd.sub?.let { it.draw(cv); y += it.height }
            y += lp(4f); cv.save(); cv.translate(0f, y); hd.title.draw(cv); cv.restore(); y += hd.title.height + lp(8f)
            cv.save(); cv.translate(0f, y); hd.meta.draw(cv); cv.restore(); y += hd.meta.height + lp(12f)
            paint.reset(); paint.color = c.ink; cv.drawRect(0f, y, lp(CW), y + lp(1.4f), paint)
            if (!thumb) drawScore(cv, doc, c, anims, now)
        }
        cv.restore()
        // 문항
        cv.save()
        cv.translate(lp(PAD_X), qp.colTop)
        if (qp.k != 1f) cv.scale(qp.k, qp.k)
        for ((b, y) in qp.blocks) {
            cv.save(); cv.translate(0f, y)
            val dim = qs.focus && qs.grade["${b.idx}"] == "o" && qs.star["${b.idx}"] == null
            if (dim) cv.saveLayerAlpha(-lp(40f), -lp(20f), b.width * L + lp(40f), b.h + lp(20f), (255 * .18f).toInt())
            drawBlock(cv, doc, b, c, b.idx == peek, anims.filter { it.idx == b.idx }, now)
            if (dim) cv.restore()
            cv.restore()
        }
        cv.restore()
        // 쪽 번호
        if (!thumb) { cv.save(); cv.translate(lp(PAD_X), lp(PH - PAD_B) - qp.foot.height); qp.foot.draw(cv); cv.restore() }
    }

    private fun prog(a: Anim?, now: Long, ms: Float): Float = if (a == null) 1f else ((now - a.t0) / ms).coerceIn(0f, 1f)
    private fun easeOut(t: Float): Float { val u = 1 - t; return 1 - u * u * u * u }
    private fun spring(t: Float): Float { if (t >= 1f) return 1f; val e = Math.exp(-6.0 * t).toFloat(); return 1 - e * Math.cos(9.0 * t).toFloat() }

    private fun drawScore(cv: Canvas, doc: Doc, c: PaperColors, anims: List<Anim>, now: Long) {
        val qs = doc.qs!!
        val sc = Quiz.score(doc)
        if (sc.graded == 0 || !qs.scored) return
        val n = Tex.layout("${sc.right}", lp(60f).toInt(), lp(28f), c.red, 700, 0f)
        val d = Tex.layout("/${sc.total}", lp(60f).toInt(), lp(14f), c.red, 700, 0f)
        val nw = n.getLineWidth(0); val dw = d.getLineWidth(0)
        val x0 = lp(CW) - nw - dw; val y0 = -lp(6f)
        val a = anims.find { it.kind == "score" }; val t = prog(a, now, 550f)
        cv.save(); cv.translate(x0, y0)
        val s = if (a != null) 0.6f + 0.4f * spring(prog(a, now, 500f)) else 1f
        cv.scale(s, s, (nw + dw) / 2, n.height / 2f)
        n.draw(cv); cv.save(); cv.translate(nw, n.getLineBaseline(0) - d.getLineBaseline(0).toFloat()); d.draw(cv); cv.restore()
        // 손으로 그은 밑줄
        val w = (nw + dw) * 1.2f
        cv.translate(-(nw + dw) * .1f, n.height - lp(2f))
        cv.scale(w / 120f, lp(14f) / 20f)
        stroke(cv, swoosh, c.red, 2.6f * 120f / w * L, easeOut(t))
        cv.restore()
    }

    private val pm = android.graphics.PathMeasure()
    private fun stroke(cv: Canvas, path: Path, color: Int, width: Float, t: Float) {
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.color = color
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        if (t >= 1f) { cv.drawPath(path, paint); return }
        pm.setPath(path, false); val len = pm.length
        paint.pathEffect = DashPathEffect(floatArrayOf(len, len), len * (1 - t))
        cv.drawPath(path, paint); paint.pathEffect = null
    }

    private fun drawBlock(cv: Canvas, doc: Doc, b: Block, c: PaperColors, peek: Boolean, anims: List<Anim>, now: Long) {
        val p = b.p; val qs = doc.qs!!; val key = "${b.idx}"
        if (p.isPassage) {
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = lp(.8f); paint.color = c.line
            val r = lp(6f); cv.drawRoundRect(RectF(lp(.4f), lp(.4f), b.width * L - lp(.4f), b.h - lp(.4f)), r, r, paint)
            var y = lp(12f)
            b.phL?.let { cv.save(); cv.translate(lp(14f), y); it.draw(cv); cv.restore(); y += it.height + lp(6f) }
            b.ptL?.let { cv.save(); cv.translate(lp(14f), y); it.draw(cv); cv.restore() }
            p.img?.let { m -> drawImg(cv, m.src, (b.width * L - lp(m.w)) / 2, b.imgY, lp(m.w), lp(m.h), c) }
            return
        }
        val sel = qs.sel[key]; val rev = qs.rev[key]?.get("ans") == 1 || qs.show; val g = qs.grade[key]
        if (peek) { paint.reset(); paint.isAntiAlias = true; paint.color = if (c.dark) 0x14FFFFFF else 0x0A212529; val r = lp(8f); cv.drawRoundRect(RectF(-lp(4f), -lp(2f), b.width * L + lp(4f), b.headH + lp(2f)), r, r, paint) }
        // 번호
        val nl = b.noL!!
        cv.save(); cv.translate((b.noW - lp(40f)) / 2f, 0f); nl.draw(cv); cv.restore()
        val ncx = b.noW / 2; val ncy = nl.height / 2f
        // 채점 표시 (손으로 그은 동그라미 / 빗금)
        if (g != null) {
            val a = anims.find { it.kind == "mark" }
            cv.save(); cv.translate(ncx - lp(20f), ncy - lp(20f) * 1.04f); cv.scale(L, L)
            stroke(cv, when (g) { "o" -> markO; "?" -> markT; else -> markX }, c.red, 2.6f, easeOut(prog(a, now, 550f)))
            cv.restore()
        }
        // 별표
        if (qs.star[key] != null) {
            cv.save(); cv.translate(b.noW - lp(2f), -lp(6f)); val s = lp(11f) / 24f; cv.scale(s, s)
            paint.reset(); paint.isAntiAlias = true; paint.color = c.star; paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = 1.75f; paint.strokeJoin = Paint.Join.ROUND
            cv.drawPath(starP, paint); cv.restore()
        }
        cv.save(); cv.translate(b.qX, b.qY); b.qL!!.draw(cv); cv.restore()
        p.img?.let { m -> drawImg(cv, m.src, b.imgX, b.imgY, lp(m.w), lp(m.h), c) }
        // 보기
        for (ch in b.choices) {
            val on = sel == ch.n; val correct = rev && ch.n.toString() == p.answer
            val cx = ch.x + lp(3f) + lp(8.5f); val cy = ch.y + ch.h / 2
            if (on) { paint.reset(); paint.isAntiAlias = true; paint.color = c.ink; cv.drawCircle(cx, cy, lp(8.5f) * 1.1f, paint) }
            val circ = CIRC.getOrElse(ch.n - 1) { "${ch.n}" }
            paint.reset(); paint.isAntiAlias = true; paint.typeface = Fonts.w(400); paint.textSize = lp(13f) * (if (on) 1.1f else 1f)
            paint.color = if (on) c.paper else c.ink; paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics; cv.drawText(circ, cx, cy - (fm.ascent + fm.descent) / 2, paint)
            if (correct) {
                val a = anims.find { it.kind == "stamp" }
                val s = if (a != null) { val t = prog(a, now, 420f); 1.35f - 0.35f * spring(t) } else 1f
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = lp(1.6f); paint.color = c.red
                if (a != null) paint.alpha = (255 * min(1f, prog(a, now, 160f))).toInt()
                cv.drawCircle(cx, cy, (lp(8.5f) + lp(4f) - lp(.8f)) * s, paint)
            }
            val tx = ch.x + lp(3f + 17f + 5f); val ty = cy - ch.text.height / 2f
            cv.save(); cv.translate(tx, ty); ch.text.draw(cv); cv.restore()
            if (on && g == "x") {
                paint.reset(); paint.color = c.red; paint.strokeWidth = lp(1.4f)
                for (li in 0 until ch.text.lineCount) {
                    val ly = ty + (ch.text.getLineTop(li) + ch.text.getLineBottom(li)) / 2f
                    cv.drawLine(tx, ly, tx + ch.text.getLineWidth(li), ly, paint)
                }
            }
        }
        // 답 줄
        b.ansLabel?.let { lab ->
            val lineW = lp(120f); val bx = b.width * L - lineW; val by = b.ansY
            cv.save(); cv.translate(bx - lp(8f) - lab.getLineWidth(0), by - lab.height); lab.draw(cv); cv.restore()
            paint.reset(); paint.color = c.line; paint.strokeWidth = lp(.9f); cv.drawLine(bx, by - lp(.45f), bx + lineW, by - lp(.45f), paint)
            if (rev) {
                // 답이 길면: 칸을 왼쪽으로 넓히고 글자를 줄이고, 그래도 넘치면 잘라서 "…" (문항 글자 위로 넘쳐 나오지 않게)
                val maxH = lp(40f)
                var fs = 16f; var w = lineW; var src = p.answer
                fun lay() = Tex.layout(Tex.build(src, lp(fs), c.red, Fonts.w(700)), w.toInt(), lp(fs), c.red, 700, 0f, if (w == lineW) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_OPPOSITE)
                var al = lay()
                if (al.height > maxH) { w = b.width * L * .9f; al = lay() }
                while (al.height > maxH && fs > 9f) { fs -= 1f; al = lay() }
                while (al.height > maxH && src.length > 6) { src = src.take((src.length * .8f).toInt()).trimEnd() + "…"; al = lay() }
                cv.save(); cv.translate(bx + lineW - w, by - lp(2f) - al.height); al.draw(cv); cv.restore()
            }
        }
    }

    private fun drawImg(cv: Canvas, src: String, x: Float, y: Float, w: Float, h: Float, c: PaperColors) {
        val bm = bitmap(src) ?: return
        paint.reset(); paint.isFilterBitmap = true; paint.isAntiAlias = true
        if (c.dark) paint.colorFilter = invert
        cv.drawBitmap(bm, null, RectF(x, y, x + w, y + h), paint)
    }

    /* ---------- 누를 자리 찾기 (페이지 단위 좌표) ---------- */
    class Hit(val idx: Int, val kind: String, val n: Int, val rect: RectF)

    private fun toPage(qp: QPage, y: Float, r: RectF): RectF {
        val k = qp.k
        return RectF((lp(PAD_X) + r.left * k) / L, (qp.colTop + (y + r.top) * k) / L, (lp(PAD_X) + r.right * k) / L, (qp.colTop + (y + r.bottom) * k) / L)
    }

    /** 톡: 보기(여유 3), 번호(여유 6) */
    fun tapHit(doc: Doc, pi: Int, ux: Float, uy: Float): Hit? {
        val qp = page(doc, pi)
        for ((b, y) in qp.blocks) {
            if (b.p.isPassage) continue
            for (ch in b.choices) {
                val r = toPage(qp, y, RectF(ch.x, ch.y, ch.x + ch.w, ch.y + ch.h))
                if (ux >= r.left - 3 && ux <= r.right + 3 && uy >= r.top - 3 && uy <= r.bottom + 3) return Hit(b.idx, "choice", ch.n, r)
            }
            val nr = toPage(qp, y, RectF(0f, 0f, b.noW, b.noL!!.height.toFloat()))
            if (ux >= nr.left - 6 && ux <= nr.right + 6 && uy >= nr.top - 6 && uy <= nr.bottom + 6) return Hit(b.idx, "no", 0, nr)
        }
        return null
    }

    /** 꾹 누르기: 문제 머리(여유 6), 보기 묶음(4), 주관식 답 줄(18) */
    fun pressHit(doc: Doc, pi: Int, ux: Float, uy: Float): Hit? {
        val qp = page(doc, pi)
        fun inside(r: RectF, pad: Float) = ux >= r.left - pad && ux <= r.right + pad && uy >= r.top - pad && uy <= r.bottom + pad
        for ((b, y) in qp.blocks) {
            if (b.p.isPassage) continue
            val head = toPage(qp, y, RectF(0f, 0f, b.width * L, b.headH))
            if (inside(head, 6f)) return Hit(b.idx, "head", 0, head)
            if (b.choices.isNotEmpty()) {
                val cr = toPage(qp, y, RectF(lp(32f), b.chTop, b.width * L, b.chTop + b.chH))
                if (inside(cr, 4f)) return Hit(b.idx, "choices", 0, cr)
            }
            if (b.p.type == "short") {
                val ar = toPage(qp, y, RectF(b.width * L - lp(150f), b.ansY - lp(20f), b.width * L, b.ansY))
                if (inside(ar, 18f)) return Hit(b.idx, "ans", 0, ar)
            }
        }
        return null
    }

    /** 문항 블록의 페이지 안 위치 (정답표에서 이동할 때) */
    fun blockTop(doc: Doc, pi: Int, idx: Int): Float? {
        val qp = page(doc, pi)
        val (b, y) = qp.blocks.find { it.first.idx == idx } ?: return null
        return toPage(qp, y, RectF(0f, 0f, b.width * L, b.h)).top
    }
}
