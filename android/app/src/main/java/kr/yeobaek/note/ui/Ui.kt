package kr.yeobaek.note.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.yeobaek.note.Y
import kotlin.math.roundToInt

/* =============== 아이콘 =============== */
private val pathCache = HashMap<String, Path>()
private fun iconPath(name: String): Path = pathCache.getOrPut(name) { PathParser().parsePathString(IconData.map[name] ?: "").toPath() }

@Composable
fun Ic(name: String, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = T.ink2, stroke: Float = 1.75f, fill: Boolean = false) {
    Canvas(modifier.size(size)) {
        // Apple SF Symbols가 있으면 그것으로 (굵기: 선 두께 1.75 → 보통, 2 이상 → 중간)
        if (Sym.draw(this, if (fill && Sym.map.containsKey(name + "fill")) name + "fill" else name, color, if (stroke >= 2f) 510 else 400)) return@Canvas
        val k = this.size.width / 24f
        scale(k, k, Offset.Zero) {
            val p = iconPath(name)
            if (fill) drawPath(p, color)
            drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/* =============== 겹쳐 뜨는 것들: 메뉴(팝오버), 창, 토스트, 이름표 =============== */
class PopSpec(val key: Any, val anchor: Rect, val width: Dp?, val align: String, val owner: String?, val onClose: (() -> Unit)?, val content: @Composable ColumnScope.() -> Unit)
class ModalSpec(val id: Int, val page: Boolean, val width: Dp, val dismissable: Boolean, val onClose: (() -> Unit)?, val content: @Composable (close: () -> Unit) -> Unit, val bare: Boolean = false)
class ToastSpec(val id: Long, val msg: String, val action: String?, val onAction: (() -> Unit)?, val ms: Long)
class TipSpec(val id: Long, val text: String, val anchor: Rect)

object Overlay {
    var pop by mutableStateOf<PopSpec?>(null)
    val modals = mutableStateListOf<ModalSpec>()
    var toast by mutableStateOf<ToastSpec?>(null)
    var tip by mutableStateOf<TipSpec?>(null)
    private var mid = 0

    /** 같은 버튼을 다시 누르면 닫기만 (켜고 끄기) */
    fun popover(key: Any, anchor: Rect, width: Dp? = null, align: String = "center", owner: String? = null, onClose: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
        if (pop?.key == key) { closePop(); return }
        closePop()
        pop = PopSpec(key, anchor, width, align, owner, onClose, content)
    }
    fun closePop(): Boolean { val p = pop ?: return false; pop = null; p.onClose?.invoke(); return true }

    fun modal(page: Boolean = false, width: Dp = 460.dp, dismissable: Boolean = true, onClose: (() -> Unit)? = null, bare: Boolean = false, content: @Composable (close: () -> Unit) -> Unit): () -> Unit {
        closePop()
        val m = ModalSpec(++mid, page, width, dismissable, onClose, content, bare); modals.add(m)
        return { closeModal(m) }
    }
    fun closeModal(m: ModalSpec) { if (modals.remove(m)) m.onClose?.invoke() }
    fun closeTop(): Boolean { val m = modals.lastOrNull() ?: return false; closeModal(m); return true }

    fun toast(msg: String, action: String? = null, ms: Long = 2600, onAction: (() -> Unit)? = null) { toast = ToastSpec(System.nanoTime(), msg, action, onAction, ms) }

    fun confirm(title: String, ok: String, danger: Boolean = false, cancel: String = "취소", text: String? = null, then: (Boolean) -> Unit) {
        var done = false
        lateinit var close: () -> Unit
        close = modal(width = 380.dp, onClose = { if (!done) { done = true; then(false) } }) { cl ->
            Column {
                BasicText(title, style = txt(17.sp, 600, T.snow))
                if (text != null) { Spacer(Modifier.size(6.dp)); BasicText(text, style = txt(14.sp, 400, T.mist)) }
                Spacer(Modifier.size(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Btn(cancel, "ghost") { done = true; cl(); then(false) }
                    Spacer(Modifier.width(4.dp))
                    Btn(ok, if (danger) "danger" else "primary") { done = true; cl(); then(true) }
                }
            }
        }
    }
}

/** 화면 맨 위에 겹쳐 그리는 곳 */
@Composable
fun OverlayHost() {
    Box(Modifier.fillMaxSize()) {
        Overlay.pop?.let { PopoverBox(it) }
        Overlay.modals.forEach { m -> key(m.id) { ModalBox(m) } }
        Overlay.toast?.let { ToastBox(it) }
        Overlay.tip?.let { TipBox(it) }
    }
}

@Composable private fun key(k: Any, c: @Composable () -> Unit) = androidx.compose.runtime.key(k) { c() }

@Composable
private fun PopoverBox(spec: PopSpec) {
    val d = LocalDensity.current
    val sb = androidx.compose.foundation.layout.WindowInsets.systemBars
    val insT = sb.getTop(d); val insB = sb.getBottom(d)
    var size by remember(spec) { mutableStateOf(IntSize.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val a = remember(spec) { Animatable(0f) }
    val opened = remember(spec) { System.currentTimeMillis() }
    LaunchedEffect(spec) { a.animateTo(1f, tween(320, easing = Spring)) }
    Box(Modifier.fillMaxSize().onSizeChanged { screen = it }
        .pointerInput(spec) {
            // 바깥(연 버튼 포함)을 누르면 닫히기만 함 → 같은 버튼을 다시 누르면 닫힘
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = e.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    val r = Rect(popPos(spec, size, screen, d, insT, insB).toOffset(), androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
                    // S Pen으로 톡 하면 닿음이 두 번 잡혀 열리자마자 닫히던 것: 연 직후 같은 버튼에 다시 닿은 건 무시
                    if (System.currentTimeMillis() - opened < 300 && spec.anchor.inflate(12f).contains(ch.position)) { ch.consume(); continue }
                    if (!r.contains(ch.position)) { ch.consume(); Overlay.closePop() }
                }
            }
        }) {
        val pos = popPos(spec, size, screen, d, insT, insB)
        val ox = if (size.width > 0) ((spec.anchor.center.x - pos.x) / size.width).coerceIn(0f, 1f) else .5f
        val up = pos.y < spec.anchor.top
        Column(Modifier.offset { pos }.onSizeChanged { size = it }
            .then(if (spec.width != null) Modifier.width(spec.width) else Modifier.widthIn(min = 160.dp, max = 340.dp))
            .graphicsLayer { val s = .9f + .1f * a.value; scaleX = s; scaleY = s; alpha = (a.value * 6).coerceAtMost(1f); transformOrigin = TransformOrigin(ox, if (up) 1f else 0f) }
            .shadow(28.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .background(if (T.dark) Color(0xFF2C2C2E) else Color(0xFFFAFAFA), RoundedCornerShape(26.dp)).border(.5.dp, T.hair, RoundedCornerShape(26.dp))
            .heightIn(max = with(d) { ((screen.height - insT - insB) * .8f).toDp() }.coerceAtLeast(120.dp))
            .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 12.dp)) { spec.content(this) }
    }
}

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())
private fun popPos(spec: PopSpec, size: IntSize, screen: IntSize, d: androidx.compose.ui.unit.Density, insT: Int = 0, insB: Int = 0): IntOffset {
    val m = with(d) { 8.dp.toPx() }
    val r = spec.anchor; val pw = size.width; val ph = size.height
    var left = when (spec.align) { "left" -> r.left; "right" -> r.right - pw; else -> r.center.x - pw / 2f }
    left = left.coerceIn(m, (screen.width - pw - m).coerceAtLeast(m))
    // 상태 표시줄·내비게이션 바는 피해서
    val lo = insT + m; val hi = screen.height - insB - m
    var top = r.bottom + m
    if (top + ph > hi && r.top - ph - m > lo) top = r.top - ph - m
    top = top.coerceIn(lo, (hi - ph).coerceAtLeast(lo))
    return IntOffset(left.roundToInt(), top.roundToInt())
}

@Composable
private fun ModalBox(m: ModalSpec) {
    val a = remember(m) { Animatable(0f) }
    LaunchedEffect(m) { a.animateTo(1f, tween(340, easing = Out)) }
    val close = { Overlay.closeModal(m) }
    if (m.page) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (a.value * 3).coerceAtMost(1f); translationX = (1 - a.value) * 24.dp.toPx() }
            .background(T.bg).pointerInput(Unit) { detectTapGestures { } }) { m.content(close) }
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f * a.value))
        .pointerInput(m) { detectTapGestures { if (m.dismissable) close() } }, contentAlignment = Alignment.Center) {
        Box(Modifier.padding(16.dp).widthIn(max = m.width).fillMaxWidth()
            .graphicsLayer { translationY = (1 - a.value) * 14.dp.toPx(); alpha = (a.value * 3).coerceAtMost(1f) }
            .shadow(30.dp, RoundedCornerShape(34.dp), ambientColor = Color(0x2E000000), spotColor = Color(0x2E000000))
            .background(if (m.bare) (if (T.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)) else T.raise, RoundedCornerShape(34.dp)).pointerInput(Unit) { detectTapGestures { } }
            .padding(if (m.bare) 0.dp else if (m.width <= 380.dp) 24.dp else 28.dp)) { m.content(close) }
    }
}

