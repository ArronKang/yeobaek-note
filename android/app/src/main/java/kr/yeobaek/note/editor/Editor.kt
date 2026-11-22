package kr.yeobaek.note.editor

import android.graphics.Bitmap
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.yeobaek.note.Nav
import kr.yeobaek.note.OpenDoc
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.TextBox
import kr.yeobaek.note.ink.Ink
import kr.yeobaek.note.ink.Paper
import kr.yeobaek.note.quiz.Fonts
import kr.yeobaek.note.quiz.Quiz
import kr.yeobaek.note.quiz.QuizLayout
import kr.yeobaek.note.quiz.Tex
import kr.yeobaek.note.ui.Btn
import kr.yeobaek.note.ui.Dot
import kr.yeobaek.note.ui.Ic
import kr.yeobaek.note.ui.IconBtn
import kr.yeobaek.note.ui.Out
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.PItem
import kr.yeobaek.note.ui.PTitle
import kr.yeobaek.note.ui.Seg
import kr.yeobaek.note.ui.Spring
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.txt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 에디터 화면 상태 */
class Ed {
    var tab by mutableStateOf("draw")
    var sidebar by mutableStateOf(false)
    var collapsed by mutableStateOf(false)
    var rev by mutableIntStateOf(0)          // 도구 다시 그리기
    var page by mutableIntStateOf(0)
    var chipT by mutableStateOf(0L)
    var pulling by mutableStateOf(false)
    var card by mutableStateOf<Card?>(null)
    var edit by mutableStateOf<TextEdit?>(null)
    var view by mutableStateOf<DocView?>(null)
    var chromeH by mutableFloatStateOf(0f)
}
class Card(val pi: Int, val idx: Int, val x: Float, val y: Float, val press: Boolean) {
    var hot by mutableStateOf<String?>(null)
    var dragging = press
    var hover = false; var seen = false
    val acts = HashMap<String, Rect>()
    var bounds = Rect.Zero
}
class TextEdit(val pi: Int, val t: TextBox, val isNew: Boolean)

object EditorBack {
    var handler: (() -> Boolean)? = null
}

