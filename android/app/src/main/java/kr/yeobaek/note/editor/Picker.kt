package kr.yeobaek.note.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Pen
import kr.yeobaek.note.data.Stroke as InkStroke
import kr.yeobaek.note.ink.Ink
import kr.yeobaek.note.ui.Ic
import kr.yeobaek.note.ui.Out
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.Spring
import kr.yeobaek.note.ui.Switch
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.TipSpec
import kr.yeobaek.note.ui.txt
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** 원본 도구 막대 그림에서 뽑은 색 */
object PK {
    val BAR = Color(0xFFF5F5F5); val BAR_DK = Color(0xFF2A2A2D)
    val CIRC = Color(0xFFE6E6E6); val CIRC_DK = Color(0xFF3A3A3D)
    val GLYPH = Color(0xFF6B6B6B)
    val HANDLE = Color(0xFFD9D9D9)
    /** 처음 색 칸: 원본 막대 그대로 (검정, 파랑, 초록, 노랑, 빨강) */
    val ORIG = listOf("#000000", "#157EFA", "#53D669", "#FED031", "#FC3142")
    /** Apple iOS 27 UI 키트 Color Pickers/Color Grid 12×10 (그림에서 칸 가운데 색을 그대로 뽑음) */
    val GRID = listOf(
        "FEFFFE EBEBEB D6D6D6 C2C2C2 ADADAD 999999 858585 707070 5C5C5C 474747 333333 000000",
        "00374A 011D57 11053B 2E063D 3C071B 5C0701 5A1C00 583300 563D00 666100 4F5504 263E0F",
        "004D65 012F7B 1A0A52 450D59 551029 831100 7B2900 7A4A00 785800 8D8602 6F760A 38571A",
        "016E8F 0042A9 2C0977 61187C 791A3D B51A00 AD3E00 A96800 A67B01 C4BC00 9BA50E 4E7A27",
        "008CB4 0056D6 371A94 7A219E 99244F E22400 DA5100 D38301 D19D01 F5EC00 C3D117 669D34",
        "00A1D8 0061FD 4D22B2 982ABC B92D5D FF4015 FF6A00 FFAB01 FCC700 FEFB41 D9EC37 76BB40",
        "01C7FC 3A87FD 5E30EB BE38F3 E63B7A FE6250 FE8648 FEB43F FECB3E FFF76B E4EF65 96D35F",
        "52D6FC 74A7FF 864FFD D357FE EE719E FF8C82 FEA57D FEC777 FED977 FFF994 EAF28F B1DD8B",
        "93E3FC A7C6FF B18CFE E292FE F4A4C0 FFB5AF FFC5AB FED9A8 FDE4A8 FFFBB9 F1F7B7 CDE8B5",
        "CBF0FF D2E2FE D8C9FE EFCAFE F9D3E0 FFDAD8 FFE2D6 FEECD4 FEF1D5 FDFBDD F6FADB DEEED4",
    ).map { r -> r.split(" ").map { "#$it" } }
    /** 펜 종류 (펜 칸마다 따로 저장) */
    val TYPES = listOf("ball" to "볼펜", "fine" to "세필", "fountain" to "만년필", "brush" to "붓펜", "pencil" to "연필", "marker" to "마커", "hl" to "형광펜")
}

/** 펜 칸마다 저장한 굵기(mm) ↔ 화면 퍼센트 */
object Width {
    fun range(p: Pen) = if (p.kind == "hl") 2f to 20f else 0.2f to 12f
    fun pct(p: Pen): Int { val (a, b) = range(p); return (((p.mm - a) / (b - a)).coerceIn(0f, 1f).pow(1f / 1.6f) * 100).roundToInt().coerceIn(1, 100) }
    fun mm(p: Pen, pct: Float): Float { val (a, b) = range(p); return a + (pct / 100f).coerceIn(0f, 1f).pow(1.6f) * (b - a) }
}

fun pushRecent(c: String) {
    val l = Y.settings.recent
    l.removeAll { it.equals(c, true) }; l.add(0, c.uppercase())
    while (l.size > 10) l.removeAt(l.size - 1)
    Y.saveSettings()
}
fun swatches(): List<String> { val r = Y.settings.recent; return (r + PK.ORIG.filter { o -> r.none { it.equals(o, true) } }).take(5) }

/** 막대를 끌어 떼어 내기·옮기기 */
interface PickerDrag { fun start(); fun drag(d: Offset); fun end() }

