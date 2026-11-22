package kr.yeobaek.note.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kr.yeobaek.note.quiz.Fonts
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** 페이지 양식: 줄·모눈·점은 기기 픽셀에 맞춰 선을 긋고, 플래너·레시피 저널은 양식을 그려요 (단위: 페이지 너비 560) */
object Templates {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** @param W 페이지 너비(픽셀) @param H 높이 @param px 화면 1px이 기기 픽셀 몇 개인지 */
    fun draw(cv: Canvas, tpl: String, W: Float, H: Float, px: Float, c: PaperColors) {
        val dp = W / 560f
        val lw = max(1f, px.roundToInt().toFloat())
        fun snap(v: Float) = v.roundToInt() + (if (lw.toInt() % 2 == 1) .5f else 0f)
        fun from(o: Float, st: Float) = o - floor(o / st) * st
        p.reset(); p.strokeWidth = lw; p.isAntiAlias = false
        when (tpl) {
            "lined" -> { p.color = if (c.dark) 0xFF41434A.toInt() else c.line; var y = from(44 * dp, 28 * dp); while (y < H) { val yy = snap(y - lw); cv.drawLine(0f, yy, W, yy, p); y += 28 * dp } }
            "grid" -> {
                p.color = c.line2
                var y = from(4 * dp, 20 * dp); while (y < H) { val yy = snap(y - lw); cv.drawLine(0f, yy, W, yy, p); y += 20 * dp }
                var x = from(2 * dp, 20 * dp); while (x < W) { val xx = snap(x - lw); cv.drawLine(xx, 0f, xx, H, p); x += 20 * dp }
            }
            "dot" -> {
                p.isAntiAlias = true; p.color = c.line; val r = max(1.2f, 1.4f * dp)
                var y = 20 * dp; while (y < H) { var x = 20 * dp; while (x < W) { cv.drawCircle(x, y, r, p); x += 20 * dp }; y += 20 * dp }
            }
            "planner" -> { cv.save(); cv.scale(dp, dp); planner(cv, H / dp, c); cv.restore() }
            "recipe" -> { cv.save(); cv.scale(dp, dp); recipe(cv, H / dp, c); cv.restore() }
        }
    }

    private fun text(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, weight: Int, ls: Float = 0f): Float {
        p.reset(); p.isAntiAlias = true; p.typeface = Fonts.w(weight); p.textSize = size; p.color = color; p.letterSpacing = ls
        cv.drawText(s, x, y, p); return p.measureText(s)
    }
    private fun label(cv: Canvas, en: String, ko: String, x: Float, y: Float, c: PaperColors): Float {
        val w = text(cv, en, x, y, 8f, c.label, 600, .06f)
        return w + 6f + text(cv, ko, x + w + 6f, y, 8f, c.label, 500, .02f)
    }
    private fun line(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, w: Float = .75f) {
        p.reset(); p.isAntiAlias = true; p.color = color; p.strokeWidth = w; cv.drawLine(x0, y0, x1, y1, p)
    }
    private fun box(cv: Canvas, r: RectF, c: PaperColors) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = .75f; p.color = c.line; cv.drawRoundRect(r, 4f, 4f, p)
    }
    private fun field(cv: Canvas, en: String, ko: String, x: Float, y: Float, w: Float, c: PaperColors) {
        val lw = label(cv, en, ko, x, y, c)
        line(cv, x + lw + 8f, y + 2f, x + w, y + 2f, c.line)
    }
    private fun head(cv: Canvas, title: String, link: String, c: PaperColors) {
        text(cv, "‹", 36f, 58f, 20f, c.label, 400)
        text(cv, title, 56f, 56f, 16f, c.ink, 600)
        p.typeface = Fonts.w(500); p.textSize = 9f
        val w = p.measureText(link); text(cv, link, 524f - w, 55f, 9f, c.label, 500)
        line(cv, 36f, 68f, 524f, 68f, c.line)
    }

    private fun planner(cv: Canvas, H: Float, c: PaperColors) {
        head(cv, "Weekly planner", "월간 목차", c)
        field(cv, "WEEK OF", "주 시작일", 36f, 92f, 210f, c)
        field(cv, "FOCUS", "이번 주 목표", 260f, 92f, 524f, c)
        val top = 108f; val notesH = 110f; val bottom = H - 36f - notesH - 14f
        val r = RectF(36f, top, 524f, bottom); box(cv, r, c)
        val days = listOf("MON" to "월", "TUE" to "화", "WED" to "수", "THU" to "목", "FRI" to "금", "SAT" to "토", "SUN" to "일")
        val rh = (bottom - top) / 7
        days.forEachIndexed { i, (en, ko) ->
            val y = top + i * rh
            if (i > 0) line(cv, 36f, y, 524f, y, c.line)
            text(cv, en, 44f, y + 16f, 8f, c.label, 600, .06f); text(cv, ko, 44f, y + 27f, 8f, c.label, 500)
        }
        line(cv, 88f, top, 88f, bottom, c.line)
        val ny = bottom + 14f
        label(cv, "NOTES", "메모", 36f, ny + 8f, c)
        box(cv, RectF(36f, ny + 14f, 524f, H - 36f), c)
    }

    private fun recipe(cv: Canvas, H: Float, c: PaperColors) {
        head(cv, "Recipe journal", "건강 & 식단 목차", c)
        val top = 84f; val gap = 20f; val bh = (H - 36f - top - gap) / 2
        for (k in 0..1) {
            val y0 = top + k * (bh + gap)
            field(cv, "RECIPE NAME", "레시피 이름", 36f, y0 + 14f, 300f, c)
            field(cv, "LINK TO SOURCE", "출처 링크", 36f, y0 + 40f, 300f, c)
            // 난이도·평점 등 칸
            val mx = 316f; val mw = 208f; val ch = 24f
            val meta = listOf("DIFFICULTY" to "난이도", "RATING (1-5)" to "평점", "PREP TIME" to "준비 시간", "COOK TIME" to "조리 시간", "SERVINGS" to "인분", "TOTAL TIME" to "총 시간")
            box(cv, RectF(mx, y0, mx + mw, y0 + ch * 3), c)
            line(cv, mx + mw / 2, y0, mx + mw / 2, y0 + ch * 3, c.line)
            for (r in 1..2) line(cv, mx, y0 + ch * r, mx + mw, y0 + ch * r, c.line)
            meta.forEachIndexed { i, (en, ko) ->
                val cx = mx + (i % 2) * mw / 2 + 6f; val cy = y0 + (i / 2) * ch + 10f
                val w = text(cv, en, cx, cy, 6.5f, c.label, 600, .06f); text(cv, ko, cx + w + 4f, cy, 6.5f, c.label, 500)
            }
            val by = y0 + ch * 3 + 10f; val bb = y0 + bh
            label(cv, "INGREDIENTS", "재료", 36f, by + 8f, c)
            box(cv, RectF(36f, by + 14f, 200f, bb), c)
            label(cv, "DIRECTIONS", "조리 순서", 214f, by + 8f, c)
            var ly = by + 14f + 20f
            while (ly < bb) { line(cv, 214f, ly, 524f, ly, c.line2, 1f); ly += 20f }
        }
    }
}
