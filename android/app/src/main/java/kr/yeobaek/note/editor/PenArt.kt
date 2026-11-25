package kr.yeobaek.note.editor

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.yeobaek.note.ink.Ink
import kr.yeobaek.note.ui.AppleFont

/*
 * 펜 고르기 막대의 도구 그림: 사용자가 준 iPadOS 도구 막대 그림(630×104)을 4배로 확대해 잰 치수와 색 그대로.
 * 좌표는 칸 하나(폭 53, 높이 104) 기준. 고르지 않은 도구 윗끝 y=34, 고른 도구는 18.5 올라옴.
 * 모든 끝과 모서리는 살짝 둥글게, 몸통은 양쪽이 대칭인 원통 광택.
 */
const val SLOT_W = 53f
const val BAR_H = 104f

/** 원통 몸통 광택: 가장자리 어둡고 가운데 밝은 대칭 */
private fun cyl(cx: Float, w: Float) = Brush.horizontalGradient(
    0f to Color(0xFFE9E9E9), .1f to Color(0xFFDADADA), .24f to Color(0xFFE5E5E5), .42f to Color(0xFFF5F4F4),
    .52f to Color(0xFFFDFCFC), .62f to Color(0xFFF5F4F4), .78f to Color(0xFFE5E5E5), .9f to Color(0xFFDADADA), 1f to Color(0xFFE9E9E9),
    startX = cx - w / 2, endX = cx + w / 2)
/** 색 띠·지우개 머리 위에 얹는 광택 (가운데 밝게, 양끝 살짝 어둡게) */
private fun gloss(cx: Float, w: Float) = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = .16f), .2f to Color.Black.copy(alpha = .04f), .5f to Color.White.copy(alpha = .26f),
    .8f to Color.Black.copy(alpha = .04f), 1f to Color.Black.copy(alpha = .16f), startX = cx - w / 2, endX = cx + w / 2)
private fun silver(cx: Float, w: Float) = Brush.horizontalGradient(
    0f to Color(0xFFC4C4C4), .3f to Color(0xFFE6E6E6), .5f to Color(0xFFF7F7F7), .7f to Color(0xFFE6E6E6), 1f to Color(0xFFC4C4C4),
    startX = cx - w / 2, endX = cx + w / 2)
private val EDGE = Color(0xFFD3D3D3)
private val NUM = Color(0xFF8E8E93)
private val round = PathEffect.cornerPathEffect(1.4f)

private fun mix(c: Color, w: Float) = Color(c.red + (1 - c.red) * w, c.green + (1 - c.green) * w, c.blue + (1 - c.blue) * w, 1f)

private val numPaint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
private fun DrawScope.num(text: String, cx: Float, cy: Float) {
    val p = numPaint.get()!!
    p.typeface = AppleFont.sym(500) ?: android.graphics.Typeface.DEFAULT; p.textSize = 7.6f; p.color = NUM.toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy + 2.7f, p)
}

private fun DrawScope.bodyFrom(cx: Float, y: Float, w: Float = 20f) {
    drawRect(cyl(cx, w), Offset(cx - w / 2, y), Size(w, 140f - y))
    drawLine(EDGE, Offset(cx - w / 2, y), Offset(cx - w / 2, 140f), .5f)
    drawLine(EDGE, Offset(cx + w / 2, y), Offset(cx + w / 2, 140f), .5f)
}
private fun DrawScope.band(cx: Float, y0: Float, y1: Float, c: Color, w: Float = 20f) {
    drawRect(c, Offset(cx - w / 2, y0), Size(w, y1 - y0))
    drawRect(gloss(cx, w), Offset(cx - w / 2, y0), Size(w, y1 - y0))
}

/**
 * @param kind pen | hl | eraser | lasso | ruler
 * @param type pen일 때 ball | fine | fountain | brush | pencil | marker
 * @param label 몸통 숫자 (null이면 없음)
 */
