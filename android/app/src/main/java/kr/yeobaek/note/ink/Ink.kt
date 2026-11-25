package kr.yeobaek.note.ink

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kr.yeobaek.note.data.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 획 그리기와 기하 (engine.js의 drawStroke, segWidth, 지우개, 올가미, 모양 보정) */
object Ink {
    private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    fun parse(c: String): Int = colorCache.getOrPut(c) { runCatching { Color.parseColor(if (c.startsWith("#")) c else "#$c") }.getOrDefault(Color.BLACK) }

    private val lightCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    /** 어두운 종이: 검정 펜은 흰색, 색 펜은 38% 밝게 (저장된 색은 그대로) */
    fun strokeColor(st: Stroke, dark: Boolean): Int {
        if (!dark) return parse(st.c)
        if (st.c.equals("#1A1A20", true)) return 0xFFF2F2F6.toInt()
        if (st.t == "hl") return parse(st.c)
        return lightCache.getOrPut(st.c) {
            val n = parse(st.c); fun mix(v: Int) = (v + (255 - v) * 0.38f).roundToInt()
            Color.rgb(mix(Color.red(n)), mix(Color.green(n)), mix(Color.blue(n)))
        }
    }

    private fun anc(P: List<FloatArray>, i: Int): FloatArray {
        val n = P.size
        if (i <= 0) return P[0]; if (i >= n) return P[n - 1]
        return floatArrayOf((P[i - 1][0] + P[i][0]) / 2, (P[i - 1][1] + P[i][1]) / 2)
    }

    fun segWidth(st: Stroke, k: Int, K: Float): Float {
        val P = st.p; val n = if (st.live) 1_000_000_000 else P.size; val c = P[k]; val pr = c.getOrElse(2) { .5f }; val base = st.s * K
        val a = P[max(0, k - 1)]; val b = P[min(P.size - 1, k + 1)]
        val dx = b[0] - a[0]; val dy = b[1] - a[1]; val len = hypot(dx, dy)
        return when (st.k) {
            "fine" -> max(0.6f, base * 0.8f)
            "pencil" -> {                           // 연필: 가늘고 누르는 힘에 따라 조금 굵어짐
                val taper = min(1f, min((k + 2) / 4f, (n - k + 1) / 4f))
                max(0.5f, base * (0.6f + pr * 0.7f) * (0.6f + 0.4f * taper))
            }
            "marker" -> max(0.8f, base)             // 마커: 늘 같은 굵기
            "fountain" -> {                         // 45° 납작 촉: 방향에 따라 굵기
                val ang = if (len > 0) atan2(dy, dx) else 0f
                val t = min(1f, min((k + 2) / 4f, (n - k + 1) / 4f))
                max(0.5f, base * (0.3f + 1.15f * abs(sin(ang - PI.toFloat() / 4))) * (0.75f + pr * 0.5f) * (0.5f + 0.5f * t))
            }
            "brush" -> {                            // 압력·속도에 크게 반응, 끝이 길게 빠짐
                val sp = len * K / max(1f, base * 3)
                val t = min(1f, min((k + 1) / 7f, (n - k) / 9f))
                max(0.4f, base * (0.25f + pr * 1.6f) * max(0.35f, min(1.25f, 1.3f - sp * 0.25f)) * (0.2f + 0.8f * t))
            }
            else -> {
                val taper = min(1f, min((k + 2) / 5f, (n - k + 1) / 5f))
                max(0.6f, base * (0.55f + pr * 0.9f) * (0.45f + 0.55f * taper))
            }
        }
    }

    // 저지연 층(그리기 스레드)과 화면(메인 스레드)이 동시에 그리므로 붓·경로는 스레드마다 따로
    private val tlPaint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val tlPath = ThreadLocal.withInitial { Path() }