@Composable
private fun BoxScope.ToastBox(t: ToastSpec) {
    val a = remember(t.id) { Animatable(0f) }
    LaunchedEffect(t.id) {
        a.animateTo(1f, tween(300, easing = Spring)); delay(t.ms); a.animateTo(0f, tween(160)); if (Overlay.toast?.id == t.id) Overlay.toast = null
    }
    Row(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars).padding(bottom = 28.dp)
        .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1 - a.value) * 10.dp.toPx() }
        .background(T.floatBg, CircleShape).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(t.msg, style = txt(14.sp, 400, T.floatFg))
        if (t.action != null) {
            Spacer(Modifier.width(14.dp))
            BasicText(t.action, style = txt(14.sp, 600, T.floatFg).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                modifier = Modifier.pointerInput(t.id) { detectTapGestures { Overlay.toast = null; t.onAction?.invoke() } })
        }
    }
}

@Composable
private fun TipBox(t: TipSpec) {
    val d = LocalDensity.current
    var size by remember(t.id) { mutableStateOf(IntSize.Zero) }
    val a = remember(t.id) { Animatable(0f) }
    LaunchedEffect(t.id) { a.animateTo(1f, tween(300, easing = Spring)); delay(1400); a.animateTo(0f, tween(160)); if (Overlay.tip?.id == t.id) Overlay.tip = null }
    val below = t.anchor.top < with(d) { 60.dp.toPx() }
    val x = (t.anchor.center.x - size.width / 2f).coerceAtLeast(with(d) { 8.dp.toPx() })
    val y = if (below) t.anchor.bottom + with(d) { 8.dp.toPx() } else t.anchor.top - size.height - with(d) { 8.dp.toPx() }
    Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.onSizeChanged { size = it }
        .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); val s = .94f + .06f * a.value; scaleX = s; scaleY = s; translationY = (1 - a.value) * (if (below) -6 else 6).dp.toPx() }
        .shadow(10.dp, RoundedCornerShape(9.dp), ambientColor = Color(0x24212529), spotColor = Color(0x24212529))
        .background(T.raise.copy(alpha = .94f), RoundedCornerShape(9.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
        BasicText(t.text, style = txt(13.sp, 500, T.snow))
    }
}