fun DrawScope.drawTool(kind: String, type: String?, color: Color, label: String?, lift: Float, cx: Float = SLOT_W / 2) {
    val T = 34f - 18.5f * lift
    when {
        kind == "eraser" -> {
            // 분홍 머리(위가 둥근) + 회색 고리 + 분홍 ×
            val cap = Path().apply {
                moveTo(cx - 10f, T + 17f); lineTo(cx - 10f, T + 8f); quadraticTo(cx - 10f, T, cx - 2f, T)
                lineTo(cx + 2f, T); quadraticTo(cx + 10f, T, cx + 10f, T + 8f); lineTo(cx + 10f, T + 17f); close()
            }
            drawPath(cap, Brush.verticalGradient(0f to Color(0xFFF4A7A6), 1f to Color(0xFFEF9190), startY = T, endY = T + 17f))
            drawPath(cap, gloss(cx, 20f))
            drawRect(cyl(cx, 20f), Offset(cx - 10f, T + 17f), Size(20f, 16.25f))
            drawRect(Color.Black.copy(alpha = .035f), Offset(cx - 10f, T + 17f), Size(20f, 16.25f))
            val xc = T + 25.75f; val xr = 3.1f; val xcol = Color(0xFFEF8E8D)
            drawLine(xcol, Offset(cx - xr, xc - xr), Offset(cx + xr, xc + xr), 1.7f, StrokeCap.Round)
            drawLine(xcol, Offset(cx + xr, xc - xr), Offset(cx - xr, xc + xr), 1.7f, StrokeCap.Round)
            bodyFrom(cx, T + 33.25f)
            drawLine(Color(0xFFD9D9D9), Offset(cx - 10f, T + 33.25f), Offset(cx + 10f, T + 33.25f), .6f)
        }
        kind == "lasso" -> {
            // 줄무늬 원뿔 (끝은 둥글게)
            val cone = Path().apply {
                moveTo(cx - 1f, T + 1.8f); quadraticTo(cx, T - .3f, cx + 1f, T + 1.8f)
                lineTo(cx + 10f, T + 34.25f); lineTo(cx - 10f, T + 34.25f); close()
            }
            drawPath(cone, cyl(cx, 20f))
            clipPath(cone) {
                var x = cx - 46f
                while (x < cx + 14f) { drawLine(Color(0xFFBEBEBE), Offset(x, T + 36f), Offset(x + 36f, T), 1.5f); x += 4.4f }
            }
            bodyFrom(cx, T + 34.25f)
        }
        kind == "ruler" -> {
            val w = 32.5f; val l = cx - w / 2
            drawRoundRect(Color(0xFFF3F3F3), Offset(l, T), Size(w, 140f - T), CornerRadius(1.8f))
            drawRect(Color(0xFFE9E9E9), Offset(l, T + .5f), Size(12.5f, 140f - T))
            drawRoundRect(Color(0xFFD7D7D7), Offset(l, T), Size(w, 140f - T), CornerRadius(1.8f), style = Stroke(.5f))
            var i = 0
            while (true) {
                val y = T + 1.75f + i * 4.5f; if (y > BAR_H + 2) break
                val (len, c, sw) = when { i % 10 == 0 -> Triple(11f, Color(0xFF4A4A4A), 1f); i % 5 == 0 -> Triple(11f, Color(0xFF9A9A9A), 1f); else -> Triple(2.6f, Color(0xFF8E8E8E), .8f) }
                drawLine(c, Offset(l + 1.6f, y), Offset(l + 1.6f + len, y), sw)
                i++
            }
        }
        kind == "hl" || type == "marker" -> {
            // 형광펜: 비스듬한 납작 촉 + 좁은 목 + 둥근 어깨 + 색 띠
            val chisel = Path().apply { moveTo(cx - 5f, T + 4.2f); lineTo(cx + 5f, T + .2f); lineTo(cx + 5f, T + 10.4f); lineTo(cx - 5f, T + 10.4f); close() }
            drawPath(chisel, color, style = Fill)
            drawPath(chisel, color, style = Stroke(1.2f, pathEffect = round))
            drawPath(chisel, gloss(cx, 10f))
            val b = Path().apply {
                moveTo(cx - 6f, T + 10f); lineTo(cx - 6f, T + 26f)
                cubicTo(cx - 6f, T + 31f, cx - 10f, T + 31.5f, cx - 10f, T + 36f); lineTo(cx - 10f, 140f); lineTo(cx + 10f, 140f); lineTo(cx + 10f, T + 36f)
                cubicTo(cx + 10f, T + 31.5f, cx + 6f, T + 31f, cx + 6f, T + 26f); lineTo(cx + 6f, T + 10f); close()
            }
            drawPath(b, cyl(cx, 20f)); drawPath(b, EDGE, style = Stroke(.5f))
            band(cx, T + 39.5f, T + 47f, color)
            label?.let { num(it, cx, T + 62f) }
        }
        type == "pencil" -> {
            // 연필: 둥근 심 + 깎은 나무(면) + 육각 몸통 면 + 가는 색 줄
            val tip = Path().apply { moveTo(cx - 2.4f, T + 11.5f); lineTo(cx - .9f, T + 1.8f); quadraticTo(cx, T + .1f, cx + .9f, T + 1.8f); lineTo(cx + 2.4f, T + 11.5f); close() }
            val wood = Path().apply { moveTo(cx - 2.4f, T + 11.5f); lineTo(cx - 10.25f, T + 35f); lineTo(cx + 10.25f, T + 35f); lineTo(cx + 2.4f, T + 11.5f); close() }
            drawPath(wood, cyl(cx, 20.5f)); drawPath(wood, EDGE, style = Stroke(.5f))
            drawLine(Color(0xFFDDDDDD), Offset(cx - .8f, T + 11.5f), Offset(cx - 3.4f, T + 35f), .6f)
            drawLine(Color(0xFFDDDDDD), Offset(cx + .8f, T + 11.5f), Offset(cx + 3.4f, T + 35f), .6f)
            drawPath(tip, color); drawPath(tip, gloss(cx, 4.8f))
            bodyFrom(cx, T + 35f, 20.5f)
            drawLine(Color(0xFFE0E0E0), Offset(cx - 3.4f, T + 35f), Offset(cx - 3.4f, 140f), .7f)
            drawLine(Color(0xFFE0E0E0), Offset(cx + 3.4f, T + 35f), Offset(cx + 3.4f, 140f), .7f)
            drawRect(mix(color, .3f), Offset(cx - 10.25f, T + 40.2f), Size(20.5f, 1.3f))
            label?.let { num(it, cx, T + 62f) }
        }
        else -> {
            // 펜: 둥근 촉 + 둥글게 부푼 원뿔 + 흰 고리 + 색 띠
            when (type) {
                "fountain" -> {
                    val cone = Path().apply { moveTo(cx - 10f, T + 37f); cubicTo(cx - 9.6f, T + 30f, cx - 7.2f, T + 25f, cx - 6f, T + 21f); lineTo(cx + 6f, T + 21f); cubicTo(cx + 7.2f, T + 25f, cx + 9.6f, T + 30f, cx + 10f, T + 37f); close() }
                    drawPath(cone, cyl(cx, 20f)); drawPath(cone, EDGE, style = Stroke(.5f))
                    val nib = Path().apply { moveTo(cx - 6f, T + 22f); cubicTo(cx - 6f, T + 12f, cx - 1.6f, T + 5f, cx - .5f, T + 1.2f); quadraticTo(cx, T, cx + .5f, T + 1.2f); cubicTo(cx + 1.6f, T + 5f, cx + 6f, T + 12f, cx + 6f, T + 22f); close() }
                    drawPath(nib, silver(cx, 12f)); drawPath(nib, Color(0xFFB8B8B8), style = Stroke(.5f))
                    drawLine(Color(0xFF7A7A7A), Offset(cx, T + 3.5f), Offset(cx, T + 15f), .6f)
                    drawCircle(Color(0xFF7A7A7A), 1.1f, Offset(cx, T + 15.6f))
                }
                "brush" -> {
                    val fer = Path().apply { moveTo(cx - 10f, T + 37f); cubicTo(cx - 9f, T + 28f, cx - 5f, T + 20f, cx - 3.4f, T + 15f); lineTo(cx + 3.4f, T + 15f); cubicTo(cx + 5f, T + 20f, cx + 9f, T + 28f, cx + 10f, T + 37f); close() }
                    drawPath(fer, cyl(cx, 20f)); drawPath(fer, EDGE, style = Stroke(.5f))
                    val tuft = Path().apply { moveTo(cx, T + .6f); cubicTo(cx + 1.4f, T + 4f, cx + 3.4f, T + 10f, cx + 3.4f, T + 15.5f); lineTo(cx - 3.4f, T + 15.5f); cubicTo(cx - 3.4f, T + 10f, cx - 1.4f, T + 4f, cx, T + .6f); close() }
                    drawPath(tuft, color); drawPath(tuft, gloss(cx, 6.8f))
                }
                else -> {
                    val tw = if (type == "fine") 1.8f else 2.4f; val tl = if (type == "fine") 10f else 8.5f
                    val cone = Path().apply { moveTo(cx - 10f, T + 37f); cubicTo(cx - 9.6f, T + 26f, cx - tw - 2.2f, T + tl + 5.5f, cx - tw, T + tl); lineTo(cx + tw, T + tl); cubicTo(cx + tw + 2.2f, T + tl + 5.5f, cx + 9.6f, T + 26f, cx + 10f, T + 37f); close() }
                    drawPath(cone, cyl(cx, 20f)); drawPath(cone, EDGE, style = Stroke(.5f))
                    val tip = Path().apply { moveTo(cx - tw, T + tl); lineTo(cx - tw * .42f, T + 1.6f); quadraticTo(cx, T - .2f, cx + tw * .42f, T + 1.6f); lineTo(cx + tw, T + tl); close() }
                    drawPath(tip, color)
                }
            }
            drawRect(cyl(cx, 20f), Offset(cx - 10f, T + 37f), Size(20f, 2.4f))
            band(cx, T + 39.25f, T + 46.25f, color)
            bodyFrom(cx, T + 46.25f)
            label?.let { num(it, cx, T + 62.5f) }
        }
    }
}