/* =============== 펜 고르기 막대 (원본 그림 그대로) =============== */
@Composable
fun ToolPicker(ed: Ed, modifier: Modifier = Modifier, drag: PickerDrag? = null) {
    Y.penRev; Y.setRev; ed.rev; Y.mode
    val dark = Y.isDark()
    val shape = RoundedCornerShape(52.dp)
    Box(modifier.height(BAR_H.dp)
        .shadow(14.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
        .background(if (dark) PK.BAR_DK else PK.BAR, shape).clip(shape)
        .then(if (drag != null) Modifier.pointerInput(drag) {
            detectDragGestures(onDragStart = { drag.start() }, onDragEnd = { drag.end() }, onDragCancel = { drag.end() }) { ch, d -> ch.consume(); drag.drag(d) }
        } else Modifier)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(36.dp, 4.dp).background(if (dark) Color(0xFF55555A) else PK.HANDLE, CircleShape))
        Row(Modifier.fillMaxSize().padding(start = 21.dp, end = 21.dp), verticalAlignment = Alignment.CenterVertically) {
            val v = ed.view
            Circ("undo", "실행취소", v?.canUndo == true) { v?.undo() }
            Spacer(Modifier.width(10.dp))
            Circ("redo", "다시실행", v?.canRedo == true) { v?.redo() }
            Spacer(Modifier.width(4.dp))
            PenStrip(ed, Modifier.weight(1f).fillMaxHeight())
            Slot("eraser", null, Color.Unspecified, null, Y.mode == "eraser", "지우개") { r ->
                if (Y.mode != "eraser") { if (Y.mode == "lasso") ed.view?.clearSel(); Y.mode = "eraser"; Overlay.closePop() } else eraserPop(r)
                Y.haptic(8); ed.rev++
            }
            Slot("lasso", null, Color.Unspecified, null, Y.mode == "lasso", "올가미") {
                Y.mode = if (Y.mode == "lasso") { ed.view?.clearSel(); "pen" } else "lasso"; Overlay.closePop(); Y.haptic(8); ed.rev++
            }
            if (Y.settings.ruler) Slot("ruler", null, Color.Unspecified, null, ed.ruler, "자", w = 60f) {
                ed.ruler = !ed.ruler; ed.view?.setRuler(ed.ruler); Y.haptic(8); ed.rev++
            }
            Spacer(Modifier.width(12.dp))
            ColorGrid(ed)
            Spacer(Modifier.width(12.dp))
            Circ("more", "필기 옵션", true) { r -> pickerMore(r, ed) }
        }
    }
}

/** 원본의 회색 동그라미 버튼 (되돌리기, 다시 하기, 더보기) */
@Composable
private fun Circ(icon: String, label: String, enabled: Boolean, onClick: (Rect) -> Unit) {
    var b by remember { mutableStateOf(Rect.Zero) }
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) .9f else 1f, tween(if (down) 80 else 220, easing = Out), label = "c")
    val dark = Y.isDark()
    Box(Modifier.size(32.dp).onGloballyPositioned { b = it.boundsInRoot() }.graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else .45f }
        .background(if (dark) PK.CIRC_DK else PK.CIRC, CircleShape)
        .pointerInput(enabled, label) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onLongPress = { Overlay.tip = TipSpec(System.nanoTime(), label, b) }, onTap = { if (enabled) onClick(b) }) },
        contentAlignment = Alignment.Center) { Ic(icon, size = 17.dp, color = if (dark) Color(0xFFC7C7CC) else PK.GLYPH, stroke = 2f) }
}

/** 도구 칸 하나 (고르면 18.5 올라옴) */
@Composable
private fun Slot(kind: String, type: String?, color: Color, label: String?, sel: Boolean, name: String, w: Float = SLOT_W, onClick: (Rect) -> Unit) {
    val lift by animateFloatAsState(if (sel) 1f else 0f, tween(380, easing = Spring), label = "lift")
    var b by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.width(w.dp).fillMaxHeight().onGloballyPositioned { b = it.boundsInRoot() }
        .pointerInput(kind, type, name) { detectTapGestures(onLongPress = { Overlay.tip = TipSpec(System.nanoTime(), name, b) }, onTap = { onClick(b) }) }) {
        ToolArt(kind, type, color, label, lift, Modifier.fillMaxSize())
    }
}

private fun penLabel(p: Pen) = "${(p.alpha * 100).roundToInt()}"
private fun artKind(p: Pen) = if (p.kind == "hl") "hl" else "pen"

