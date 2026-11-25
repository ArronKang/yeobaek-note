package kr.yeobaek.note.quiz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.PathParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Problem
import kr.yeobaek.note.data.QuizSpec
import kr.yeobaek.note.data.Stroke
import kr.yeobaek.note.ink.PaperColors
import kotlin.math.max
import kotlin.math.min

/**
 * 문제지 새 구조 (.ybq 2): 원본 PDF는 그대로, 그 위에 보이지 않는 답안 정보 층.
 * quiz.json: { ybq: 2, title, subject, problems: [{ no, page, num: [x0,y0,x1,y1], type: choice|short, choices: [[…]…], answer, solution, area? }] }
 * 좌표는 쪽 폭·높이에 대한 비율(0~1). 펜은 늘 필기, 번호 칸을 꾹 = 보기 위 필기를 읽어 채점 + 정답 카드, 보기 칸을 손가락으로 톡 = 고르기.
 */
class OvProblem(val no: Int, val page: Int, val num: RectF, val type: String, val choices: List<RectF>, val answer: String, val solution: String, val area: RectF?)
class QOverlay(val title: String, val subject: String, val problems: List<OvProblem>, val raw: JsonObject) {
    fun onPage(pi: Int) = problems.withIndex().filter { it.value.page == pi }
}

object QOv {
    private fun rect(e: kotlinx.serialization.json.JsonElement?): RectF? {
        val a = (e as? JsonArray) ?: return null
        if (a.size < 4) return null
        val v = a.map { it.jsonPrimitive.floatOrNull ?: 0f }
        return RectF(min(v[0], v[2]), min(v[1], v[3]), max(v[0], v[2]), max(v[1], v[3]))
    }

    fun parse(text: String): QOverlay? = runCatching {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        val ps = o["problems"]!!.jsonArray.mapIndexed { i, e ->
            val p = e.jsonObject
            OvProblem(p["no"]?.jsonPrimitive?.intOrNull ?: (i + 1), p["page"]?.jsonPrimitive?.intOrNull ?: 0, rect(p["num"]) ?: RectF(),
                p["type"]?.jsonPrimitive?.contentOrNull ?: "choice", (p["choices"] as? JsonArray)?.mapNotNull { rect(it) } ?: emptyList(),
                p["answer"]?.jsonPrimitive?.contentOrNull ?: "", p["solution"]?.jsonPrimitive?.contentOrNull ?: "", rect(p["area"]))
        }
        QOverlay(o["title"]?.jsonPrimitive?.contentOrNull ?: "", o["subject"]?.jsonPrimitive?.contentOrNull ?: "", ps, o)
    }.getOrNull()

    /** 정답 카드·채점이 그대로 쓰는 문제 목록 (보기 글 대신 ①②③…) */
    fun spec(ov: QOverlay): QuizSpec = QuizSpec(ov.title, ov.subject, ov.problems.mapIndexed { i, p ->
        Problem(p.type, p.no, "p$i", "", "", p.choices.indices.map { QuizLayout.CIRC.getOrElse(it) { "${it + 1}" } }, p.answer, p.solution, 0.0, 0f, 0, null)
    }, ov.raw)

    private fun RectF.grow(k: Float, minPad: Float = 0f): RectF { val dx = max(width() * k, minPad); val dy = max(height() * k, minPad); return RectF(left - dx, top - dy, right + dx, bottom + dy) }

    /** 꾹: 번호 칸(조금 넉넉히) */
    fun pressHit(ov: QOverlay, pi: Int, fx: Float, fy: Float): Int? =
        ov.onPage(pi).firstOrNull { (_, p) -> p.num.grow(.35f, .012f).contains(fx, fy) }?.index

    /** 톡: 보기 칸 */
    fun tapHit(ov: QOverlay, pi: Int, fx: Float, fy: Float): Pair<Int, Int>? {
        for ((i, p) in ov.onPage(pi)) p.choices.forEachIndexed { n, r -> if (r.grow(.25f, .006f).contains(fx, fy)) return i to (n + 1) }
        return null
    }

