package kr.yeobaek.note.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.yeobaek.note.ink.Ink

/** 펜 그림: 위에서 내려다본 촉 — 색 페룰 + 회색 원뿔 + 색 촉 (editor.js penSvg) */
private val pc = HashMap<String, Path>()
private fun P(d: String) = pc.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private val speck = Color(0xFFB9BFC6)
private val shade = Brush.horizontalGradient(0f to Color.White.copy(alpha = .35f), .45f to Color.White.copy(alpha = 0f), 1f to Color(0xFF212529).copy(alpha = .18f), startX = 11f, endX = 29f)
private val silver = Brush.horizontalGradient(0f to Color(0xFFF1F3F5), .5f to Color(0xFFCED4DA), 1f to Color(0xFFADB5BD), startX = 11f, endX = 29f)

@Composable
fun PenArt(kind: String, color: String?, type: String?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val c = color?.let { Color(Ink.parse(it)) } ?: Color(0xFF212529)
    Canvas(modifier.size(size)) {
        val k = this.size.width / 40f
        scale(k, k, Offset.Zero) { pen(kind, c, type) }
    }
}

private fun DrawScope.ferrule(fill: Color, scallop: Boolean = false) {
    if (scallop) drawPath(P("M11 2h18v7c-1.5 0-1.5 2-3 2s-1.5-2-3-2-1.5 2-3 2-1.5-2-3-2-1.5 2-3 2-1.5-2-3-2z"), fill)
    else {
        drawRoundRect(fill, Offset(11f, 2f), Size(18f, 8f), androidx.compose.ui.geometry.CornerRadius(1.2f))
        drawRect(Color.Black.copy(alpha = .2f), Offset(11f, 8.2f), Size(18f, 1.8f))
    }
}
private fun DrawScope.cone(d: String) { val p = P(d); drawPath(p, speck); drawPath(p, shade) }

private fun DrawScope.pen(kind: String, c: Color, type: String?) {
    when {
        kind == "hl" -> {
            ferrule(c); cone("M11 10h18v11l-4 8H15l-4-8z"); drawPath(P("M15 29h10v3.5L15 37z"), c)
        }
        kind == "eraser" -> {
            drawRoundRect(Color(0xFF495057), Offset(11f, 2f), Size(18f, 8f), androidx.compose.ui.geometry.CornerRadius(1.2f))
            val b = P("M11 10h18v16a9 9 0 0 1-18 0z"); drawPath(b, Color(0xFFF1F3F5)); drawPath(b, Color(0xFFCED4DA), style = Stroke(.8f))
            drawPath(P("M14 12v13a6 6 0 0 0 3 5"), Color.White, style = Stroke(2f, cap = StrokeCap.Round))
        }
        type == "fountain" -> {
            ferrule(Color(0xFF212529))
            drawPath(P("M11.5 10h17v5c0 8-5 14-8.5 23-3.5-9-8.5-15-8.5-23z"), silver)
            drawLine(Color(0xFF495057), Offset(20f, 25f), Offset(20f, 38f), .9f)
            drawCircle(Color(0xFF495057), 1.8f, Offset(20f, 24f))
            drawPath(P("M11.5 15c0 4 2 7 4 10"), Color.White.copy(alpha = .7f), style = Stroke(1f))
            drawRect(c, Offset(11f, 6.5f), Size(18f, 2f))
        }
        type == "fine" -> {
            ferrule(c); cone("M11 10h18l-5 15h-8z")
            drawRect(silver, Offset(18.4f, 25f), Size(3.2f, 7f))
            drawRoundRect(c, Offset(19.3f, 32f), Size(1.4f, 5f), androidx.compose.ui.geometry.CornerRadius(.5f))
        }
        type == "brush" -> {
            ferrule(c, true); cone("M11 10h18l-7.6 23.5c-.4 1.3-2.4 1.3-2.8 0z"); drawPath(P("M18.6 30.5h2.8L20 38z"), c)
        }
        else -> {
            ferrule(Color(0xFF212529)); cone("M11 10h18l-6.8 22.5c-.6 1.6-3.8 1.6-4.4 0z")
            drawCircle(c, 1.6f, Offset(20f, 34f)); drawRect(c, Offset(11f, 6.5f), Size(18f, 2f))
        }
    }
}