@Composable
fun EditorScreen(open: OpenDoc, onExport: (Doc, DocView) -> Unit) {
    val ed = remember { Ed() }
    val doc = open.doc
    val scope = rememberCoroutineScope()
    val d = LocalDensity.current
    var closeJob by remember { mutableStateOf<Job?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    fun closeCard() { ed.card = null; ed.view?.setCard(-1, -1) }

    // 안드로이드 뒤로가기: 팝오버 → 정답 카드 → 글상자 → 사이드바 → 편집 화면
    DisposableEffect(open) {
        EditorBack.handler = {
            when {
                Overlay.closePop() -> true
                ed.card != null -> { closeCard(); true }
                ed.edit != null -> { val e = ed.edit!!; ed.edit = null; ed.view?.commitText(e.pi, e.t, e.isNew); true }
                ed.sidebar -> { ed.sidebar = false; true }
                else -> false
            }
        }
        onDispose { EditorBack.handler = null }
    }

    val host = remember(open) {
        object : DocView.Host {
            override fun onPage(n: Int, changed: Boolean) { if (changed) { ed.page = n; ed.chipT = System.currentTimeMillis() } }
            override fun onTools() { ed.rev++ }
            override fun onAnyDown() { focus.clearFocus(); Overlay.closePop(); if (ed.card != null && !(ed.card!!.dragging)) closeCard(); ed.edit?.let { e -> ed.edit = null; ed.view?.commitText(e.pi, e.t, e.isNew) } }
            override fun quizCard(pi: Int, idx: Int, x: Float, y: Float, press: Boolean) {
                ed.card = Card(pi, idx, x, y, press)
                ed.view?.setCard(pi, idx)
                if (Quiz.reveal(doc, idx)) ed.view?.animate(idx, listOf("stamp", "mark"))
            }
            override fun cardDrag(x: Float, y: Float) { val c = ed.card ?: return; setHot(c, x, y) }
            override fun cardUp(x: Float, y: Float, pen: Boolean) {
                val c = ed.card ?: return; c.dragging = false
                val h = hitAct(c, x, y)
                if (h != null) { c.hot = null; act(doc, ed, c, h); return }
                if (pen) {
                    // 펜 호버 단계: 곧 호버 움직임이 안 오면(멀리 듦) 닫힘
                    c.hover = true; closeJob?.cancel()
                    closeJob = scope.launch { delay(700); if (ed.card === c && !c.seen) closeCard() }
                } else closeCard()
            }
            override fun cardHover(x: Float, y: Float) { val c = ed.card ?: return; if (!c.hover) return; if (!c.seen) { c.seen = true; closeJob?.cancel() }; setHot(c, x, y) }
            override fun cardHoverExit() { val c = ed.card ?: return; if (c.hover) closeCard() }
            override fun editText(pi: Int, t: TextBox, isNew: Boolean) { ed.view?.editing = t; ed.edit = TextEdit(pi, t, isNew) }
            override fun quizChanged() { ed.rev++ }
            override fun toast(s: String) { Overlay.toast(s) }
            override fun onPull(active: Boolean) { ed.pulling = active }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(Paper.c().desk))) {
        androidx.compose.runtime.key(open.key) {
            AndroidView(
                factory = { ctx -> DocView(ctx, doc, host).also { v -> ed.view = v; v.restore(open.zoom, open.scroll) } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { v -> open.scroll = v.sy; open.zoom = v.zoom; v.release(); if (ed.view === v) ed.view = null },
            )
        }
        val dk = Y.isDark(); val gray = Y.paperGray()
        LaunchedEffect(dk, gray) { ed.view?.themeChanged() }
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LaunchedEffect(ed.chromeH, ed.collapsed, ed.view) {
            ed.view?.topPad = (if (ed.collapsed) with(d) { statusTop.toPx() } else ed.chromeH) + with(d) { 32.dp.toPx() }
        }

        // 페이지 사이드바
        PageSidebar(ed, doc, statusTop)

        // 정답 카드
        ed.card?.let { QuizCard(ed, doc, it) { closeCard() } }

        // 글상자 고치기
        ed.edit?.let { TextEditor(ed, it) }

        // 페이지 칩
        PageChip(ed, doc)

        // 상단 유리판
        Chrome(ed, open, statusTop, onExport)

        if (Nav.busy) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

private fun hitAct(c: Card, x: Float, y: Float): String? = c.acts.entries.firstOrNull { it.value.contains(androidx.compose.ui.geometry.Offset(x, y)) }?.key
private fun setHot(c: Card, x: Float, y: Float) { val h = hitAct(c, x, y); if (h != c.hot) { c.hot = h; if (h != null) Y.haptic(6) } }
private fun act(doc: Doc, ed: Ed, c: Card, v: String) {
    Quiz.setGrade(doc, c.idx, v)
    if (v != "clear") ed.view?.animate(c.idx, listOf("mark"))
    ed.view?.quizRepaint(); ed.card = null; ed.view?.setCard(-1, -1); ed.rev++
}

/* =============== 상단 =============== */
@Composable
private fun Chrome(ed: Ed, open: OpenDoc, statusTop: androidx.compose.ui.unit.Dp, onExport: (Doc, DocView) -> Unit) {
    val d = LocalDensity.current
    val doc = open.doc
    val hide = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ed.collapsed) { hide.animateTo(if (ed.collapsed) 1f else 0f, tween(380, easing = Out)) }
    var h by remember { mutableFloatStateOf(0f) }
    val glass = Modifier.shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1A212529), spotColor = Color(0x1A212529))
        .background(T.glass, RoundedCornerShape(18.dp)).border(.5.dp, T.glassEdge, RoundedCornerShape(18.dp))

    // 블라인드처럼: 빈 곳을 잡고 위로 끌면 숨음
    Column(Modifier.fillMaxWidth().padding(top = statusTop + 8.dp, start = 8.dp, end = 8.dp)
        .onSizeChanged { h = it.height.toFloat(); ed.chromeH = it.height + with(d) { (statusTop + 8.dp).toPx() } }
        .graphicsLayer { translationY = -hide.value * (h + with(d) { (statusTop + 24.dp).toPx() }) + drag }
        .then(glass)
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { if (drag < -36.dp.toPx()) { ed.collapsed = true; Y.haptic(8) }; drag = 0f },
                onDragCancel = { drag = 0f },
            ) { _, dy -> val nd = drag + dy; drag = if (nd < 0) nd else nd * .25f }
        }
        .padding(horizontal = 6.dp, vertical = 4.dp)) {
        // 1줄: 뒤로, 문서 탭, 제목, 공유, ⋯
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn("back", "라이브러리") { Nav.closeEditor() }
            DocTabs()
            Spacer(Modifier.width(6.dp))
            var title by remember(open) { mutableStateOf(doc.title) }
            BasicTextField(title, { title = it; doc.title = it; ed.view?.queueSave() }, Modifier.weight(1f),
                textStyle = txt(15.sp, 600, T.snow).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), singleLine = true,
                cursorBrush = SolidColor(T.acc), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            Spacer(Modifier.width(6.dp))
            IconBtn("share", "내보내기") { ed.view?.let { v -> v.saveNow(); onExport(doc, v) } }
            IconBtn("more", "더보기") { r -> moreMenu(ed, r) }
        }
        // 2줄: 탭, 도구, 펜
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("home" to ("home" to "홈"), "draw" to ("draw" to "필기"), "view" to ("eye" to "보기")).forEach { (id, p) ->
                IconBtn(p.first, p.second, size = 40.dp, pressed = ed.tab == id) {
                    if (ed.tab != id) { ed.tab = id; Overlay.closePop(); Y.haptic(8); if (id != "draw" && Y.mode == "lasso") ed.view?.clearSel() }
                }
            }
            Box(Modifier.padding(horizontal = 6.dp).size(.5.dp, 22.dp).background(T.hair))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                ed.rev; Y.penRev; Y.setRev
                Tools(ed, doc)
            }
            if (ed.tab == "draw") Tray(ed)
        }
    }
    // 숨겼을 때: 오른쪽 위 작은 유리 ⋯ (누르거나 아래로 끌면 내려옴)
    if (hide.value > .5f) {
        var dy by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxWidth().padding(top = statusTop + 10.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
            Box(Modifier.offset { IntOffset(0, dy.roundToInt()) }.size(40.dp)
                .shadow(8.dp, CircleShape).background(T.glass, CircleShape).border(.5.dp, T.glassEdge, CircleShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { if (dy > 30.dp.toPx()) { ed.collapsed = false; Y.haptic(8) }; dy = 0f }) { _, a -> dy = (dy + a).coerceIn(0f, 80.dp.toPx()) }
                }
                .pointerInput(Unit) { detectTapGestures { ed.collapsed = false; Y.haptic(8) } },
                contentAlignment = Alignment.Center) { Ic("more", size = 20.dp, color = T.snow) }
        }
    }
}