    /**
     * @param K 페이지 너비(픽셀). 좌표 = 정규화 값 × K
     * @param from 이 점부터만 그림 (실시간 획)
     */
    fun draw(cv: Canvas, st: Stroke, K: Float, dark: Boolean, from: Int = 0, alphaMul: Float = 1f, to: Int = Int.MAX_VALUE) {
        val P = st.p; val n = P.size; if (n == 0) return
        val paint = tlPaint.get()!!; val path = tlPath.get()!!
        paint.reset(); paint.isAntiAlias = true
        paint.color = strokeColor(st, dark)
        paint.strokeJoin = Paint.Join.ROUND
        if (st.t == "hl") {
            paint.alpha = (255 * st.a * alphaMul).roundToInt().coerceIn(0, 255)
            if (!dark) paint.blendMode = BlendMode.MULTIPLY
            if (n == 1) { paint.style = Paint.Style.FILL; cv.drawCircle(P[0][0] * K, P[0][1] * K, max(0.7f, st.s * K / 2), paint); return }
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.BUTT; paint.strokeWidth = st.s * K
            path.reset(); path.moveTo(P[0][0] * K, P[0][1] * K)
            for (k in 0 until n) { val b = anc(P, k + 1); val c = P[k]; path.quadTo(c[0] * K, c[1] * K, b[0] * K, b[1] * K) }
            cv.drawPath(path, paint)
            return
        }
        // 투명도가 있는 펜·연필: 획 전체를 한 겹(레이어)에 불투명하게 그린 뒤 한 번에 투명도를 줌 (마디마다 겹쳐 진해지지 않게)
        val layered = st.a < 0.999f || st.k == "pencil"
        var saved = -1
        if (layered) {
            val bb = bbox(listOf(st)); val pad = st.s * K * 2 + 4
            val a = (255 * st.a * alphaMul * (if (st.k == "pencil") 0.92f else 1f)).roundToInt().coerceIn(0, 255)
            saved = cv.saveLayerAlpha(bb[0] * K - pad, bb[1] * K - pad, bb[2] * K + pad, bb[3] * K + pad, a)
        } else if (alphaMul < 1f) paint.alpha = (255 * alphaMul).roundToInt()
        if (n == 1) {
            if (from == 0 || layered) { paint.style = Paint.Style.FILL; cv.drawCircle(P[0][0] * K, P[0][1] * K, max(0.7f, st.s * K / 2), paint) }
            if (saved >= 0) { if (st.k == "pencil") grain(cv, paint, st, K); cv.restoreToCount(saved) }
            return
        }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        for (j in (if (layered) 0 else max(0, from)) until (if (layered) n else min(n, to))) {
            val a = anc(P, j); val b = anc(P, j + 1); val c = P[j]
            paint.strokeWidth = segWidth(st, j, K)
            path.reset(); path.moveTo(a[0] * K, a[1] * K); path.quadTo(c[0] * K, c[1] * K, b[0] * K, b[1] * K)
            cv.drawPath(path, paint)
        }
        if (saved >= 0) { if (st.k == "pencil") grain(cv, paint, st, K); cv.restoreToCount(saved) }
    }

    /** 이 획은 늘 처음부터 다시 그려야 함(겹쳐 그리면 진해지는 획) */
    fun whole(st: Stroke) = st.t == "hl" || st.a < 0.999f || st.k == "pencil"

    // 연필 결: 종이 좌표에 고정된 잡티 무늬로 획을 군데군데 덜어냄 (같은 자리는 늘 같은 결)
    private val noise: android.graphics.Bitmap by lazy {
        val s = 96; val r = java.util.Random(7); val px = IntArray(s * s)
        for (i in px.indices) { val v = r.nextFloat(); val a = if (v < .18f) (v / .18f * 150).toInt() else 0; px[i] = Color.argb(a, 0, 0, 0) }
        android.graphics.Bitmap.createBitmap(px, s, s, android.graphics.Bitmap.Config.ARGB_8888)
    }
    private val tlGrain = ThreadLocal.withInitial { Paint().apply { shader = android.graphics.BitmapShader(noise, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT); blendMode = BlendMode.DST_OUT } }
    private fun grain(cv: Canvas, p: Paint, st: Stroke, K: Float) {
        val g = tlGrain.get()!!
        val bb = bbox(listOf(st)); val pad = st.s * K * 2 + 4
        val m = android.graphics.Matrix(); val k = (K / 800f).coerceIn(.6f, 3f); m.setScale(k, k); g.shader.setLocalMatrix(m)
        cv.drawRect(bb[0] * K - pad, bb[1] * K - pad, bb[2] * K + pad, bb[3] * K + pad, g)
    }

    /* ---------- 기하 ---------- */
    fun dist(a: FloatArray, b: FloatArray) = hypot(a[0] - b[0], a[1] - b[1])

    fun dSeg(p: FloatArray, a: FloatArray, b: FloatArray): Float {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l = dx * dx + dy * dy
        var t = if (l > 0) ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l else 0f
        t = t.coerceIn(0f, 1f)
        return hypot(a[0] + t * dx - p[0], a[1] + t * dy - p[1])
    }

