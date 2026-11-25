package kr.yeobaek.note.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.yeobaek.note.Nav
import kr.yeobaek.note.OpenDoc
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.quiz.Quiz
import kr.yeobaek.note.quiz.QuizLayout
import kr.yeobaek.note.ui.Ic
import kr.yeobaek.note.ui.Out
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.PItem
import kr.yeobaek.note.ui.Spring
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.TipSpec
import kr.yeobaek.note.ui.txt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* Apple iOS 27 UI 키트 값: 유리 버튼 44, 위 도구 막대 높이 54, 탭 바 캡슐 44(탭 36), 파랑 #0088FF */
private val KIT_BLUE = Color(0xFF0088FF)

/** Liquid Glass: 반투명 채움 + 0.5pt 테두리 + 옅은 그림자 (설정의 투명도 %) */
@Composable
fun glassFill(): Color {
    Y.setRev
    val pct = if (Y.settings.glassPct >= 0) Y.settings.glassPct else when (Y.settings.glass) { "clear" -> 20; "solid" -> 100; else -> 60 }
    val a = .35f + .65f * pct / 100f
    return if (Y.isDark()) Color(0xFF2C2C2E).copy(alpha = a) else Color(0xFFFAFAFA).copy(alpha = a)
}
@Composable
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier =
    this.shadow(8.dp, shape, ambientColor = Color(0x18000000), spotColor = Color(0x18000000))
        .background(glassFill(), shape)
        .border(.5.dp, if (Y.isDark()) Color(0x26FFFFFF) else Color(0x29000000), shape)

/** 유리 동그라미 버튼 44 */
@Composable
fun GlassBtn(icon: String, label: String, modifier: Modifier = Modifier, tint: Color? = null, onClick: (Rect) -> Unit) {
    var b by remember { mutableStateOf(Rect.Zero) }
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) .9f else 1f, tween(if (down) 80 else 220, easing = Out), label = "g")
    Box(modifier.size(44.dp).onGloballyPositioned { b = it.boundsInRoot() }.graphicsLayer { scaleX = s; scaleY = s }.glass()
        .pointerInput(label) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onLongPress = { Overlay.tip = TipSpec(System.nanoTime(), label, b) }, onTap = { onClick(b) }) },
        contentAlignment = Alignment.Center) { Ic(icon, size = 22.dp, color = tint ?: T.snow) }
}