/** 펜 줄: 옆으로 밀기 + 끝에서 더 당기면 고무줄 게이지가 차고 "펜 추가하기" */
@Composable
private fun PenStrip(ed: Ed, modifier: Modifier) {
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val n = Y.settings.trayCount.coerceIn(1, 12)
    val pens = Y.pens.take(n)
    if (Y.penIdx >= pens.size) Y.penIdx = 0
    val slotPx = with(d) { SLOT_W.dp.toPx() }
    val gaugePx = with(d) { 76.dp.toPx() }
    var vw by remember { mutableFloatStateOf(1f) }
    val scroll = remember { Animatable(0f) }
    val pull = remember { Animatable(0f) }        // 당긴 양(원래 손 움직임)
    var full by remember { mutableStateOf(false) }
    var gaugeRect by remember { mutableStateOf(Rect.Zero) }
    val maxS = max(0f, pens.size * slotPx - vw)
    fun shown(raw: Float) = gaugePx * 1.25f * (1f - exp(-raw / (gaugePx * 1.25f)))   // 늘어날수록 뻑뻑해지는 고무줄
    val prog = (shown(pull.value) / gaugePx).coerceIn(0f, 1f)
    Box(modifier.clipToBounds().onSizeChanged { vw = it.width.toFloat() }
        .pointerInput(pens.size, vw) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragging = false; var tot = Offset.Zero
                val vt = VelocityTracker(); vt.addPosition(down.uptimeMillis, down.position)
                scope.launch { scroll.stop() }
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val dl = ch.positionChange(); tot += dl; vt.addPosition(ch.uptimeMillis, ch.position)
                    if (!dragging && abs(tot.x) > viewConfiguration.touchSlop && abs(tot.x) > abs(tot.y) * 1.2f) dragging = true
                    if (!dragging && abs(tot.y) > viewConfiguration.touchSlop * 1.5f) return@awaitEachGesture   // 위아래 = 막대 끌기
                    if (dragging) {
                        ch.consume()
                        val dx = -dl.x
                        scope.launch {
                            if (pull.value > 0f || scroll.value + dx > maxS) {
                                val over = (pull.value + (if (pull.value > 0f) dx else scroll.value + dx - maxS))
                                if (over < 0f) { pull.snapTo(0f); scroll.snapTo((maxS + over).coerceIn(0f, maxS)) } else { scroll.snapTo(maxS); pull.snapTo(over) }
                                val f = shown(pull.value) >= gaugePx
                                if (f && !full) Y.haptic(14)
                                full = f
                            } else scroll.snapTo((scroll.value + dx).coerceIn(0f, maxS))
                        }
                    }
                }
                if (dragging) {
                    val wasFull = full
                    full = false
                    scope.launch { pull.animateTo(0f, spring(.72f, 420f)) }
                    if (wasFull) addPenPop(gaugeRect, ed)
                    else {
                        val vx = -vt.calculateVelocity().x
                        scope.launch { scroll.updateBounds(0f, maxS); scroll.animateDecay(vx, exponentialDecay(1.6f)); scroll.updateBounds(null, null) }
                    }
                }
            }
        }) {
        val sh = shown(pull.value)
        Row(Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true).graphicsLayer { translationX = -scroll.value - sh }, verticalAlignment = Alignment.CenterVertically) {
            pens.forEachIndexed { i, p ->
                val sel = (Y.mode == "pen") && Y.penIdx == i
                Slot(artKind(p), p.type, Color(Ink.parse(p.color)), penLabel(p), sel, PK.TYPES.firstOrNull { it.first == (if (p.kind == "hl") "hl" else p.type) }?.second ?: "펜") { r ->
                    val same = sel
                    Y.penIdx = i; Y.haptic(8)
                    if (Y.mode != "pen") { if (Y.mode == "lasso") ed.view?.clearSel(); Y.mode = "pen" }
                    if (same) penPop(r, i, ed) else Overlay.closePop()
                    ed.rev++
                }
            }
            // 고무줄 게이지: + 가 점점 커지고 둘레가 차며, 아래 "펜 추가하기"가 서서히 보임
            Box(Modifier.width(with(d) { max(sh, 1f).toDp() }).fillMaxHeight().onGloballyPositioned { gaugeRect = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
                if (sh > 2f) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wrapContentWidth(unbounded = true)) {
                    val acc = T.acc
                    Canvas(Modifier.size(34.dp)) {
                        val r = size.minDimension / 2 - 2.dp.toPx()
                        drawCircle(Color(0x1F000000), r, style = Stroke(2.5.dp.toPx()))
                        drawArc(if (prog >= 1f) acc else Color(0xFF8E8E93), -90f, 360f * prog, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                        val k = .45f + .55f * prog; val L = r * .55f * k; val sw = 2.2.dp.toPx()
                        val pc = if (prog >= 1f) acc else Color(0xFF6B6B6B)
                        drawLine(pc, Offset(center.x - L, center.y), Offset(center.x + L, center.y), sw, StrokeCap.Round)
                        drawLine(pc, Offset(center.x, center.y - L), Offset(center.x, center.y + L), sw, StrokeCap.Round)
                    }
                    Spacer(Modifier.height(5.dp))
                    BasicText("펜 추가하기", Modifier.graphicsLayer { alpha = ((prog - .25f) / .75f).coerceIn(0f, 1f) }, style = txt(10.sp, 600, Color(0xFF6B6B6B)), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** 색 3×2: 최근 색 5개 + 무지개(색 창) */
@Composable
private fun ColorGrid(ed: Ed) {
    Y.setRev; Y.penRev
    val sw = swatches()
    val pen = Y.curPen()
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        for (row in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(12.5.dp)) {
            for (col in 0..2) {
                val i = row * 3 + col
                var b by remember { mutableStateOf(Rect.Zero) }
                if (i < 5) {
                    val c = sw[i]
                    val on = Y.mode == "pen" && pen.color.equals(c, true)
                    Canvas(Modifier.size(29.dp).onGloballyPositioned { b = it.boundsInRoot() }
                        .pointerInput(c) {
                            detectTapGestures(
                                onLongPress = { Y.haptic(10); colorsPop(b, ed, replace = i) },
                                onTap = { applyColor(ed, c, recent = false); Y.haptic(6) })
                        }) {
                        val col2 = Color(Ink.parse(c))
                        drawCircle(col2)
                        if (on) drawCircle(Color.White, size.minDimension / 2 - 3.6.dp.toPx(), style = Stroke(2.4.dp.toPx()))
                        if (c.equals("#FFFFFF", true) || c.equals("#FEFFFE", true)) drawCircle(Color(0x33000000), size.minDimension / 2, style = Stroke(.5.dp.toPx()))
                    }
                } else {
                    Canvas(Modifier.size(29.dp).onGloballyPositioned { b = it.boundsInRoot() }
                        .pointerInput(Unit) { detectTapGestures { colorsPop(b, ed) } }) { colorWheel(center, size.minDimension / 2) }
                }
            }
        }
    }
}

/** 지금 펜에 색 넣기 (지우개·올가미였으면 펜으로) */
fun applyColor(ed: Ed, c: String, recent: Boolean = true, alpha: Float? = null) {
    if (Y.mode != "pen") { if (Y.mode == "lasso") ed.view?.let { v -> if (v.sel != null) { v.recolorSel(c); return } ; v.clearSel() }; Y.mode = "pen" }
    val p = Y.curPen(); p.color = c.uppercase(); alpha?.let { p.alpha = it }
    Y.savePens(); if (recent) pushRecent(c); ed.rev++
}

/* =============== 펜 창: 종류, 굵기 %, 투명도, 색 =============== */
fun penPop(r: Rect, i: Int, ed: Ed) {
    Overlay.popover("pen$i", r, 360.dp, "center") {
        Y.penRev; Y.setRev
        val pen = Y.pens.getOrNull(i) ?: return@popover
        // 종류: 칸마다 따로 저장
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PK.TYPES.forEach { (id, name) ->
                val on = if (id == "hl") pen.kind == "hl" else pen.kind == "pen" && (pen.type ?: "ball") == id
                val lift by animateFloatAsState(if (on) 1f else 0f, tween(320, easing = Spring), label = "t")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).pointerInput(id) {
                    detectTapGestures {
                        if (id == "hl") { if (pen.kind != "hl") { pen.kind = "hl"; pen.type = null; pen.mm = 6f; pen.alpha = .4f } }
                        else { if (pen.kind == "hl") { pen.kind = "pen"; pen.mm = 0.8f; pen.alpha = 1f }; pen.type = id }
                        Y.haptic(6); Y.savePens(); ed.rev++
                    }
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ToolArt(if (id == "hl") "hl" else "pen", if (id == "hl") null else id, Color(Ink.parse(pen.color)), null, lift, Modifier.size(44.dp, 58.dp), yFrom = 12f, yTo = 74f)
                    BasicText(name, style = txt(11.sp, if (on) 600 else 400, if (on) T.snow else T.ink2), maxLines = 1)
                }
            }
        }
        Sep()
        // 굵기: 퍼센트, 실시간 미리보기, 천천히 끌면 돋보기 눈금으로 미세 조정
        val pct = Width.pct(pen)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("굵기", Modifier.weight(1f), style = txt(13.sp, 600, T.ink2))
            BasicText("$pct%", style = txt(13.sp, 600, T.snow))
        }
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (Y.isDark()) Color(0xFF1C1C1E) else Color.White)) {
            val w = size.width; val hh = size.height
            val pts = (0..80).map { k -> val t = k / 80f; floatArrayOf(.08f + t * .84f, (hh / 2 + kotlin.math.sin(t * Math.PI * 2).toFloat() * hh * .2f) / w, .3f + .55f * kotlin.math.sin(t * Math.PI).toFloat()) }
            val st = InkStroke(pen.kind, pen.color, pen.mm / 210f * (w / 300f * 2.2f), pen.alpha, if (pen.kind == "pen") pen.type?.takeIf { it != "ball" } else null, pts.toMutableList())
            drawContext.canvas.nativeCanvas.let { Ink.draw(it, st, w, Y.isDark()) }
        }
        FineSlider(pct.toFloat(), 1f, 100f, Modifier.padding(horizontal = 12.dp)) { v -> pen.mm = (Width.mm(pen, v) * 100).roundToInt() / 100f; Y.savePens() }
        Spacer(Modifier.height(6.dp))
        // 투명도: 손잡이를 잡으면 유리 렌즈처럼
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("투명도", Modifier.weight(1f), style = txt(13.sp, 600, T.ink2))
            BasicText("${(pen.alpha * 100).roundToInt()}%", style = txt(13.sp, 600, T.snow))
        }
        GlassSlider(pen.alpha * 100f, 5f, 100f, Color(Ink.parse(pen.color)), Modifier.padding(horizontal = 12.dp)) { v -> pen.alpha = (v.roundToInt() / 100f).coerceIn(.05f, 1f); Y.savePens(); ed.rev++ }
        Sep()
        // 색: 최근 쓴 색 + 무지개(색 창)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val list = (Y.settings.recent + PK.ORIG).distinctBy { it.uppercase() }.take(8)
            list.forEach { c ->
                val on = pen.color.equals(c, true)
                Canvas(Modifier.size(30.dp).pointerInput(c) { detectTapGestures { pen.color = c.uppercase(); Y.savePens(); Y.haptic(6); ed.rev++ } }) {
                    drawCircle(Color(Ink.parse(c)))
                    if (on) drawCircle(Color.White, size.minDimension / 2 - 3.6.dp.toPx(), style = Stroke(2.4.dp.toPx()))
                }
            }
            var b by remember { mutableStateOf(Rect.Zero) }
            Canvas(Modifier.size(30.dp).onGloballyPositioned { b = it.boundsInRoot() }.pointerInput(Unit) { detectTapGestures { Overlay.closePop(); colorsPop(b, ed) } }) { colorWheel(center, size.minDimension / 2) }
        }
    }
}