/* =============== 버튼들 =============== */

/**
 * 아이콘 버튼: 손가락에 살짝 눌리고(0.92), 켜진 상태는 진한 색 + 아래 작은 점.
 * 0.45초 꾹 누르면 이름표만 보여 주고 실행하지 않음.
 */
@Composable
fun IconBtn(icon: String, label: String, modifier: Modifier = Modifier, pressed: Boolean = false, enabled: Boolean = true,
            size: Dp = 40.dp, iconSize: Dp = 22.dp, color: Color? = null, dot: Boolean = true, onBounds: ((Rect) -> Unit)? = null, onClick: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) .92f else 1f, tween(if (down) 80 else 200, easing = Out), label = "press")
    Box(modifier.size(size).semantics { contentDescription = label }
        .onGloballyPositioned { bounds = it.boundsInRoot(); onBounds?.invoke(bounds) }
        .scale(s).alpha(if (enabled) 1f else .35f)
        .pointerInput(enabled, label) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { down = true; tryAwaitRelease(); down = false },
                onLongPress = { Y.haptic(6); Overlay.tip = TipSpec(System.nanoTime(), label, bounds) },
                onTap = { onClick(bounds) },
            )
        }, contentAlignment = Alignment.Center) {
        Ic(icon, size = iconSize, color = color ?: if (pressed) T.snow else T.ink2)
        if (pressed && dot) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(4.dp).background(T.acc, CircleShape))
    }
}