/* =============== 위쪽: 탭 + 모드 + 펜 막대 + 페이지 눈금 =============== */
@Composable
fun Chrome(ed: Ed, open: OpenDoc, statusTop: Dp, onExport: (Doc, DocView) -> Unit) {
    val d = LocalDensity.current
    val doc = ed.view?.doc ?: open.doc
    val hide = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ed.collapsed) { hide.animateTo(if (ed.collapsed) 1f else 0f, tween(380, easing = Out)) }
    var h by remember { mutableFloatStateOf(0f) }
    var title by remember(open, doc) { mutableStateOf(doc.title) }
    Y.setRev; ed.rev
    Column(Modifier.fillMaxWidth().padding(top = statusTop + 6.dp, start = 12.dp, end = 12.dp)
        .onSizeChanged { h = it.height.toFloat(); ed.chromeH = it.height + with(d) { (statusTop + 6.dp).toPx() } }
        .graphicsLayer { translationY = -hide.value * (h + with(d) { (statusTop + 24.dp).toPx() }) + drag }
        .pointerInput(Unit) {
            // 빈 곳을 잡고 위로 끌면 블라인드처럼 숨음
            detectVerticalDragGestures(
                onDragEnd = { if (drag < -36.dp.toPx()) { ed.collapsed = true; Y.haptic(8) }; drag = 0f },
                onDragCancel = { drag = 0f },
            ) { _, dy -> val nd = drag + dy; drag = if (nd < 0) nd else nd * .25f }
        }) {
        // 1줄: 서재로, 탭 바(× 닫기, + 새 노트), 공유, ⋯
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBtn("back", "서재") { Nav.closeEditor() }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { TabBar(title) }
            Spacer(Modifier.width(10.dp))
            GlassBtn("share", "내보내기") { ed.view?.let { v -> v.saveNow(); onExport(doc, v) } }
            Spacer(Modifier.width(10.dp))
            GlassBtn("more", "더보기") { r -> moreMenu(ed, r, doc, onExport) { renameModal(title) { t -> title = t; doc.title = t; ed.view?.queueSave(); ed.view?.saveNow() } } }
        }
        Spacer(Modifier.height(10.dp))
        // 2줄: 모드 전환(그리기 / 파일) + 펜 막대 또는 파일 도구
        Row(Modifier.fillMaxWidth().height(if (ed.tab == "draw") BAR_H.dp else 72.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeSwitch(ed)
            Spacer(Modifier.width(10.dp))
            if (ed.tab == "draw") {
                if (Y.settings.dock == "top" && ed.pkFloat == null) AttachedPicker(ed, Modifier.weight(1f))
                else DockSlot(Modifier.weight(1f))
            } else FileBar(ed, doc, Modifier.weight(1f))
        }
        // 올가미로 고른 것: 복사, 색, 삭제
        ed.view?.let { v -> if (ed.tab == "draw" && v.sel != null) SelBar(ed, v) }
        // 페이지 눈금 (설정에서 끄고 켬)
        if (Y.settings.scale) ed.view?.let { v -> PageScale(ed, v) }
    }
    // 숨겼을 때: 오른쪽 위 작은 유리 ⋯ (누르거나 아래로 끌면 내려옴)
    if (hide.value > .5f) {
        var dy by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxWidth().padding(top = statusTop + 10.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
            Box(Modifier.offset { IntOffset(0, dy.roundToInt()) }.size(44.dp).glass()
                .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (dy > 30.dp.toPx()) { ed.collapsed = false; Y.haptic(8) }; dy = 0f }) { _, a -> dy = (dy + a).coerceIn(0f, 80.dp.toPx()) } }
                .pointerInput(Unit) { detectTapGestures { ed.collapsed = false; Y.haptic(8) } },
                contentAlignment = Alignment.Center) { Ic("more", size = 20.dp, color = T.snow) }
        }
    }
}

/** 그리기 / 파일 모드: 세로 세그먼트 (Segmented Controls, 고른 칸 흰 캡슐) */
@Composable
private fun ModeSwitch(ed: Ed) {
    val dark = Y.isDark()
    val tall = ed.tab == "draw"
    Column(Modifier.width(56.dp).fillMaxHeight().glass(RoundedCornerShape(28.dp)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Triple("draw", "pencil", "그리기"), Triple("file", "doc", "파일")).forEach { (id, ic, t) ->
            val on = ed.tab == id
            val a by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = Spring), label = "m")
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(if (dark) Color(0xFF636366).copy(alpha = a) else Color.White.copy(alpha = a), RoundedCornerShape(24.dp))
                .pointerInput(id) {
                    detectTapGestures {
                        if (ed.tab != id) { ed.tab = id; Overlay.closePop(); Y.haptic(8); if (id != "draw" && Y.mode == "lasso") ed.view?.clearSel() }
                    }
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Ic(ic, size = if (tall) 21.dp else 17.dp, color = if (on) KIT_BLUE else T.ink2)
                if (tall) { Spacer(Modifier.height(2.dp)); BasicText(t, style = txt(10.sp, 600, if (on) KIT_BLUE else T.ink2)) }
            }
        }
    }
}

/* =============== 펜 막대: 탭 아래에 붙어 있다가 떼어 내기 =============== */
/** 막대 끄는 상태 (화면 기준 px) */
private object PkDrag {
    var start = Offset.Zero        // 끌기 시작 때 막대 왼쪽 위
    var off = Offset.Zero
    var detached = false
    var size = Size.Zero
}