@Composable private fun Sep() = Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(.5.dp).background(T.hair))

/**
 * 굵기 막대: 빠르게 끌면 평소대로, 천천히 끌면 돋보기 팝업(확대된 눈금)이 뜨고 움직임이 5분의 1로 줄어 세밀하게.
 */
@Composable
fun FineSlider(value: Float, min: Float, max: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    var fine by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var live by remember { mutableFloatStateOf(value) }
    if (!held) live = value
    val k = ((live - min) / (max - min)).coerceIn(0f, 1f)
    val acc = T.acc
    val d = LocalDensity.current
    val loupeA by animateFloatAsState(if (fine && held) 1f else 0f, tween(220, easing = Out), label = "loupe")
    Box(modifier.fillMaxWidth().height(36.dp).onSizeChanged { w = it.width.toFloat() }
        .pointerInput(min, max) {
            awaitEachGesture {
                val down = awaitFirstDown()
                held = true
                val r = 19.dp.toPx()
                val kx = (down.position.x - r) / (w - 2 * r)
                // 손잡이 밖을 누르면 그 자리로
                if (abs(down.position.x - (r + (w - 2 * r) * ((live - min) / (max - min)))) > r * 1.4f) { live = (min + kx.coerceIn(0f, 1f) * (max - min)); onChange(live) }
                var slowSince = -1L
                while (true) {
                    val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val dx = ch.positionChange().x; ch.consume()
                    val dt = max(1L, ch.uptimeMillis - ch.previousUptimeMillis)
                    val speed = abs(dx) / dt * 1000f / density            // dp/초
                    val t = ch.uptimeMillis
                    if (speed < 70f) { if (slowSince < 0) slowSince = t; if (t - slowSince > 140 && !fine) { fine = true; Y.haptic(6) } }
                    else { slowSince = -1; if (speed > 260f) fine = false }
                    val gain = if (fine) .2f else 1f
                    val nv = (live + dx / (w - 2 * r) * (max - min) * gain).coerceIn(min, max)
                    if (fine && nv.roundToInt() != live.roundToInt()) Y.haptic(3)
                    live = nv; onChange(live)
                }
                held = false; fine = false
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2; val r = 19.dp.toPx(); val x = r + (size.width - 2 * r) * k
            drawLine(Color(0x29787880), Offset(r, y), Offset(size.width - r, y), 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(acc, Offset(r, y), Offset(x, y), 6.dp.toPx(), cap = StrokeCap.Round)
            val kw = 38.dp.toPx(); val kh = 24.dp.toPx()
            drawRoundRect(Color(0x26000000), Offset(x - kw / 2, y - kh / 2 + 3.dp.toPx()), Size(kw, kh), CornerRadius(kh / 2))
            drawRoundRect(Color.White, Offset(x - kw / 2, y - kh / 2), Size(kw, kh), CornerRadius(kh / 2))
        }
        // 돋보기: 손잡이 위로 확대된 눈금과 숫자
        if (loupeA > .01f) {
            val r = with(d) { 19.dp.toPx() }
            val x = r + (w - 2 * r) * k
            val lw = with(d) { 132.dp.toPx() }
            Box(Modifier.offset { IntOffset((x - lw / 2).roundToInt(), (-62).dp.roundToPx()) }.size(132.dp, 54.dp)
                .graphicsLayer { alpha = loupeA; val s = .85f + .15f * loupeA; scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f) }
                .shadow(10.dp, RoundedCornerShape(27.dp)).background(Color.White.copy(alpha = .97f), RoundedCornerShape(27.dp)).border(.5.dp, Color(0x1F000000), RoundedCornerShape(27.dp))) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2; val per = 9.dp.toPx()           // 1단위 = 9dp (원래의 약 4배)
                    val base = live
                    var u = kotlin.math.floor(base - cx / per).toInt()
                    while (true) {
                        val px = cx + (u - base) * per; if (px > size.width) break
                        if (u in min.toInt()..max.toInt()) {
                            val long = u % 10 == 0; val mid = u % 5 == 0
                            val h = if (long) 16.dp.toPx() else if (mid) 11.dp.toPx() else 7.dp.toPx()
                            drawLine(if (long) Color(0xFF3A3A3C) else Color(0xFF9A9AA0), Offset(px, size.height - 8.dp.toPx() - h), Offset(px, size.height - 8.dp.toPx()), if (long) 1.4.dp.toPx() else 1.dp.toPx())
                        }
                        u++
                    }
                    drawLine(acc, Offset(cx, 6.dp.toPx()), Offset(cx, size.height - 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
                }
                BasicText("${live.roundToInt()}%", Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 6.dp), style = txt(12.sp, 700, Color(0xFF1C1C1E)))
            }
        }
    }
}