@Composable
private fun DocTabs() {
    if (Nav.tabs.size < 2) return
    Row(Modifier.widthIn(max = 420.dp).horizontalScroll(rememberScrollState()).padding(start = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Nav.tabs.forEach { t ->
            val on = Nav.cur === t
            var pressing by remember(t) { mutableStateOf(false) }
            // 꾹 누르고 있으면 점점 오그라들다가 닫힘
            val s by animateFloatAsState(if (pressing) .6f else 1f, tween(if (pressing) 550 else 200, easing = androidx.compose.animation.core.CubicBezierEasing(.5f, 0f, .75f, 0f)), label = "tab")
            Box(Modifier.height(30.dp).widthIn(max = 132.dp).scale(s).alpha(if (pressing) .5f + .5f * ((s - .6f) / .4f) else 1f)
                .clip(CircleShape).background(if (on) T.fill2 else Color.Transparent)
                .pointerInput(t) {
                    detectTapGestures(
                        onPress = { pressing = true; val ok = tryAwaitRelease(); pressing = false },
                        onLongPress = { Y.haptic(12); pressing = false; Nav.closeTab(t) },
                        onTap = { Nav.switchTo(t) })
                }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                BasicText(Y.clean(t.doc.title), style = txt(12.5.sp, if (on) 600 else 500, if (on) T.snow else T.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconBtn("plus", "다른 문서 열기", size = 30.dp, iconSize = 16.dp) { Nav.closeEditor() }
    }
}

private fun moreMenu(ed: Ed, r: Rect) {
    Overlay.popover("more", r, 220.dp, "right") {
        PItem("도구 막대 숨기기", "collapse") { Overlay.closePop(); ed.collapsed = true }
        PItem("다크 모드", "moon", on = Y.isDark()) { Overlay.closePop(); toggleDark(ed) }
        PItem("설정", "settings") { Overlay.closePop(); kr.yeobaek.note.settings.openSettings() }
    }
}
private fun toggleDark(ed: Ed) { Y.settings.dark = if (Y.isDark()) "off" else "on"; Y.saveSettings(); QuizLayout.clearCache(); ed.view?.themeChanged(); Y.haptic(8) }

@Composable private fun Sep() = Box(Modifier.padding(horizontal = 4.dp).size(.5.dp, 22.dp).background(T.hair))

/* =============== 도구줄 =============== */
@Composable
private fun Tools(ed: Ed, doc: Doc) {
    ed.rev; Y.mode; Y.setRev   // 기록·선택·모드가 바뀌면 다시 그림
    val v = ed.view ?: return
    fun setMode(m: String) { if (Y.mode == "lasso" && m != "lasso") v.clearSel(); Y.mode = m; Overlay.closePop(); ed.rev++ }
    when (ed.tab) {
        "draw" -> {
            IconBtn("undo", "실행취소", enabled = v.canUndo) { v.undo() }
            IconBtn("redo", "다시실행", enabled = v.canRedo) { v.redo() }
            Sep()
            IconBtn("text", "텍스트", pressed = Y.mode == "text") { setMode(if (Y.mode == "text") "pen" else "text") }
            IconBtn("lasso", "올가미 선택", pressed = Y.mode == "lasso") { setMode(if (Y.mode == "lasso") "pen" else "lasso") }
            IconBtn("space", "공간 삽입", pressed = Y.mode == "space") { setMode(if (Y.mode == "space") "pen" else "space") }
            Sep()
            IconBtn(if (Y.settings.finger) "finger" else "hand", if (Y.settings.finger) "손가락: 필기" else "손가락: 이동", pressed = Y.settings.finger) {
                Y.settings.finger = !Y.settings.finger; Y.saveSettings()
            }
            if (v.sel != null) {
                Sep()
                IconBtn("copy", "복사") { v.copySel() }
                IconBtn("palette", "색 변경") { r ->
                    Overlay.popover("recolor", r, 250.dp) {
                        PTitle("색")
                        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Y.PEN_COLORS.forEach { c -> Dot(Color(Ink.parse(c)), false) { v.recolorSel(c); Overlay.closePop() } }
                        }
                    }
                }
                IconBtn("trash", "삭제", color = T.danger) { v.deleteSel() }
            }
        }
        "home" -> {
            IconBtn("library", "라이브러리") { Nav.closeEditor() }
            IconBtn("sidebar", "페이지 사이드바", pressed = ed.sidebar) { ed.sidebar = !ed.sidebar }
            when (doc.type) {
                "quiz" -> {
                    Sep()
                    IconBtn("check", "전체 채점") {
                        val (n, sc) = Quiz.gradeAll(doc)
                        v.quizRepaint(); v.animateScore()
                        doc.quiz!!.problems.indices.forEach { if (doc.qs!!.grade["$it"] != null) v.animate(it, listOf("mark")) }
                        if (n == 0 && sc.graded == 0) Overlay.toast("고른 보기가 없어요") else Overlay.toast("${sc.right} / ${sc.total}", ms = 2200)
                        ed.rev++
                    }
                    IconBtn("answer", "정답표") { r -> answerSheet(r, doc, v) }
                    IconBtn("focus", "틀린 문제만 보기", pressed = doc.qs!!.focus) { Quiz.setFocus(doc, null); v.quizRepaint(); ed.rev++ }
                    IconBtn("retry", "틀린 문제로 새 문제지") {
                        Nav.busy = true
                        Nav.scope.launch {
                            val meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Quiz.retry(doc) }
                            Nav.busy = false
                            if (meta == null) Overlay.toast("틀린 문제가 없어요") else { Nav.closeEditor(); delay(250); Nav.open(meta) }
                        }
                    }
                    IconBtn("reset", "처음 상태로") {
                        Overlay.confirm("문제지를 처음 상태로 돌릴까요?", "초기화", danger = true) { ok -> if (ok) { Quiz.reset(doc); ed.card = null; v.quizRepaint(); ed.rev++ } }
                    }
                }
                "note" -> {
                    IconBtn("pageplus", "새 페이지") { v.addPage() }
                    IconBtn("template", "템플릿 변경") { r -> tplPop(r, v, v.pageIdx) { ed.rev++ } }
                }
                else -> {
                    IconBtn("list", "목차") { r -> tocPop(r, doc, v) }
                    val has = ed.page in doc.bookmarks
                    IconBtn("bookmark", "북마크", pressed = has) { if (has) doc.bookmarks.remove(ed.page) else doc.bookmarks.add(ed.page); ed.rev++ }
                    if (doc.pdf?.canSearch == true) { Sep(); SearchBox(ed, v) }
                }
            }
        }
        else -> {
            val z = v.zoom
            IconBtn("fit", "맞춤", pressed = z == "fit") { v.setZoom("fit"); ed.rev++ }
            IconBtn("width", "너비", pressed = z == "width") { v.setZoom("width"); ed.rev++ }
            Sep()
            IconBtn("minus", "축소") { v.setZoom(max(0.4f, v.zoomRel() - 0.25f)); ed.rev++ }
            IconBtn("plus", "확대") { v.setZoom(min(3f, v.zoomRel() + 0.25f)); ed.rev++ }
            Sep()
            IconBtn("paper", "종이 색") { r ->
                Overlay.popover("paper", r, 180.dp, "left") {
                    PTitle("종이 색")
                    listOf("white" to "흰색", "gray" to "연회색").forEach { (id, n) ->
                        PItem(n, on = Y.settings.paper == id) { Y.settings.paper = id; Y.saveSettings(); v.themeChanged(); Overlay.closePop() }
                    }
                }
            }
            IconBtn("moon", "다크 모드", pressed = Y.isDark()) { toggleDark(ed) }
        }
    }
}

@Composable
private fun SearchBox(ed: Ed, v: DocView) {
    var q by remember { mutableStateOf(v.search?.q ?: "") }
    var count by remember { mutableStateOf("") }
    fun upd() { val s = v.search; count = if (s == null) "" else if (s.list.isNotEmpty()) "${s.cur + 1} / ${s.list.size}" else if (s.done) "0" else "…" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ic("search", size = 18.dp)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(130.dp)) {
            if (q.isEmpty()) BasicText("본문 검색", style = txt(14.sp, 400, T.ink2))
            BasicTextField(q, { q = it }, textStyle = txt(14.sp, 400, T.snow), singleLine = true, cursorBrush = SolidColor(T.acc),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { v.runSearch(q) { upd() } }))
        }
        IconBtn("up", "이전 결과", size = 32.dp, iconSize = 18.dp) { v.search?.let { v.searchGo(it.cur - 1); upd() } }
        IconBtn("down", "다음 결과", size = 32.dp, iconSize = 18.dp) { v.search?.let { v.searchGo(it.cur + 1); upd() } }
        BasicText(count, style = txt(12.sp, 500, T.ink2))
    }
}

private fun tplPop(r: Rect, v: DocView, i: Int, after: () -> Unit) {
    Overlay.popover("tpl$i", r, 220.dp, "left") {
        PTitle("템플릿")
        Y.TEMPLATES.forEach { (id, n) -> PItem(n, on = v.doc.pages.getOrNull(i)?.tpl == id) { v.setTemplate(i, id); Overlay.closePop(); after() } }
    }
}

private fun tocPop(r: Rect, doc: Doc, v: DocView) {
    Overlay.popover("toc", r, 300.dp, "left") {
        PTitle("목차")
        val bms = doc.bookmarks.sorted()
        if (bms.isNotEmpty()) {
            BasicText("북마크", Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp), style = txt(12.sp, 500, T.ink2))
            bms.forEach { i -> PItem("${i + 1}쪽") { v.scrollToPage(i); Overlay.closePop() } }
        } else BasicText("목차 없음", Modifier.padding(10.dp), style = txt(14.sp, 400, T.ink2))
    }
}