@Composable
private fun AttachedPicker(ed: Ed, modifier: Modifier) {
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val breakPx = with(d) { 54.dp.toPx() }
    val dragger = remember(ed) {
        object : PickerDrag {
            override fun start() { PkDrag.start = bounds.topLeft; PkDrag.off = Offset.Zero; PkDrag.detached = false; PkDrag.size = bounds.size }
            override fun drag(dl: Offset) {
                PkDrag.off += dl
                if (!PkDrag.detached) {
                    // 자석처럼 버팀: 손의 28%만 따라오다가 일정 거리를 넘으면 톡 떨어짐
                    if (PkDrag.off.getDistance() > breakPx) { PkDrag.detached = true; Y.haptic(14); ed.pkFloat = PkDrag.start + PkDrag.off; scope.launch { pull.snapTo(Offset.Zero) } }
                    else scope.launch { pull.snapTo(PkDrag.off * .28f) }
                } else ed.pkFloat = PkDrag.start + PkDrag.off
            }
            override fun end() {
                if (PkDrag.detached) dropPicker(ed, d) else scope.launch { pull.animateTo(Offset.Zero, spring(.55f, 600f)) }
            }
        }
    }
    ToolPicker(ed, modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.graphicsLayer { translationX = pull.value.x; translationY = pull.value.y }, dragger)
}

/** 떼어 낸 막대가 원래 있던 자리 (다시 붙일 수 있는 자석 자리) */
@Composable
private fun DockSlot(modifier: Modifier) {
    Box(modifier.fillMaxHeight().padding(vertical = 6.dp).border(1.dp, T.hair, RoundedCornerShape(46.dp)))
}

/** 놓은 자리로: 위(붙이기), 왼쪽·오른쪽·아래 가장자리(자석), 아니면 그 자리에 떠 있음 */
private fun dropPicker(ed: Ed, d: androidx.compose.ui.unit.Density) {
    val p = ed.pkFloat ?: return
    val scr = ed.screen
    val m = with(d) { 70.dp.toPx() }
    val w = PkDrag.size.width; val hgt = PkDrag.size.height
    val cx = p.x + w / 2; val top = p.y; val bottom = p.y + hgt
    val chromeBottom = PkDrag.start.y + hgt
    val s = Y.settings
    s.dock = when {
        top < chromeBottom - hgt * .2f && abs(cx - (PkDrag.start.x + w / 2)) < scr.width * .4f -> "top"
        cx < m * 1.6f || p.x < m * .4f -> "left"
        cx > scr.width - m * 1.6f || p.x + w > scr.width - m * .4f -> "right"
        bottom > scr.height - m -> "bottom"
        else -> "float"
    }
    if (s.dock == "float") { s.dockX = with(d) { p.x.toDp().value }; s.dockY = with(d) { p.y.toDp().value } }
    Y.haptic(12); Y.saveSettings(); ed.pkFloat = null; ed.rev++
}