    /**
     * 보기 위 필기 읽기: 기호를 감싸는 동그라미·덮어 칠함·✓ = 고름, 가로지르는 긴 사선 = 지운 보기, 살짝 스침 = 무시.
     * 획 좌표는 쪽 폭 기준(x, y 모두) → 비율로 바꿔 비교. @return 고른 보기 번호들
     */
    fun readSel(p: OvProblem, strokes: List<Stroke>, ratio: Float): List<Int> {
        val out = ArrayList<Int>()
        p.choices.forEachIndexed { n, r0 ->
            val r = r0.grow(.6f, .008f)
            var score = 0f
            for (st in strokes) {
                if (st.t == "hl") continue
                var x0 = 9f; var y0 = 9f; var x1 = -9f; var y1 = -9f; var inside = 0
                for (pt in st.p) {
                    val fx = pt[0]; val fy = pt[1] / ratio
                    x0 = min(x0, fx); y0 = min(y0, fy); x1 = max(x1, fx); y1 = max(y1, fy)
                    if (r.contains(fx, fy)) inside++
                }
                if (inside == 0) continue
                val bw = x1 - x0; val bh = y1 - y0
                val frac = inside.toFloat() / st.p.size
                val encloses = x0 <= r0.centerX() && x1 >= r0.centerX() && y0 <= r0.centerY() && y1 >= r0.centerY()
                val sizeOk = bw < r0.width() * 4.5f && bh < r0.height() * 4.5f
                // 긴 사선: 칸보다 훨씬 길고 거의 직선 → 지운 보기
                val chord = kotlin.math.hypot(st.p.last()[0] - st.p.first()[0], (st.p.last()[1] - st.p.first()[1]) / ratio)
                val strike = chord > r0.width() * 2.5f && chord > max(bw, bh) * .9f
                if (strike) { score -= 2f; continue }
                if (encloses && sizeOk) score += 2f
                else if (frac > .5f && st.p.size >= 4) score += 1.2f
                else if (inside >= 3) score += .3f
            }
            if (score >= 1.2f) out.add(n + 1)
        }
        return out
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markO = PathParser.createPathFromPathData("M21 4.5c-9 0-16.5 6.5-16.5 15.8 0 9.4 7.3 15.4 15.9 15.2 9.2-.2 15.6-6.8 15.1-15.6C35.1 11 28.6 4.3 18.8 5.6")
    private val markX = PathParser.createPathFromPathData("M9 33C16 24 24 14 33 5")
    private val markT = PathParser.createPathFromPathData("M20.5 6c4.6 8.6 9.3 17.4 14 26.2-9.8.4-19.6.6-29.5.8C9.6 24 15 15 20.5 6")

    /** 쪽 위에 표시: 고른 보기 동그라미(펜 색), 정답 보기 빨간 동그라미(답을 본 뒤), 번호에 채점 표시, 오답만 진하게면 맞은 문제 흐리게 */
    fun draw(cv: Canvas, doc: Doc, ov: QOverlay, pi: Int, W: Float, H: Float, pc: PaperColors) {
        val qs = doc.qs ?: return
        // PDF 종이는 다크 모드에서도 밝게(살짝 어둡게만) 보이므로 표시는 늘 종이용 진한 색
        val c = object { val ink = 0xFF1C1C1E.toInt(); val red = 0xFFE5484D.toInt(); val dark = false }
        for ((i, p) in ov.onPage(pi)) {
            val k = "$i"
            val g = qs.grade[k]; val rev = qs.rev[k]?.get("ans") == 1 || qs.show
            if (qs.focus && g == "o" && qs.star[k] == null) {
                val a = p.area ?: RectF(0f, p.num.top - .005f, 1f, (p.choices.maxOfOrNull { it.bottom } ?: p.num.bottom) + .01f)
                paint.reset(); paint.color = if (c.dark) 0xB31D1E22.toInt() else 0xB3FFFFFF.toInt()
                cv.drawRect(a.left * W, a.top * H, a.right * W, a.bottom * H, paint)
            }
            qs.sel[k]?.let { n -> p.choices.getOrNull(n - 1)?.let { r ->
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = W * .0022f; paint.color = c.ink
                cv.drawOval(RectF(r.left * W, r.top * H, r.right * W, r.bottom * H).apply { inset(-W * .004f, -W * .004f) }, paint)
            } }
            if (rev && p.type == "choice") p.answer.toIntOrNull()?.let { n -> p.choices.getOrNull(n - 1)?.let { r ->
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = W * .0026f; paint.color = c.red
                cv.drawOval(RectF(r.left * W, r.top * H, r.right * W, r.bottom * H).apply { inset(-W * .007f, -W * .007f) }, paint)
            } }
            if (g != null) {
                val s = max(p.num.width() * W, p.num.height() * H) * 1.6f / 40f
                cv.save(); cv.translate(p.num.centerX() * W - 20f * s, p.num.centerY() * H - 20.8f * s); cv.scale(s, s)
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.color = c.red; paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                cv.drawPath(when (g) { "o" -> markO; "?" -> markT; else -> markX }, paint)
                cv.restore()
            }
            if (rev && p.type == "short" && p.answer.isNotBlank()) {
                // 단답 정답: 번호 오른쪽 위에 작게 (칸을 넘지 않게 줄여서)
                paint.reset(); paint.isAntiAlias = true; paint.color = c.red; paint.typeface = Fonts.w(700)
                var ts = p.num.height() * H * .9f; paint.textSize = ts
                val maxW = W * .4f; while (paint.measureText(p.answer) > maxW && ts > W * .012f) { ts *= .9f; paint.textSize = ts }
                val t = if (paint.measureText(p.answer) > maxW) p.answer.take((p.answer.length * maxW / paint.measureText(p.answer)).toInt().coerceAtLeast(1)) + "…" else p.answer
                cv.drawText(t, (p.num.right + .01f) * W, p.num.top * H - ts * .2f, paint)
            }
        }
    }
}