private fun answerSheet(r: Rect, doc: Doc, v: DocView) {
    Overlay.popover("sheet", r, 300.dp) {
        PTitle("정답표")
        val ps = doc.quiz!!.problems.withIndex().filter { !it.value.isPassage }
        ps.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (i, p) ->
                    var shown by remember { mutableStateOf(false) }
                    val g = doc.qs!!.grade["$i"]
                    val c = Paper.c()
                    Column(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp)).background(T.fill)
                        .pointerInput(i) {
                            detectTapGestures {
                                if (!shown) { shown = true; return@detectTapGestures }
                                Overlay.closePop(); v.goToProblem(i)
                            }
                        }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        BasicText("${p.no}" + if (doc.qs!!.star["$i"] != null) "★" else "", style = txt(12.sp, 500, when (g) { "o" -> Color(c.green); "x" -> Color(c.red); else -> T.ink2 }))
                        BasicText(if (!shown) "?" else if (p.type == "choice") QuizLayout.CIRC.getOrElse((p.answer.toIntOrNull() ?: 1) - 1) { p.answer } else p.answer,
                            style = txt(15.sp, 600, if (shown) Color(c.red) else T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/* =============== 펜 트레이 =============== */
@Composable
private fun Tray(ed: Ed) {
    Y.penRev; ed.rev
    val n = Y.settings.trayCount.coerceIn(3, 8)
    val pens = Y.pens.take(n)
    if (Y.penIdx >= pens.size) Y.penIdx = 0
    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        pens.forEachIndexed { i, p ->
            val sel = Y.mode == "pen" && Y.penIdx == i
            PenSlot(p.kind, p.color, p.type, sel, if (p.kind == "hl") "형광펜 ${i + 1}" else "펜 ${i + 1}") { r ->
                val same = sel
                Y.penIdx = i; Y.haptic(8)
                if (Y.mode != "pen") { if (Y.mode == "lasso") ed.view?.clearSel(); Y.mode = "pen" }
                if (same) penPop(r, i, ed) else Overlay.closePop()
                ed.rev++
            }
        }
        PenSlot("eraser", null, null, Y.mode == "eraser", "지우개") { r ->
            Y.haptic(8)
            if (Y.mode != "eraser") { if (Y.mode == "lasso") ed.view?.clearSel(); Y.mode = "eraser"; Overlay.closePop() } else eraserPop(r)
            ed.rev++
        }
        IconBtn("plus", "펜 추가", size = 36.dp, iconSize = 20.dp) {
            if (Y.pens.size >= n) { Overlay.toast("펜 칸이 가득 찼어요"); return@IconBtn }
            Y.pens.add(kr.yeobaek.note.data.Pen("pen", "ball", Y.PEN_COLORS[Y.pens.size % 6], 0.8f, 1f))
            Y.penIdx = Y.pens.size - 1; Y.mode = "pen"; Y.savePens(); ed.rev++
        }
    }
}

/** 고른 펜만 펜꽂이에서 뽑힌 듯 올라옴 */
@Composable
private fun PenSlot(kind: String, color: String?, type: String?, sel: Boolean, label: String, onClick: (Rect) -> Unit) {
    val lift by animateFloatAsState(if (sel) 1f else 0f, tween(380, easing = Spring), label = "pen")
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.size(38.dp, 44.dp).onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(kind, color, type, label) {
            detectTapGestures(onLongPress = { Overlay.tip = kr.yeobaek.note.ui.TipSpec(System.nanoTime(), label, bounds) }, onTap = { onClick(bounds) })
        }, contentAlignment = Alignment.Center) {
        PenArt(kind, color, type, 32.dp, Modifier.graphicsLayer { translationY = (2f - 6f * lift).dp.toPx(); val s = 1f + .06f * lift; scaleX = s; scaleY = s })
    }
}

private fun penPop(r: Rect, i: Int, ed: Ed) {
    Overlay.popover("pen$i", r, 320.dp, "right") {
        Y.penRev
        val pen = Y.pens[i]
        // 펜 모양 (만년필·볼펜·세필·붓 타일)
        if (pen.kind == "pen") Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Y.PEN_TYPES.forEach { (id, name) ->
                val on = pen.type == id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).pointerInput(id) { detectTapGestures { pen.type = id; Y.savePens(); ed.rev++ } }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(if (on) T.fill2 else T.fill), contentAlignment = Alignment.Center) { PenArt("pen", pen.color, id, 34.dp) }
                    Spacer(Modifier.height(4.dp))
                    BasicText(name, style = txt(12.sp, if (on) 600 else 400, if (on) T.snow else T.ink2))
                }
            }
        }
        // 미리보기
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(4.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
            val w = size.width; val hh = size.height
            val pts = (0..90).map { k -> val t = k / 90f; floatArrayOf(0.06f + t * 0.88f, (hh / 2 + kotlin.math.sin(t * Math.PI * 2.2).toFloat() * hh * .22f + kotlin.math.cos(t * 9.0).toFloat() * 3f) / w, 0.25f + 0.6f * kotlin.math.sin(t * Math.PI).toFloat()) }
            val st = kr.yeobaek.note.data.Stroke(pen.kind, pen.color, pen.mm / 90f, if (pen.kind == "hl") pen.alpha else 1f, if (pen.kind == "pen") pen.type?.takeIf { it != "ball" } else null, pts.toMutableList())
            drawContext.canvas.nativeCanvas.let { Ink.draw(it, st, w, false) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Slider(pen.mm, .3f, 12f, Modifier.weight(1f)) { pen.mm = (it * 10).roundToInt() / 10f; Y.savePens() }
            BasicText("%.1fmm".format(pen.mm), Modifier.width(56.dp), style = txt(13.sp, 500, T.ink2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
        }
        // 색
        Row(Modifier.fillMaxWidth().padding(4.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Y.PEN_COLORS.forEach { c -> Dot(Color(Ink.parse(c)), pen.kind == "pen" && pen.color == c) { if (pen.kind != "pen") { pen.kind = "pen"; pen.mm = 1f; pen.alpha = 1f; if (pen.type == null) pen.type = "ball" }; pen.color = c; Y.savePens(); ed.rev++ } }
            Box(Modifier.padding(horizontal = 4.dp).size(.5.dp, 20.dp).background(T.hair))
            Y.HL_COLORS.forEach { c -> Dot(Color(Ink.parse(c)), pen.kind == "hl" && pen.color == c) { if (pen.kind != "hl") { pen.kind = "hl"; pen.mm = 6f; pen.alpha = .4f }; pen.color = c; Y.savePens(); ed.rev++ } }
        }
        if (pen.kind == "hl") Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("투명도", style = txt(13.sp, 400, T.ink2)); Spacer(Modifier.width(8.dp))
            Slider(pen.alpha, .1f, 1f, Modifier.weight(1f)) { pen.alpha = (it * 20).roundToInt() / 20f; Y.savePens() }
            BasicText("${(pen.alpha * 100).roundToInt()}%", Modifier.width(48.dp), style = txt(13.sp, 500, T.ink2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
        }
    }
}

private fun eraserPop(r: Rect) {
    Overlay.popover("eraser", r, 280.dp, "right") {
        Y.penRev
        val e = Y.eraser
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("크기", style = txt(13.sp, 400, T.ink2)); Spacer(Modifier.width(8.dp))
            Slider(e.mm, 1f, 20f, Modifier.weight(1f)) { e.mm = (it * 2).roundToInt() / 2f; Y.savePens() }
            BasicText("%.1fmm".format(e.mm), Modifier.width(56.dp), style = txt(13.sp, 500, T.ink2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
        }
        Box(Modifier.padding(8.dp)) { Seg(e.partial, listOf(false to "획 지우기", true to "부분 지우기")) { e.partial = it; Y.savePens() } }
    }
}

/** 단순한 슬라이더: 가는 선 + 동그란 손잡이 */
@Composable
fun Slider(value: Float, min: Float, max: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    val k = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val acc = T.acc; val track = T.fill2
    Box(modifier.height(36.dp).onSizeChanged { w = it.width.toFloat() }
        .pointerInput(min, max) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull() ?: continue
                    if (ch.pressed) { onChange(min + (ch.position.x / w).coerceIn(0f, 1f) * (max - min)); ch.consume() }
                }
            }
        }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2; val r = 9.dp.toPx(); val x = r + (size.width - 2 * r) * k
            drawLine(track, androidx.compose.ui.geometry.Offset(r, y), androidx.compose.ui.geometry.Offset(size.width - r, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(acc, androidx.compose.ui.geometry.Offset(r, y), androidx.compose.ui.geometry.Offset(x, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.White, r, androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(Color(0x33212529), r, androidx.compose.ui.geometry.Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(.5.dp.toPx()))
        }
    }
}

/* =============== 페이지 칩 =============== */
@Composable
private fun PageChip(ed: Ed, doc: Doc) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(ed.chipT) {
        if (ed.chipT == 0L) return@LaunchedEffect
        a.animateTo(1f, tween(350, easing = Out)); delay(1600); a.animateTo(0f, tween(350, easing = Out))
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.graphicsLayer { alpha = if (ed.pulling) 0f else a.value; translationY = (1 - a.value) * 10.dp.toPx() }
            .background(T.floatBg.copy(alpha = .92f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp)) {
            BasicText("${ed.page + 1} / ${doc.pages.size}", style = txt(12.sp, 500, T.floatFg))
        }
    }
}

/* =============== 페이지 사이드바 =============== */
@Composable
private fun PageSidebar(ed: Ed, doc: Doc, statusTop: androidx.compose.ui.unit.Dp) {
    val a by animateFloatAsState(if (ed.sidebar) 1f else 0f, tween(380, easing = Out), label = "side")
    if (a <= 0.01f) return
    ed.rev
    val v = ed.view ?: return
    val d = LocalDensity.current
    val top = with(d) { ed.chromeH.toDp() } + 16.dp
    val st = rememberLazyListState()
    LaunchedEffect(ed.page) { if (ed.sidebar) runCatching { st.animateScrollToItem(max(0, ed.page - 1)) } }
    Box(Modifier.padding(top = top, bottom = 8.dp).fillMaxHeight().width(148.dp)
        .graphicsLayer { translationX = -(1 - a) * size.width; alpha = a }
        .shadow(12.dp, RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)).background(T.glass.copy(alpha = .94f), RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))) {
        LazyColumn(state = st, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(doc.pages.toList(), key = { i, p -> System.identityHashCode(p) * 31 + i }) { i, pg ->
                val wpx = with(d) { 116.dp.roundToPx() }
                val bm = remember(pg, ed.rev, Y.setRev, i) { if (i < doc.pages.size) runCatching { v.thumb(i, wpx) }.getOrNull() else null }
                var bounds by remember { mutableStateOf(Rect.Zero) }
                Column(Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
                    .pointerInput(i, doc.type) {
                        detectTapGestures(
                            onLongPress = { if (doc.type == "note") { Y.haptic(12); pageMenu(bounds, v, i, ed) } },
                            onTap = { v.scrollToPage(i) })
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(116.dp).height((116 * pg.ratio).dp).border(if (ed.page == i) 2.dp else .5.dp, if (ed.page == i) T.acc else T.hair, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
                        bm?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText("${i + 1}", style = txt(11.sp, if (ed.page == i) 600 else 400, if (ed.page == i) T.snow else T.ink2))
                }
            }
        }
    }
}

private fun pageMenu(r: Rect, v: DocView, i: Int, ed: Ed) {
    val pages = v.doc.pages
    if (i !in pages.indices) return
    fun after() { v.pagesChanged(); ed.rev++ }
    Overlay.popover("pm$i", r, 200.dp, "left") {
        PItem("복제", "dup") {
            Overlay.closePop(); val pg = pages.getOrNull(i) ?: return@PItem
            pages.add(i + 1, kr.yeobaek.note.data.Page(pg.ratio, pg.tpl, pg.strokes.map { it.copyDeep() }.toMutableList(), pg.texts.map { kr.yeobaek.note.data.TextBox(Y.uid(), it.x, it.y, it.w, it.fs, it.c, it.t) }.toMutableList()))
            after()
        }
        PItem("템플릿 변경", "template") { Overlay.closePop(); Nav.scope.launch { delay(160); tplPop(r, v, i) { ed.rev++ } } }
        PItem("위로 이동", "up", enabled = i > 0) { Overlay.closePop(); if (i !in pages.indices) return@PItem; val t = pages[i]; pages[i] = pages[i - 1]; pages[i - 1] = t; after() }
        PItem("아래로 이동", "down", enabled = i < pages.size - 1) { Overlay.closePop(); if (i + 1 !in pages.indices) return@PItem; val t = pages[i]; pages[i] = pages[i + 1]; pages[i + 1] = t; after() }
        PItem("삭제", "trash", color = null) { Overlay.closePop(); if (pages.size < 2 || i !in pages.indices) { Y.haptic(20); return@PItem }; pages.removeAt(i); after() }
    }
}

/* =============== 정답·풀이 카드 =============== */
@Composable
private fun QuizCard(ed: Ed, doc: Doc, c: Card, close: () -> Unit) {
    val v = ed.view ?: return
    val d = LocalDensity.current
    val pc = Paper.c()
    val k = v.W / QuizLayout.PW                                     // 페이지 1단위 = 픽셀
    val p = doc.quiz!!.problems[c.idx]
    val base = (11f * k / d.density / d.fontScale).coerceIn(14f, 19f)
    val left = v.pageLeft() + 76f * k; val right = v.pageLeft() + v.W - 44f * k
    val pageTop = v.pageTop(c.pi); val pageBottom = pageTop + v.pageH(c.pi)
    var hpx by remember(c) { mutableFloatStateOf(0f) }
    val below = c.y + 22f * k
    val up = hpx > 0 && below + hpx > pageBottom - 12f * k && c.y - hpx - 22f * k > pageTop + 8f * k
    val top = if (up) c.y - hpx - 22f * k else min(below, pageBottom - hpx - 12f * k).coerceAtLeast(pageTop + 8f * k)
    val a = remember(c) { Animatable(0f) }
    LaunchedEffect(c) { a.animateTo(1f, tween(340, easing = Spring)) }
    val cardBg = if (pc.dark) Color(0xFF2B2C31) else Color(pc.paper)
    val ink = Color(pc.ink); val label = Color(pc.label); val red = Color(pc.red); val green = Color(pc.green)
    val wDp = with(d) { (right - left).toDp() }
    val ox = ((c.x - left) / max(1f, right - left)).coerceIn(0f, 1f)
    val g = doc.qs!!.grade["${c.idx}"]

    Column(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.width(wDp)
        .onSizeChanged { hpx = it.height.toFloat() }
        .onGloballyPositioned { c.bounds = it.boundsInRoot() }
        .graphicsLayer { alpha = if (hpx == 0f) 0f else (a.value * 7).coerceAtMost(1f); val s = .92f + .08f * a.value; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(ox, if (up) 1f else 0f) }
        .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x2E212529), spotColor = Color(0x2E212529))
        .background(cardBg, RoundedCornerShape(14.dp)).border(1.dp, Color(pc.line2), RoundedCornerShape(14.dp))
        .heightIn(max = with(d) { (520f * k).toDp() }).verticalScroll(rememberScrollState())
        .padding(horizontal = (base * 1.2f).dp, vertical = (base * 1f).dp)) {
        @Composable fun Acts() {
            // 답안 제거 · 맞았어요 · 틀렸어요 — 누른 채로 밀어서 고르거나 눌러서 골라요
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((6f * k / d.density).dp)) {
                listOf(Triple("clear", "reset", "답안 제거"), Triple("o", "check", "맞았어요"), Triple("x", "close", "틀렸어요")).forEach { (id, ic, t) ->
                    val hot = c.hot == id
                    val col = when (id) { "o" -> green; "x" -> red; else -> ink }
                    val s by animateFloatAsState(if (hot) 1.06f else 1f, tween(200, easing = Spring), label = "a")
                    Row(Modifier.weight(1f).height(with(d) { (34f * k).toDp() }.coerceAtLeast(40.dp)).scale(s)
                        .onGloballyPositioned { c.acts[id] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(10.dp)).background(if (hot) col else cardBg)
                        .border(1.dp, if (g == id && !hot) ink else Color(pc.line2), RoundedCornerShape(10.dp))
                        .pointerInput(id) { detectTapGestures { act(doc, ed, c, id) } },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val fg = if (hot) Color(pc.paper) else col
                        Ic(ic, size = (base * 1.15f).dp, color = fg)
                        Spacer(Modifier.width(4.dp))
                        BasicText(t, style = txt((base * .88f).sp, 600, fg), maxLines = 1)
                    }
                }
            }
        }
        if (!up) { Acts(); Spacer(Modifier.height(10.dp)) }
        BasicText("${p.no}번 정답", style = txt((base * .82f).sp, 600, label, .04f))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.type == "choice") {
                val ai = p.answer.toIntOrNull() ?: 1
                BasicText(QuizLayout.CIRC.getOrElse(ai - 1) { p.answer }, style = txt((base * 1.85f).sp, 600, red))
                Spacer(Modifier.width(10.dp))
                p.choices.getOrNull(ai - 1)?.let { TexText(it, base * 1.5f, ink, 500) }
            } else TexText(p.answer, base * 1.5f, ink, 600)
        }
        if (p.type == "choice") {
            val sel = doc.qs!!.sel["${c.idx}"]
            if (sel != null) {
                val right = Quiz.isRight(doc, c.idx, sel) == true
                Spacer(Modifier.height(4.dp))
                BasicText(if (right) "맞았어요" else "내 답 " + QuizLayout.CIRC.getOrElse(sel - 1) { "$sel" }, style = txt((base * .95f).sp, 600, if (right) green else red))
            }
        }
        if (p.solution.isNotBlank()) {
            Spacer(Modifier.height(12.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(pc.line2))); Spacer(Modifier.height(8.dp))
            BasicText("풀이", style = txt((base * .82f).sp, 600, label, .04f))
            Spacer(Modifier.height(4.dp))
            TexText(p.solution, base, ink, 400, lineH = 1.7f)
        }
        if (up) { Spacer(Modifier.height(10.dp)); Acts() }
    }
}