/** 떠 있거나 가장자리에 붙은 펜 막대 (편집 화면 맨 위 층) */
@Composable
fun FloatingPicker(ed: Ed) {
    if (ed.tab != "draw") return
    val dock = Y.settings.dock
    val fl = ed.pkFloat
    if (dock == "top" && fl == null) return
    val d = LocalDensity.current
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat(); val sh = constraints.maxHeight.toFloat()
        val wDp = minOf(maxWidth - 40.dp, 680.dp); val mh = maxHeight
        val nav = WindowInsets.navigationBars.getBottom(d).toFloat()
        val vertical = fl == null && (dock == "left" || dock == "right")
        val dragger = remember(ed) {
            object : PickerDrag {
                var origin = Offset.Zero
                override fun start() { origin = ed.pkFloat ?: currentPos(ed, size, ed.screen.width, ed.screen.height, nav, d); PkDrag.start = Offset(origin.x, -10000f); PkDrag.off = Offset.Zero; PkDrag.detached = true; PkDrag.size = Size(size.width.toFloat(), size.height.toFloat()); ed.pkFloat = origin }
                override fun drag(dl: Offset) { PkDrag.off += dl; ed.pkFloat = origin + PkDrag.off }
                override fun end() {
                    val p = ed.pkFloat ?: return
                    // 위로 가져가면 다시 탭에 붙음
                    if (p.y < ed.chromeH - with(d) { 40.dp.toPx() }) { Y.settings.dock = "top"; Y.saveSettings(); ed.pkFloat = null; Y.haptic(12); ed.rev++; return }
                    PkDrag.start = Offset(p.x, -10000f)
                    dropPicker(ed, d)
                }
            }
        }
        val pos = fl ?: currentPos(ed, size, sw, sh, nav, d)
        Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }.zIndex(5f)
            .onSizeChanged { size = it }
            .then(if (vertical) Modifier.rotated(if (dock == "left") 90f else -90f) else Modifier)) {
            ToolPicker(ed, Modifier.width(if (vertical) minOf(mh - with(d) { ed.chromeH.toDp() } - 60.dp, 680.dp) else wDp), dragger)
        }
    }
}

private fun currentPos(ed: Ed, size: androidx.compose.ui.unit.IntSize, sw: Float, sh: Float, nav: Float, d: androidx.compose.ui.unit.Density): Offset {
    val m = with(d) { 12.dp.toPx() }
    return when (Y.settings.dock) {
        "bottom" -> Offset((sw - size.width) / 2, sh - nav - size.height - m * 2)
        "left" -> Offset(m, ed.chromeH + (sh - ed.chromeH - size.height) / 2)
        "right" -> Offset(sw - size.width - m, ed.chromeH + (sh - ed.chromeH - size.height) / 2)
        else -> Offset(with(d) { Y.settings.dockX.dp.toPx() }.coerceIn(0f, max(0f, sw - size.width)), with(d) { Y.settings.dockY.dp.toPx() }.coerceIn(ed.chromeH * .5f, max(ed.chromeH, sh - size.height)))
    }
}

/** 90도 돌려 세로로 (자리도 가로·세로 바꿈) */
fun Modifier.rotated(deg: Float) = this.layout { m, c ->
    val p = m.measure(Constraints(maxWidth = c.maxHeight, maxHeight = c.maxWidth))
    layout(p.height, p.width) { p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = deg } }
}