    fun hits(st: Stroke, pt: FloatArray, r: Float): Boolean {
        val lim = r + st.s / 2; val P = st.p
        if (P.size == 1) return dist(P[0], pt) <= lim
        for (i in 1 until P.size) if (dSeg(pt, P[i - 1], P[i]) <= lim) return true
        return false
    }

    fun densify(P: List<FloatArray>, step: Float): MutableList<FloatArray> {
        val out = arrayListOf(P[0])
        for (i in 1 until P.size) {
            val a = P[i - 1]; val b = P[i]; val d = dist(a, b); val n = max(1, ceil(d / step).toInt())
            for (k in 1..n) { val t = k.toFloat() / n; out.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, b.getOrElse(2) { .5f })) }
        }
        return out
    }

    fun inPoly(p: FloatArray, poly: List<FloatArray>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a[1] > p[1]) != (b[1] > p[1]) && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) c = !c
            j = i
        }
        return c
    }

    fun bbox(list: List<Stroke>): FloatArray {
        var x0 = 9f; var y0 = 9f; var x1 = -9f; var y1 = -9f
        list.forEach { st -> val pad = st.s / 2 + 0.006f; st.p.forEach { p -> x0 = min(x0, p[0] - pad); y0 = min(y0, p[1] - pad); x1 = max(x1, p[0] + pad); y1 = max(y1, p[1] + pad) } }
        return floatArrayOf(x0, y0, x1, y1)
    }

    fun move(list: List<Stroke>, dx: Float, dy: Float) = list.forEach { st -> st.p.forEach { it[0] += dx; it[1] += dy } }
    fun scale(list: List<Stroke>, k: Float, ox: Float, oy: Float) = list.forEach { st -> st.s *= k; st.p.forEach { it[0] = ox + (it[0] - ox) * k; it[1] = oy + (it[1] - oy) * k } }

    /** 모양 보정: 직선·원(타원)·사각형 */
    fun detectShape(P: List<FloatArray>): MutableList<FloatArray>? {
        if (P.size < 8) return null
        var x0 = 9f; var y0 = 9f; var x1 = -9f; var y1 = -9f; var len = 0f
        P.forEachIndexed { i, p -> x0 = min(x0, p[0]); y0 = min(y0, p[1]); x1 = max(x1, p[0]); y1 = max(y1, p[1]); if (i > 0) len += dist(P[i - 1], p) }
        val bw = x1 - x0; val bh = y1 - y0; val diag = hypot(bw, bh)
        if (diag < 0.05f) return null
        val a = P[0]; val b = P[P.size - 1]; val chord = dist(a, b)
        if (chord / len > 0.92f) {
            var dev = 0f; P.forEach { dev = max(dev, dSeg(it, a, b)) }
            if (dev < diag * 0.07f) return mutableListOf(floatArrayOf(a[0], a[1], .5f), floatArrayOf(b[0], b[1], .5f))
        }
        if (chord < diag * 0.25f && len > diag * 1.8f && bw > 0.03f && bh > 0.03f) {
            val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2; val rx = bw / 2; val ry = bh / 2
            var ee = 0f; var re = 0f
            P.forEach { p ->
                ee += abs(hypot((p[0] - cx) / rx, (p[1] - cy) / ry) - 1)
                re += min(min(abs(p[0] - x0), abs(p[0] - x1)), min(abs(p[1] - y0), abs(p[1] - y1)))
            }
            ee /= P.size; re /= P.size * diag
            if (ee < 0.13f) return (0..48).mapTo(ArrayList()) { i -> val t = i / 48f * PI.toFloat() * 2; floatArrayOf(cx + rx * cos(t), cy + ry * sin(t), .5f) }
            if (re < 0.05f) return mutableListOf(floatArrayOf(x0, y0, .5f), floatArrayOf(x1, y0, .5f), floatArrayOf(x1, y1, .5f), floatArrayOf(x0, y1, .5f), floatArrayOf(x0, y0, .5f))
        }
        return null
    }

    /** S Pen 압력: 약하게 눌러도 선이 끊기지 않도록 완만한 곡선 */
    /** 필압 민감도(설정): 낮음은 세게 눌러야 굵어지고, 높음은 살짝만 눌러도 굵어짐 */
    fun pressure(isPen: Boolean, p: Float): Float {
        if (!isPen || p <= 0f) return 0.5f
        val g = when (kr.yeobaek.note.Y.settings.pressure) { "low" -> 1.1f; "high" -> 0.5f; else -> 0.75f }
        return min(1f, 0.12f + 0.88f * p.pow(g))
    }
}
