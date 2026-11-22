package kr.yeobaek.note.quiz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.UnderlineSpan
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import kr.yeobaek.note.Y
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Pretendard 가변 글꼴: 굵기별 Typeface */
object Fonts {
    private val cache = HashMap<Int, Typeface>()
    fun w(weight: Int): Typeface = cache.getOrPut(weight) {
        runCatching {
            Typeface.Builder(Y.app.assets, "pretendard.ttf").setFontVariationSettings("'wght' $weight").setWeight(weight).build()
        }.getOrElse { Typeface.create(Typeface.DEFAULT, weight, false) }
    }
}

class WeightSpan(private val tf: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) { p.typeface = tf }
    override fun updateDrawState(p: TextPaint) { p.typeface = tf }
}

class SizeSpan(private val k: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) { p.textSize *= k }
    override fun updateDrawState(p: TextPaint) { p.textSize *= k }
}

/** CSS line-height처럼: 줄 높이가 lh보다 작으면 위아래로 반씩 늘려요 */
class MinLine(private val lh: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
        val cur = fm.descent - fm.ascent
        if (cur < lh) {
            val extra = lh - cur; val top = extra / 2
            fm.ascent -= top; fm.descent += extra - top
            fm.top = minOf(fm.top, fm.ascent); fm.bottom = maxOf(fm.bottom, fm.descent)
        }
    }
}

/** 수식 하나 (KaTeX 대신 JLaTeXMath) */
class MathSpan(private val d: JLatexMathDrawable?, private val fallback: String) : ReplacementSpan() {
    private val w = d?.intrinsicWidth ?: 0
    private val h = d?.intrinsicHeight ?: 0
    private val depth = d?.let { runCatching { it.icon().iconDepth }.getOrDefault(h / 4) } ?: 0
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (d == null) return paint.measureText(fallback).roundToInt()
        if (fm != null) {
            val pfm = paint.fontMetricsInt
            fm.ascent = minOf(pfm.ascent, -(h - depth) - 1); fm.top = fm.ascent
            fm.descent = maxOf(pfm.descent, depth + 1); fm.bottom = fm.descent
        }
        return w
    }
    override fun draw(c: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        if (d == null) { c.drawText(fallback, x, y.toFloat(), paint); return }
        c.save(); c.translate(x, (y - (h - depth)).toFloat())
        d.setBounds(0, 0, w, h); d.draw(c); c.restore()
    }
}

object Tex {
    private var inited = false
    private val mathCache = object : LinkedHashMap<String, JLatexMathDrawable?>(256, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, JLatexMathDrawable?>) = size > 600
    }
    private val SPLIT = Regex("(\\$\\$[\\s\\S]+?\\$\\$|\\$[^$\\n]+?\\$)")

    private fun math(src: String, px: Float, color: Int): JLatexMathDrawable? {
        if (!inited) { JLatexMathAndroid.init(Y.app); inited = true }
        val key = "$px|$color|$src"
        synchronized(mathCache) { if (mathCache.containsKey(key)) return mathCache[key] }
        val d = runCatching {
            JLatexMathDrawable.builder(src.replace("\\dfrac", "\\frac").replace("\\displaystyle", "\\displaystyle "))
                .textSize(px).color(color).padding(0).background(0).align(JLatexMathDrawable.ALIGN_LEFT).build()
        }.getOrNull()?.takeIf { it.intrinsicWidth > 0 }
        synchronized(mathCache) { mathCache[key] = d }
        return d
    }

    /**
     * 본문 표기: $..$ / $$..$$ 수식, **굵게**, __밑줄__, \n 줄바꿈
     * @param px 글자 크기(픽셀), 수식은 1.08배
     */
    fun build(src: String?, px: Float, color: Int, boldTf: Typeface = Fonts.w(700)): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        val s = src ?: ""
        var last = 0
        fun text(part: String) {
            var t = part
            // **굵게** 와 __밑줄__ 을 차례로
            val re = Regex("\\*\\*(.+?)\\*\\*|__([^_\\s][^_]*?)__")
            var i = 0
            for (m in re.findAll(t)) {
                out.append(t.substring(i, m.range.first))
                val st = out.length
                if (m.groupValues[1].isNotEmpty()) { out.append(m.groupValues[1]); out.setSpan(WeightSpan(boldTf), st, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                else { out.append(m.groupValues[2]); out.setSpan(UnderlineSpan(), st, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                i = m.range.last + 1
            }
            out.append(t.substring(i))
        }
        for (m in SPLIT.findAll(s)) {
            text(s.substring(last, m.range.first))
            val v = m.value
            val disp = v.startsWith("$$") && v.length > 4
            val body = if (disp) v.substring(2, v.length - 2) else v.substring(1, v.length - 1)
            val d = math(body, px * 1.08f * (if (disp) 1.1f else 1f), color)
            if (disp) {
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                val st = out.length
                out.append("￼")
                out.setSpan(MathSpan(d, body), st, st + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), st, st + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.append('\n')
            } else {
                val st = out.length
                out.append("￼")
                out.setSpan(MathSpan(d, body), st, st + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            last = m.range.last + 1
        }
        text(s.substring(last))
        // 수식 뒤 빈 줄 정리
        while (out.isNotEmpty() && out.last() == '\n') out.delete(out.length - 1, out.length)
        return out
    }

    fun appendLabel(sb: SpannableStringBuilder, t: String, color: Int, k: Float = .92f) {
        val st = sb.length; sb.append(t)
        sb.setSpan(ForegroundColorSpan(color), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(SizeSpan(k), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun layout(text: CharSequence, width: Int, px: Float, color: Int, weight: Int = 400, lineH: Float = 1.75f,
               align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, justify: Boolean = false, letter: Float = 0f): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = px; this.color = color; typeface = Fonts.w(weight); letterSpacing = letter
        }
        val sb = if (text is SpannableStringBuilder) text else SpannableStringBuilder(text)
        if (lineH > 0) sb.setSpan(MinLine(ceil(px * lineH).toInt()), 0, sb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        val b = StaticLayout.Builder.obtain(sb, 0, sb.length, tp, max(1, width))
            .setAlignment(align).setIncludePad(false).setLineSpacing(0f, 1f)
        if (Build.VERSION.SDK_INT >= 33) b.setLineBreakConfig(LineBreakConfig.Builder().setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build())
        if (justify && Build.VERSION.SDK_INT >= 26) b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return b.build()
    }
}