/* =============== 탭 바: 탭마다 ×, 옆으로 끌어 순서, 아래로 뜯어 닫기 =============== */
@Composable
private fun TabBar(curTitle: String) {
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val slide = remember { HashMap<OpenDoc, Animatable<Float, AnimationVector1D>>() }
    var dragging by remember { mutableStateOf<OpenDoc?>(null) }
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val tearPx = with(d) { 40.dp.toPx() }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp).glass(RoundedCornerShape(22.dp)).padding(4.dp)) {
        val n = Nav.tabs.size.coerceAtLeast(1)
        val plusW = 44.dp
        val tabW = ((maxWidth - plusW) / n).coerceIn(120.dp, 230.dp)
        val wPx = with(d) { tabW.toPx() }
        Row(Modifier.fillMaxSize().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            Nav.tabs.forEachIndexed { idx, t ->
                androidx.compose.runtime.key(t) {
                    val on = Nav.cur === t
                    val me = dragging === t
                    val sl = slide.getOrPut(t) { Animatable(0f) }
                    Box(Modifier.width(tabW).height(36.dp).zIndex(if (me) 2f else 0f)
                        .graphicsLayer {
                            translationX = if (me) dx.value else sl.value
                            translationY = if (me) dy.value else 0f
                            alpha = if (me) 1f - (dy.value / (tearPx * 2.2f)).coerceIn(0f, .6f) else 1f
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) Color(0x1F767680) else Color.Transparent)
                        .pointerInput(t) { detectTapGestures(onTap = { Nav.switchTo(t) }) }
                        .pointerInput(t) {
                            detectDragGestures(
                                onDragStart = { dragging = t; scope.launch { dx.snapTo(0f); dy.snapTo(0f) }; Y.haptic(8) },
                                onDragEnd = {
                                    if (dy.value > tearPx) { Y.haptic(12); dragging = null; Nav.closeTab(t); scope.launch { dx.snapTo(0f); dy.snapTo(0f) } }
                                    else scope.launch {
                                        launch { dy.animateTo(0f, spring(.7f, 500f)) }
                                        dx.animateTo(0f, spring(.75f, 500f)); if (dragging === t) dragging = null
                                    }
                                },
                                onDragCancel = { scope.launch { launch { dy.animateTo(0f) }; dx.animateTo(0f); dragging = null } },
                            ) { ch, a ->
                                ch.consume()
                                scope.launch {
                                    dy.snapTo((dy.value + a.y).let { if (it < 0) it * .2f else it })
                                    var x = dx.value + a.x
                                    val i = Nav.tabs.indexOf(t)
                                    if (x > wPx / 2f && i < Nav.tabs.size - 1) {
                                        val o = Nav.tabs[i + 1]; Nav.tabs.removeAt(i); Nav.tabs.add(i + 1, t); x -= wPx; Y.haptic(6)
                                        val a2 = slide.getOrPut(o) { Animatable(0f) }; launch { a2.snapTo(wPx); a2.animateTo(0f, tween(240, easing = Out)) }
                                    } else if (x < -wPx / 2f && i > 0) {
                                        val o = Nav.tabs[i - 1]; Nav.tabs.removeAt(i); Nav.tabs.add(i - 1, t); x += wPx; Y.haptic(6)
                                        val a2 = slide.getOrPut(o) { Animatable(0f) }; launch { a2.snapTo(-wPx); a2.animateTo(0f, tween(240, easing = Out)) }
                                    }
                                    dx.snapTo(x)
                                }
                            }
                        }) {
                        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (t.doc.type) { "pdf" -> "pdf"; "quiz" -> "quiz"; else -> "notes" }
                            Ic(icon, size = 15.dp, color = if (on) KIT_BLUE else T.ink2)
                            Spacer(Modifier.width(6.dp))
                            BasicText(Y.clean(if (on) curTitle else t.doc.title).ifBlank { "제목 없음" }, Modifier.weight(1f),
                                style = txt(15.sp, if (on) 600 else 500, if (on) KIT_BLUE else T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.size(28.dp).clip(CircleShape).pointerInput(t) { detectTapGestures { Y.haptic(8); Nav.closeTab(t) } },
                                contentAlignment = Alignment.Center) { Ic("close", size = 12.dp, color = T.ink2, stroke = 2f) }
                        }
                    }
                }
            }
            Box(Modifier.size(44.dp, 36.dp).clip(RoundedCornerShape(18.dp)).pointerInput(Unit) { detectTapGestures { kr.yeobaek.note.library.newNoteModal() } },
                contentAlignment = Alignment.Center) { Ic("plus", size = 20.dp, color = T.snow) }
        }
    }
}