/** 수식이 섞인 글 (TextView + JLaTeXMath 조각) */
@Composable
fun TexText(src: String, sizeSp: Float, color: Color, weight: Int = 400, lineH: Float = 1.5f, modifier: Modifier = Modifier) {
    val d = LocalDensity.current
    val px = with(d) { sizeSp.sp.toPx() }
    AndroidView(factory = { ctx -> TextView(ctx).apply { includeFontPadding = false } }, modifier = modifier, update = { tv ->
        tv.typeface = Fonts.w(weight); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px); tv.setTextColor(color.toArgb())
        tv.setLineSpacing(px * (lineH - 1.2f).coerceAtLeast(0f), 1f)
        tv.text = Tex.build(src, px, color.toArgb())
    })
}

/* =============== 글상자 =============== */
@Composable
private fun TextEditor(ed: Ed, e: TextEdit) {
    val v = ed.view ?: return
    val d = LocalDensity.current
    val r = v.textRect(e.pi, e.t)
    val fr = remember(e) { FocusRequester() }
    var text by remember(e) { mutableStateOf(e.t.t) }
    var had by remember(e) { mutableStateOf(false) }
    val col = if (Paper.c().dark && e.t.c.equals("#1A1A20", true)) Color(0xFFF2F2F6) else Color(Ink.parse(e.t.c))
    LaunchedEffect(e) { delay(30); runCatching { fr.requestFocus() } }
    fun done() { if (ed.edit === e) { e.t.t = text; ed.edit = null; v.commitText(e.pi, e.t, e.isNew) } }
    BasicTextField(text, { text = it; e.t.t = it },
        Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }.width(with(d) { r.width().toDp() })
            .border(1.5.dp, T.acc, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
            .focusRequester(fr).onFocusChanged { if (it.isFocused) had = true else if (had) done() },
        textStyle = txt(with(d) { (e.t.fs * v.W).toSp() }, 400, col), cursorBrush = SolidColor(T.acc))
}