/** 둥근 캡슐 버튼: 주 버튼은 짙은 회색, 보조는 글자 버튼 */
@Composable
fun Btn(text: String, kind: String = "primary", modifier: Modifier = Modifier, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) .92f else 1f, tween(if (down) 80 else 200, easing = Out), label = "b")
    val bg = if (kind == "primary") T.primary else Color.Transparent
    val fg = when (kind) { "primary" -> T.onPrimary; "danger" -> T.danger; else -> T.ink2 }
    Box(modifier.heightIn(min = 40.dp).scale(s).clip(CircleShape).background(bg)
        .pointerInput(text) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() }) }
        .padding(horizontal = if (kind == "primary") 20.dp else 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = txt(15.sp, if (kind == "ghost") 500 else 600, fg))
    }
}

/** 메뉴 한 줄: 고른 것은 진한 글자 + 오른쪽 작은 점 */
@Composable
fun PItem(text: String, icon: String? = null, on: Boolean = false, enabled: Boolean = true, color: Color? = null, indent: Dp = 0.dp, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).background(if (down) T.fill else Color.Transparent)
        .alpha(if (enabled) 1f else .35f)
        .pointerInput(enabled, text) { if (enabled) detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() }) }
        .padding(start = 10.dp + indent, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Ic(icon, size = 20.dp, color = color ?: T.ink2); Spacer(Modifier.width(10.dp)) }
        BasicText(text, Modifier.weight(1f), style = txt(15.sp, if (on) 600 else 400, color ?: T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (on) Box(Modifier.size(5.dp).background(T.acc, CircleShape))
    }
}

@Composable fun PTitle(t: String) = BasicText(t, Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 4.dp), style = txt(12.sp, 400, T.ink2))

/** 스위치: 손잡이가 미끄러져 딸깍 */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = Spring), label = "sw")
    Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(if (on) T.acc else T.switchOff)
        .pointerInput(on) { detectTapGestures { Y.haptic(6); onChange(!on) } }) {
        Box(Modifier.offset(x = 2.dp + 16.dp * x, y = 2.dp).size(20.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** 설정 선택지: 글자 + 고른 것 아래 작은 점 */
@Composable
fun <V> Seg(value: V, opts: List<Pair<V, String>>, onPick: (V) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        opts.forEach { (v, label) ->
            val on = v == value
            Box(Modifier.heightIn(min = 32.dp).pointerInput(v, on) { detectTapGestures { Y.haptic(6); onPick(v) } }, contentAlignment = Alignment.Center) {
                BasicText(label, style = txt(14.sp, if (on) 600 else 400, if (on) T.snow else T.ink2))
                if (on) Box(Modifier.align(Alignment.BottomCenter).size(4.dp).background(T.acc, CircleShape))
            }
        }
    }
}

/** 둥근 색 점 */
@Composable
fun Dot(c: Color, on: Boolean, size: Dp = 28.dp, onClick: () -> Unit) {
    val s by animateFloatAsState(if (on) 1f else .86f, tween(300, easing = Spring), label = "d")
    Box(Modifier.size(size).pointerInput(c) { detectTapGestures { Y.haptic(6); onClick() } }, contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(size).border(1.5.dp, T.acc, CircleShape))
        Box(Modifier.size(size - 8.dp).scale(s).background(c, CircleShape).border(.5.dp, T.hair, CircleShape))
    }
}

fun hapticTap() = Y.haptic(8)