/* =============== 파일 모드 줄 =============== */
@Composable
private fun FileBar(ed: Ed, doc: Doc, modifier: Modifier) {
    ed.rev; Y.setRev
    val v = ed.view ?: return
    Row(modifier.fillMaxHeight().glass(RoundedCornerShape(36.dp)).padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        @Composable fun A(icon: String, label: String, on: Boolean = false, color: Color? = null, act: (Rect) -> Unit) = FileAct(icon, label, on, color, act)
        A("sidebar", "페이지", ed.sidebar) { ed.sidebar = !ed.sidebar }
        when (doc.type) {
            "quiz" -> {
                val qs = doc.qs!!
                A("check", "전체 채점") {
                    val (n, sc) = Quiz.gradeAll(doc)
                    v.quizRepaint(); v.animateScore()
                    doc.quiz!!.problems.indices.forEach { if (qs.grade["$it"] != null) v.animate(it, listOf("mark")) }
                    if (n == 0 && sc.graded == 0) Overlay.toast("고른 보기가 없어요") else Overlay.toast("${sc.right} / ${sc.total}", ms = 2200)
                    ed.rev++
                }
                A("focus", "오답만 진하게", qs.focus) { Quiz.setFocus(doc, null); v.quizRepaint(); ed.rev++ }
                A("eye", "답 보기", qs.show) { qs.show = !qs.show; Quiz.saveState(doc); v.quizRepaint(); ed.rev++ }
                A("book", "해설") { r -> solutionsPop(r, doc, v) }
                A("answer", "정답표") { r -> answerSheet(r, doc, v) }
                A("retry", "오답만 추출") {
                    Nav.busy = true
                    Nav.scope.launch {
                        val meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Quiz.retry(doc) }
                        Nav.busy = false
                        if (meta == null) Overlay.toast("틀린 문제가 없어요") else { Nav.closeEditor(); delay(250); Nav.open(meta) }
                    }
                }
                A("reset", "전체 초기화", color = T.danger) {
                    Overlay.confirm("문제지를 처음 상태로 돌릴까요?", "초기화", danger = true) { ok -> if (ok) { Quiz.reset(doc); ed.card = null; v.quizRepaint(); ed.rev++ } }
                }
            }
            "note" -> {
                A("pageplus", "새 페이지") { v.addPage() }
                A("template", "템플릿") { r -> tplPop(r, v, v.pageIdx) { ed.rev++ } }
                A("grid4", "보기 방식") { r -> viewModePop(r, ed, v) }
                val has = ed.page in doc.bookmarks
                A(if (has) "bookmarkfill" else "bookmark", "북마크", has) { if (has) doc.bookmarks.remove(ed.page) else doc.bookmarks.add(ed.page); ed.rev++ }
            }
            else -> {
                A("list", "목차") { r -> tocPop(r, doc, v) }
                val has = ed.page in doc.bookmarks
                A(if (has) "bookmarkfill" else "bookmark", "북마크", has) { if (has) doc.bookmarks.remove(ed.page) else doc.bookmarks.add(ed.page); ed.rev++ }
                A("grid4", "보기 방식") { r -> viewModePop(r, ed, v) }
                if (doc.pdf?.canSearch == true) Box(Modifier.padding(horizontal = 6.dp)) { SearchBox(ed, v) }
            }
        }
        A("split", "화면 분할", ed.split != null) { r -> if (ed.split != null) SplitCtl.end() else splitPicker(r, v.doc) }
        A("fit", "맞춤", v.zoom == "fit") { v.setZoom("fit"); ed.rev++ }
        A("width", "너비", v.zoom == "width") { v.setZoom("width"); ed.rev++ }
    }
}

/** 파일 모드 버튼: 아이콘 위, 이름 아래 (메뉴의 빠른 동작처럼) */
@Composable
private fun FileAct(icon: String, label: String, on: Boolean, color: Color?, act: (Rect) -> Unit) {
    var b by remember { mutableStateOf(Rect.Zero) }
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) .92f else 1f, tween(if (down) 80 else 220, easing = Out), label = "f")
    val fg = color ?: if (on) KIT_BLUE else T.snow
    Column(Modifier.widthIn(min = 64.dp).height(60.dp).onGloballyPositioned { b = it.boundsInRoot() }.graphicsLayer { scaleX = s; scaleY = s }
        .clip(RoundedCornerShape(16.dp)).background(if (on) KIT_BLUE.copy(alpha = .12f) else Color.Transparent)
        .pointerInput(label) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { Y.haptic(6); act(b) }) }
        .padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Ic(icon, size = 22.dp, color = fg)
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = txt(12.sp, 600, fg), maxLines = 1)
    }
}