/**
 * 투명도 막대: 잡으면 손잡이가 커지며 투명한 유리 렌즈가 되어 아래 막대가 확대돼 비침 (iOS 26 Liquid Glass 손잡이처럼).
 */
@Composable
fun GlassSlider(value: Float, min: Float, max: Float, tint: Color, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    var held by remember { mutableStateOf(false) }
    val g by animateFloatAsState(if (held) 1f else 0f, spring(.62f, 520f), label = "glass")
    val k = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val acc = T.acc
    Box(modifier.fillMaxWidth().height(40.dp).onSizeChanged { w = it.width.toFloat() }
        .pointerInput(min, max) {
            awaitEachGesture {
                val down = awaitFirstDown(); held = true
                val r = 19.dp.toPx()
                fun at(x: Float) = min + ((x - r) / (w - 2 * r)).coerceIn(0f, 1f) * (max - min)
                onChange(at(down.position.x))
                while (true) {
                    val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    ch.consume(); onChange(at(ch.position.x))
                }
                held = false
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2; val r = 19.dp.toPx(); val x = r + (size.width - 2 * r) * k
            val th = 6.dp.toPx()
            fun track() {
                drawLine(Color(0x29787880), Offset(r, y), Offset(size.width - r, y), th, cap = StrokeCap.Round)
                drawLine(Brush2.fade(tint, r, x), Offset(r, y), Offset(x, y), th, cap = StrokeCap.Round)
            }
            track()
            val kw = (38f + 22f * g).dp.toPx(); val kh = (24f + 12f * g).dp.toPx()
            val rr = RoundRect(x - kw / 2, y - kh / 2, x + kw / 2, y + kh / 2, CornerRadius(kh / 2))
            drawRoundRect(Color(0x22000000), Offset(rr.left, rr.top + (3f + 3f * g).dp.toPx()), Size(kw, kh), CornerRadius(kh / 2))
            // 잡기 전: 흰 캡슐 / 잡으면: 투명 유리 (안쪽에 막대가 1.4배로 비침 + 테두리 빛)
            drawRoundRect(Color.White.copy(alpha = 1f - .82f * g), Offset(rr.left, rr.top), Size(kw, kh), CornerRadius(kh / 2))
            if (g > .02f) {
                val path = Path().apply { addRoundRect(rr) }
                clipPath(path) { scale(1f + .4f * g, 1f + .4f * g, Offset(x, y)) { track() } }
                drawRoundRect(Color.White.copy(alpha = .9f * g), Offset(rr.left, rr.top), Size(kw, kh), CornerRadius(kh / 2), style = Stroke(1.5.dp.toPx()))
                drawRoundRect(Color.Black.copy(alpha = .10f * g), Offset(rr.left, rr.top), Size(kw, kh), CornerRadius(kh / 2), style = Stroke(.5.dp.toPx()))
                drawLine(Color.White.copy(alpha = .8f * g), Offset(rr.left + kh * .45f, rr.top + 3.dp.toPx()), Offset(rr.right - kh * .45f, rr.top + 3.dp.toPx()), 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

private object Brush2 {
    fun fade(c: Color, x0: Float, x1: Float) = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.copy(alpha = .08f), c), startX = x0, endX = max(x0 + 1f, x1))
}

/* =============== 색 창 (Apple iOS 27 UI 키트 Color Pickers/iPad 그대로) =============== */
/** @param replace 막대의 색 칸 번호: 고른 색으로 그 칸을 바꿈 */
fun colorsPop(r: Rect, ed: Ed, replace: Int? = null) {
    Overlay.popover("colors", r, 372.dp, "center") {
        Y.penRev; Y.setRev
        val pen = Y.curPen()
        var tab by remember { mutableIntStateOf(0) }
        fun pick(c: String) {
            if (replace != null) {
                val l = swatches().toMutableList(); l[replace] = c.uppercase()
                Y.settings.recent = (l + Y.settings.recent.filter { x -> l.none { it.equals(x, true) } }).take(10).toMutableList(); Y.saveSettings()
                applyColor(ed, c, recent = false)
            } else applyColor(ed, c)
        }
        // 머리: 제목 + 닫기
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            BasicText("색", Modifier.align(Alignment.Center), style = txt(17.sp, 600, T.snow))
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(30.dp).background(Color(0x1F787880), CircleShape)
                .pointerInput(Unit) { detectTapGestures { Overlay.closePop() } }, contentAlignment = Alignment.Center) { Ic("close", size = 14.dp, color = T.ink2, stroke = 2f) }
        }
        // 세그먼트 (Segmented Controls: 높이 32, 고른 칸 흰색)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(32.dp).background(Color(0x1F767680), RoundedCornerShape(16.dp)).padding(2.dp)) {
            listOf("격자", "스펙트럼").forEachIndexed { i, s ->
                Box(Modifier.weight(1f).fillMaxHeight().then(if (tab == i) Modifier.shadow(2.dp, RoundedCornerShape(14.dp)).background(Color.White, RoundedCornerShape(14.dp)) else Modifier)
                    .pointerInput(i) { detectTapGestures { tab = i } }, contentAlignment = Alignment.Center) { BasicText(s, style = txt(14.sp, if (tab == i) 600 else 500, Color(0xFF1C1C1E))) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) {
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(250.dp).clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val c = (o.x / size.width * 12).toInt().coerceIn(0, 11); val rw = (o.y / size.height * 10).toInt().coerceIn(0, 9)
                        pick(PK.GRID[rw][c]); Y.haptic(6)
                    }
                }) {
                val cw = size.width / 12; val ch = size.height / 10
                PK.GRID.forEachIndexed { rw, row -> row.forEachIndexed { c, hex -> drawRect(Color(Ink.parse(hex)), Offset(c * cw, rw * ch), Size(cw + .5f, ch + .5f)) } }
                PK.GRID.forEachIndexed { rw, row -> row.forEachIndexed { c, hex -> if (hex.equals(pen.color, true)) drawRect(Color.White, Offset(c * cw + 1.5.dp.toPx(), rw * ch + 1.5.dp.toPx()), Size(cw - 3.dp.toPx(), ch - 3.dp.toPx()), style = Stroke(3.dp.toPx())) } }
            }
        } else {
            // 스펙트럼: 가로 = 색상, 세로 = 밝게 ~ 어둡게
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(250.dp).clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { o -> pick(spec(o.x / size.width, o.y / size.height)); Y.haptic(6) }
                }) {
                val n = 48; val m = 30; val cw = size.width / n; val chh = size.height / m
                for (i in 0 until n) for (j in 0 until m) drawRect(Color(Ink.parse(spec((i + .5f) / n, (j + .5f) / m))), Offset(i * cw, j * chh), Size(cw + .6f, chh + .6f))
            }
        }
        Spacer(Modifier.height(14.dp))
        BasicText("투명도", Modifier.padding(start = 6.dp), style = txt(13.sp, 600, T.ink2))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { GlassSlider(pen.alpha * 100f, 5f, 100f, Color(Ink.parse(pen.color))) { v -> pen.alpha = (v.roundToInt() / 100f).coerceIn(.05f, 1f); Y.savePens(); ed.rev++ } }
            BasicText("${(pen.alpha * 100).roundToInt()}%", Modifier.width(56.dp), style = txt(17.sp, 500, T.snow).copy(textAlign = TextAlign.End))
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(.5.dp).background(T.hair))
        // 아래: 지금 색 큰 칸 + 저장한 색 + (지금 색 저장)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(Ink.parse(pen.color)).copy(alpha = pen.alpha)))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val saved = Y.settings.saved
                for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (col in 0..4) {
                        val i = row * 5 + col
                        if (i == 9) {
                            Box(Modifier.size(30.dp).background(Color(0x1F787880), CircleShape).pointerInput(Unit) {
                                detectTapGestures { val c = pen.color.uppercase(); if (saved.none { it.equals(c, true) }) { saved.add(0, c); while (saved.size > 9) saved.removeAt(saved.size - 1); Y.saveSettings() }; Y.haptic(8) }
                            }, contentAlignment = Alignment.Center) { Ic("plus", size = 16.dp, color = T.snow, stroke = 2f) }
                        } else {
                            val c = saved.getOrNull(i)
                            Canvas(Modifier.size(30.dp).pointerInput(c) { detectTapGestures { if (c != null) { pick(c); Y.haptic(6) } } }) {
                                if (c != null) { drawCircle(Color(Ink.parse(c))); if (c.equals(pen.color, true)) drawCircle(Color.White, size.minDimension / 2 - 3.6.dp.toPx(), style = Stroke(2.4.dp.toPx())) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 스펙트럼 색: h(0~1) 색상, v(0~1) 위는 밝게 아래는 어둡게 */
private fun spec(h: Float, v: Float): String {
    val hsv = if (v < .5f) floatArrayOf(h * 360f, (v / .5f).coerceIn(.08f, 1f), 1f) else floatArrayOf(h * 360f, 1f, (1f - (v - .5f) / .5f * .9f))
    val c = android.graphics.Color.HSVToColor(hsv)
    return "#%06X".format(c and 0xFFFFFF)
}

/* =============== 펜 추가 (고무줄을 끝까지 당겨 놓으면) =============== */
fun addPenPop(r: Rect, ed: Ed) {
    Overlay.popover("addpen", r, 340.dp, "center") {
        BasicText("펜 추가하기", Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), style = txt(15.sp, 600, T.snow))
        val c = Y.settings.recent.firstOrNull() ?: "#000000"
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PK.TYPES.forEach { (id, name) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).pointerInput(id) {
                    detectTapGestures {
                        val p = if (id == "hl") Pen("hl", null, "#FFE45C", 6f, .4f) else Pen("pen", id, c, if (id == "marker") 2.4f else 0.8f, 1f)
                        Y.pens.add(min(Y.pens.size, Y.settings.trayCount.coerceAtLeast(1)), p)
                        if (Y.settings.trayCount < Y.pens.size) { Y.settings.trayCount = min(12, Y.pens.size); Y.saveSettings() }
                        Y.penIdx = Y.pens.indexOf(p); Y.mode = "pen"; Y.savePens(); Y.haptic(12); Overlay.closePop(); ed.rev++
                    }
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ToolArt(if (id == "hl") "hl" else "pen", if (id == "hl") null else id, Color(Ink.parse(if (id == "hl") "#FFE45C" else c)), null, 0f, Modifier.size(44.dp, 60.dp), yFrom = 26f, yTo = 90f)
                    BasicText(name, style = txt(11.sp, 500, T.ink2), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/* =============== 필기 옵션 (막대의 ⋯) =============== */
fun pickerMore(r: Rect, ed: Ed) {
    Overlay.popover("pmore", r, 320.dp, "right") {
        Y.setRev; ed.rev
        @Composable fun Line(t: String, content: @Composable () -> Unit) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t, Modifier.weight(1f), style = txt(15.sp, 400, T.snow)); content()
            }
        }
        // 막대에 없는 필기 도구
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("text", "text", "글상자"), Triple("space", "space", "공간 삽입")).forEach { (m, ic, t) ->
                val on = Y.mode == m
                Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) T.acc.copy(alpha = .14f) else Color(0x14787880))
                    .pointerInput(m) { detectTapGestures { if (Y.mode == "lasso") ed.view?.clearSel(); Y.mode = if (Y.mode == m) "pen" else m; Overlay.closePop(); ed.rev++ } },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Ic(ic, size = 18.dp, color = if (on) T.acc else T.snow); Spacer(Modifier.width(6.dp)); BasicText(t, style = txt(14.sp, 600, if (on) T.acc else T.snow))
                }
            }
        }
        Sep()
        Line("자") { Switch(Y.settings.ruler) { Y.settings.ruler = it; if (!it && ed.ruler) { ed.ruler = false; ed.view?.setRuler(false) }; Y.saveSettings(); ed.rev++ } }
        Line("펜 개수") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(Color(0x14787880), CircleShape).pointerInput(Unit) { detectTapGestures { Y.settings.trayCount = (Y.settings.trayCount - 1).coerceIn(1, 12); Y.saveSettings(); ed.rev++ } }, contentAlignment = Alignment.Center) { Ic("minus", size = 14.dp, color = T.snow, stroke = 2f) }
                BasicText("${Y.settings.trayCount}", Modifier.width(36.dp), style = txt(15.sp, 600, T.snow).copy(textAlign = TextAlign.Center))
                Box(Modifier.size(32.dp).background(Color(0x14787880), CircleShape).pointerInput(Unit) {
                    detectTapGestures {
                        val n = (Y.settings.trayCount + 1).coerceIn(1, 12)
                        while (Y.pens.size < n) Y.pens.add(Pen("pen", "ball", PK.ORIG[Y.pens.size % 5], 0.8f, 1f))
                        Y.settings.trayCount = n; Y.saveSettings(); Y.savePens(); ed.rev++
                    }
                }, contentAlignment = Alignment.Center) { Ic("plus", size = 14.dp, color = T.snow, stroke = 2f) }
            }
        }
        Line("필압 민감도") { MiniSeg(Y.settings.pressure, listOf("low" to "낮음", "mid" to "보통", "high" to "높음")) { Y.settings.pressure = it; Y.saveSettings() } }
        Line("손바닥 무시 감도") { MiniSeg(Y.settings.palm, listOf("low" to "약", "mid" to "중", "high" to "강")) { Y.settings.palm = it; Y.saveSettings() } }
        Line("필기 보정") { Switch(Y.settings.snap) { Y.settings.snap = it; Y.saveSettings() } }
        Line("손가락으로 쓰기") { Switch(Y.settings.finger) { Y.settings.finger = it; Y.saveSettings() } }
        if (Y.settings.dock != "top") { Sep(); kr.yeobaek.note.ui.PItem("도구 막대를 위에 붙이기", "up") { Y.settings.dock = "top"; Y.saveSettings(); Overlay.closePop(); ed.rev++ } }
    }
}