/** 칸 하나 크기의 도구 그림 (폭 53 : 높이 104 비율). yFrom~yTo만 보여 주면 위쪽만 잘라 보임 */
@Composable
fun ToolArt(kind: String, type: String?, color: Color, label: String?, lift: Float, modifier: Modifier = Modifier, yFrom: Float = 0f, yTo: Float = BAR_H) {
    Canvas(modifier.clipToBounds()) {
        val k = size.height / (yTo - yFrom)
        val ox = (size.width - SLOT_W * k) / 2
        translate(ox, -yFrom * k) { scale(k, k, Offset.Zero) { drawTool(kind, type, color, label, lift) } }
    }
}

/** 예전 이름 호환: 작은 펜 그림 */
@Composable
fun PenArt(kind: String, color: String?, type: String?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val c = color?.let { Color(Ink.parse(it)) } ?: Color(0xFF212529)
    ToolArt(kind, type, c, null, 1f, modifier.size(size), yFrom = 10f, yTo = 74f)
}

/** 원본 막대의 무지개 원 (색 고르기 열기) */
fun DrawScope.colorWheel(c: Offset, r: Float) {
    drawCircle(Brush.sweepGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7BE), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFFF3B30)), c), r, c)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = 0f)), c, r * .95f), r, c)
}