/** 해설 모아 보기 (긴 해설도 창 안에서 줄바꿈·스크롤) */
private fun solutionsPop(r: Rect, doc: Doc, v: DocView) {
    Overlay.popover("sols", r, 380.dp) {
        BasicText("해설", Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp), style = txt(15.sp, 600, T.snow))
        doc.quiz!!.problems.withIndex().filter { !it.value.isPassage }.forEach { (i, p) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pointerInput(i) { detectTapGestures { Overlay.closePop(); v.goToProblem(i) } }.padding(10.dp)) {
                val ans = if (p.type == "choice") QuizLayout.CIRC.getOrElse((p.answer.toIntOrNull() ?: 1) - 1) { p.answer } else p.answer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${p.no}", Modifier.width(30.dp), style = txt(14.sp, 700, KIT_BLUE))
                    BasicText("정답 $ans", style = txt(14.sp, 600, T.snow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (p.solution.isNotBlank()) { Spacer(Modifier.height(4.dp)); TexText(p.solution, 14f, T.ink2, 400, 1.55f, Modifier.padding(start = 30.dp)) }
            }
            Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(.5.dp).background(T.hair))
        }
    }
}

/** 페이지 보기 방식: 세로로 넘기기, 여러 쪽 한눈에 */
internal fun viewModePop(r: Rect, ed: Ed, v: DocView) {
    Overlay.popover("viewmode", r, 260.dp) {
        PItem("세로로 넘기기", "rows", on = !ed.overview) { Overlay.closePop(); ed.overview = false }
        PItem("여러 쪽 한눈에", "grid4", on = ed.overview) { Overlay.closePop(); ed.overview = true }
    }
}

/* =============== 올가미로 고른 것 =============== */
@Composable
private fun SelBar(ed: Ed, v: DocView) {
    Row(Modifier.padding(top = 8.dp).height(44.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun B(icon: String, label: String, c: Color = T.snow, f: (Rect) -> Unit) {
            var b by remember { mutableStateOf(Rect.Zero) }
            Row(Modifier.height(36.dp).onGloballyPositioned { b = it.boundsInRoot() }.clip(RoundedCornerShape(18.dp)).pointerInput(label) { detectTapGestures { f(b) } }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(icon, size = 18.dp, color = c); Spacer(Modifier.width(6.dp)); BasicText(label, style = txt(14.sp, 600, c))
            }
        }
        B("copy", "복사") { v.copySel() }
        B("palette", "색") { r -> colorsPop(r, ed) }
        B("trash", "삭제", T.danger) { v.deleteSel() }
    }
}

/* =============== 페이지 눈금: 쪽마다 눈금, 지금 쪽 표시, 3 / 12 =============== */
@Composable
private fun PageScale(ed: Ed, v: DocView) {
    val n = v.doc.pages.size.coerceAtLeast(1)
    val cur = ed.page.coerceIn(0, n - 1)
    val acc = KIT_BLUE
    val tick = if (Y.isDark()) Color(0x52FFFFFF) else Color(0x4D3C3C43)
    val pos by animateFloatAsState(cur.toFloat(), tween(320, easing = Out), label = "ps")
    var w by remember { mutableFloatStateOf(1f) }
    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).fillMaxHeight().onSizeChanged { w = it.width.toFloat() }
            .pointerInput(n) {
                // 눈금을 누르거나 끌면 그 쪽으로
                detectTapGestures { o -> v.scrollToPage(((o.x / size.width) * n).toInt().coerceIn(0, n - 1)) }
            }
            .pointerInput(n) { detectDragGestures { ch, _ -> ch.consume(); val i = ((ch.position.x / size.width) * n).toInt().coerceIn(0, n - 1); if (i != ed.page) v.scrollToPage(i, smooth = false) } }) {
            val step = size.width / n
            val every = if (n > 120) 10 else if (n > 50) 5 else 1
            val by = size.height - 3.dp.toPx()
            for (i in 0..n) {
                if (i % every != 0 && i != n) continue
                val x = (i * step).coerceIn(.5f, size.width - .5f)
                val long = i % (every * 5) == 0
                drawLine(tick, Offset(x, by - (if (long) 9.dp else 5.dp).toPx()), Offset(x, by), .8.dp.toPx())
            }
            drawLine(acc, Offset(pos * step + 1.dp.toPx(), by), Offset((pos + 1) * step - 1.dp.toPx(), by), 3.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(10.dp))
        BasicText("${cur + 1} / $n", style = txt(12.sp, 700, acc))
    }
}