/** Apple 세그먼트 (작게) */
@Composable
fun <V> MiniSeg(value: V, opts: List<Pair<V, String>>, onPick: (V) -> Unit) {
    Row(Modifier.height(32.dp).background(Color(0x1F767680), RoundedCornerShape(16.dp)).padding(2.dp)) {
        opts.forEach { (v, s) ->
            val on = v == value
            Box(Modifier.fillMaxHeight().width(48.dp).then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(14.dp)).background(if (Y.isDark()) Color(0xFF636366) else Color.White, RoundedCornerShape(14.dp)) else Modifier)
                .pointerInput(v) { detectTapGestures { Y.haptic(6); onPick(v) } }, contentAlignment = Alignment.Center) {
                BasicText(s, style = txt(13.sp, if (on) 600 else 500, T.snow))
            }
        }
    }
}

/* =============== 지우개 창 =============== */
fun eraserPop(r: Rect) {
    Overlay.popover("eraser", r, 300.dp, "center") {
        Y.penRev
        val e = Y.eraser
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("크기", Modifier.weight(1f), style = txt(13.sp, 600, T.ink2))
            BasicText("%.1fmm".format(e.mm), style = txt(13.sp, 600, T.snow))
        }
        FineSlider(e.mm, 1f, 20f, Modifier.padding(horizontal = 12.dp)) { e.mm = (it * 2).roundToInt() / 2f; Y.savePens() }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
            MiniSegWide(e.partial, listOf(false to "획 지우기", true to "부분 지우기")) { e.partial = it; Y.savePens() }
        }
    }
}

@Composable
fun <V> MiniSegWide(value: V, opts: List<Pair<V, String>>, onPick: (V) -> Unit) {
    Row(Modifier.fillMaxWidth().height(32.dp).background(Color(0x1F767680), RoundedCornerShape(16.dp)).padding(2.dp)) {
        opts.forEach { (v, s) ->
            val on = v == value
            Box(Modifier.fillMaxHeight().weight(1f).then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(14.dp)).background(if (Y.isDark()) Color(0xFF636366) else Color.White, RoundedCornerShape(14.dp)) else Modifier)
                .pointerInput(v) { detectTapGestures { Y.haptic(6); onPick(v) } }, contentAlignment = Alignment.Center) {
                BasicText(s, style = txt(13.sp, if (on) 600 else 500, T.snow))
            }
        }
    }
}