/* =============== 더보기 메뉴 (9 더보기) =============== */
private fun moreMenu(ed: Ed, r: Rect, doc: Doc, onExport: (Doc, DocView) -> Unit, onRename: () -> Unit) {
    Overlay.popover("more", r, 280.dp, "right") {
        // 빠른 동작: 공유, 복제, 삭제
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            @Composable fun Q(icon: String, t: String, c: Color = T.snow, f: () -> Unit) {
                Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).pointerInput(t) { detectTapGestures { f() } }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ic(icon, size = 22.dp, color = c); Spacer(Modifier.height(4.dp)); BasicText(t, style = txt(13.sp, 600, c))
                }
            }
            Q("share", "공유") { Overlay.closePop(); ed.view?.let { v -> v.saveNow(); onExport(doc, v) } }
            Q("dup", "복제") { Overlay.closePop(); Nav.duplicate(doc) }
            Q("trash", "삭제", T.danger) {
                Overlay.closePop()
                Overlay.confirm("이 문서를 휴지통으로 옮길까요?", "삭제", danger = true) { ok -> if (ok) Nav.trashOpen(doc) }
            }
        }
        Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(.5.dp).background(T.hair))
        PItem("이름 바꾸기", "pencil") { Overlay.closePop(); onRename() }
        PItem("페이지 사이드바", "sidebar", on = ed.sidebar) { Overlay.closePop(); ed.sidebar = !ed.sidebar }
        PItem("페이지 보기 방식", "grid4") { Overlay.closePop(); ed.view?.let { v -> Nav.scope.launch { delay(160); viewModePop(r, ed, v) } } }
        val has = ed.page in doc.bookmarks
        PItem("북마크", "bookmark", on = has) { Overlay.closePop(); if (has) doc.bookmarks.remove(ed.page) else doc.bookmarks.add(ed.page); ed.rev++ }
        Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(.5.dp).background(T.hair))
        BasicText("보기", Modifier.padding(start = 10.dp, top = 4.dp, bottom = 2.dp), style = txt(13.sp, 600, T.ink2))
        DarkItem(ed)
        PItem("도구 막대 숨기기", "collapse") { Overlay.closePop(); ed.collapsed = true }
        PItem("설정", "settings") { Overlay.closePop(); kr.yeobaek.note.settings.openSettings() }
    }
}

/** 다크일 땐 "라이트 모드"(해), 라이트일 땐 "다크 모드"(달). 누르면 아이콘이 돌며 바뀜 */
@Composable
private fun DarkItem(ed: Ed) {
    val dark = Y.isDark()
    var spin by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (spin) 360f else 0f, tween(520, easing = Out), label = "spin", finishedListener = { spin = false })
    val sc by animateFloatAsState(if (spin) .6f else 1f, tween(260, easing = Out), label = "sc")
    var down by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(if (down) T.fill else Color.Transparent)
        .pointerInput(dark) {
            detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = {
                spin = true
                Nav.scope.launch { delay(240); toggleDark(ed) }
            })
        }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).graphicsLayer { rotationZ = rot; scaleX = sc; scaleY = sc }) { Ic(if (dark) "sun" else "moon", size = 20.dp, color = T.ink2) }
        Spacer(Modifier.width(10.dp))
        BasicText(if (dark) "라이트 모드" else "다크 모드", style = txt(15.sp, 400, T.snow))
    }
}
