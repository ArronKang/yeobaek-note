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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.zIndex
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
    /** 화면 분할: 오른쪽(세로 화면이면 아래) 문서 */
    var split by mutableStateOf<Doc?>(null)
    /** 자를 화면에 꺼냄 */
    var ruler by mutableStateOf(false)
    /** 펜 막대를 끄는 중: 떼어 낸 위치(px, 화면 기준) */
    var pkFloat by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    /** 편집 화면 크기(px) */
    var screen = androidx.compose.ui.geometry.Size.Zero
    /** 페이지 보기 방식: 여러 쪽 한눈에 */
    var overview by mutableStateOf(false)
    var ratio by mutableFloatStateOf(.5f)
    var left: DocView? = null
    var right: DocView? = null
    /** 지금 손댄 쪽의 문서 (도구가 그 쪽에 적용) */
    val adoc: Doc? get() = view?.doc
}
class Card(val v: DocView, val pi: Int, val idx: Int, val x: Float, val y: Float, val press: Boolean) {
    var hot by mutableStateOf<String?>(null)
    var dragging = press
    var hover = false; var seen = false
    val acts = HashMap<String, Rect>()
    var bounds = Rect.Zero
}
class TextEdit(val v: DocView, val pi: Int, val t: TextBox, val isNew: Boolean)

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

    fun closeCard() { ed.card?.v?.setCard(-1, -1); ed.card = null }

    // 안드로이드 뒤로가기: 팝오버 → 정답 카드 → 글상자 → 사이드바 → 편집 화면
    DisposableEffect(open) {
        EditorBack.handler = {
            when {
                Overlay.closePop() -> true
                ed.card != null -> { closeCard(); true }
                ed.edit != null -> { val e = ed.edit!!; ed.edit = null; e.v.commitText(e.pi, e.t, e.isNew); true }
                ed.sidebar -> { ed.sidebar = false; true }
                ed.split != null -> { SplitCtl.end(); true }
                else -> false
            }
        }
        onDispose { EditorBack.handler = null }
    }

    fun hostFor(right: Boolean): DocView.Host = object : DocView.Host {
        fun me(): DocView? = if (right) ed.right else ed.left
        fun rx(x: Float) = x + (me()?.rootX ?: 0f)
        fun ry(y: Float) = y + (me()?.rootY ?: 0f)
        override fun onPage(n: Int, changed: Boolean) { if (changed && ed.view === me()) { ed.page = n; ed.chipT = System.currentTimeMillis() } }
        override fun onTools() { ed.rev++ }
        override fun onAnyDown() {
            me()?.let { if (ed.view !== it) { ed.view = it; ed.page = it.pageIdx; ed.rev++ } }
            focus.clearFocus(); Overlay.closePop()
            if (ed.card != null && !(ed.card!!.dragging)) closeCard()
            ed.edit?.let { e -> ed.edit = null; e.v.commitText(e.pi, e.t, e.isNew) }
        }
        override fun quizCard(pi: Int, idx: Int, x: Float, y: Float, press: Boolean) {
            val v = me() ?: return
            ed.card = Card(v, pi, idx, rx(x), ry(y), press)
            v.setCard(pi, idx)
            if (Quiz.reveal(v.doc, idx)) v.animate(idx, listOf("stamp", "mark"))
        }
        override fun cardDrag(x: Float, y: Float) { val c = ed.card ?: return; setHot(c, rx(x), ry(y)) }
        override fun cardUp(x: Float, y: Float, pen: Boolean) {
            val c = ed.card ?: return; c.dragging = false
            val h = hitAct(c, rx(x), ry(y))
            if (h != null) { c.hot = null; act(ed, c, h); return }
            if (pen) {
                // 펜 호버 단계: 곧 호버 움직임이 안 오면(멀리 듦) 닫힘
                c.hover = true; closeJob?.cancel()
                closeJob = scope.launch { delay(700); if (ed.card === c && !c.seen) closeCard() }
            } else closeCard()
        }
        override fun cardHover(x: Float, y: Float) { val c = ed.card ?: return; if (!c.hover) return; if (!c.seen) { c.seen = true; closeJob?.cancel() }; setHot(c, rx(x), ry(y)) }
        override fun cardHoverExit() { val c = ed.card ?: return; if (c.hover) closeCard() }
        override fun editText(pi: Int, t: TextBox, isNew: Boolean) { val v = me() ?: return; v.editing = t; ed.edit = TextEdit(v, pi, t, isNew) }
        override fun quizChanged() { ed.rev++ }
        override fun toast(s: String) { Overlay.toast(s) }
        override fun onPull(active: Boolean) { ed.pulling = active }
    }
    val hostL = remember(open) { hostFor(false) }
    val hostR = remember(open) { hostFor(true) }
    // 분할을 끝내면 오른쪽 문서 정리
    fun endSplit() {
        val r = ed.right; ed.split?.let { sd -> r?.saveNow(); Nav.splitDoc = null; if (sd.type == "pdf" && !Nav.shared(sd)) sd.pdf?.close() }
        ed.split = null; ed.ratio = .5f; ed.view = ed.left; ed.rev++; Y.haptic(10)
    }
    SplitCtl.start = { d2 -> ed.split?.let { endSplit() }; ed.split = d2; Nav.splitDoc = d2; ed.ratio = .5f; Y.haptic(10) }
    SplitCtl.end = { endSplit() }

    Box(Modifier.fillMaxSize().background(Color(Paper.c().desk)).onSizeChanged { ed.screen = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topPad = (if (ed.collapsed) with(d) { statusTop.toPx() } else ed.chromeH) + with(d) { 32.dp.toPx() }
        val leftPane = @Composable { m: Modifier ->
            androidx.compose.runtime.key(open.key) {
                AndroidView(
                    factory = { ctx -> DocFrame(ctx, DocView(ctx, doc, hostL).also { v -> ed.left = v; ed.view = v; v.restore(open.zoom, open.scroll) }) },
                    modifier = m.clipToBounds().onGloballyPositioned { c -> ed.left?.let { val o = c.boundsInRoot(); it.rootX = o.left; it.rootY = o.top } },
                    update = { it.view.topPad = topPad },
                    onRelease = { f -> val v = f.view; open.scroll = v.sy; open.zoom = v.zoom; f.release(); if (ed.left === v) ed.left = null; if (ed.view === v) ed.view = null },
                )
            }
        }
        val sd = ed.split
        // 왼쪽 칸은 늘 같은 자리(같은 부모)에 두고 크기만 바꿈: 옮기면 안드로이드 뷰가 다시 만들어져 그려지지 않음
        var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        Box(Modifier.fillMaxSize().onSizeChanged { box = it }) {
            val bw = with(d) { box.width.toDp() }; val bh = with(d) { box.height.toDp() }
            val vertical = sd != null && bw < 820.dp && bh > bw
            val total = if (vertical) bh else bw
            val hw = 12.dp
            val r = if (sd == null) 1f else ed.ratio
            val first = if (sd == null) total else ((total - hw) * r)
            leftPane(if (vertical) Modifier.fillMaxWidth().height(first) else Modifier.fillMaxHeight().width(first))
            if (sd != null) {
                val rightPane = @Composable { m: Modifier ->
                    androidx.compose.runtime.key(sd) {
                        AndroidView(
                            factory = { ctx -> DocFrame(ctx, DocView(ctx, sd, hostR).also { v -> ed.right = v; ed.view = v; v.restore("width", 0f) }) },
                            modifier = m.clipToBounds().onGloballyPositioned { c -> ed.right?.let { val o = c.boundsInRoot(); it.rootX = o.left; it.rootY = o.top } },
                            update = { it.view.topPad = topPad },
                            onRelease = { f -> val v = f.view; f.release(); if (ed.right === v) ed.right = null; if (ed.view === v) ed.view = ed.left },
                        )
                    }
                }
                val second = (total - hw) * (1 - r)
                if (vertical) {
                    Box(Modifier.offset(y = first)) { SplitHandle(ed, true, total) }
                    rightPane(Modifier.offset(y = first + hw).fillMaxWidth().height(second))
                } else {
                    Box(Modifier.offset(x = first)) { SplitHandle(ed, false, total) }
                    rightPane(Modifier.offset(x = first + hw).fillMaxHeight().width(second))
                }
                // 지금 쓰는 칸 표시: 위쪽 가는 선
                val act = ed.view
                if (act != null && r > 0f && r < 1f) {
                    val onRight = act === ed.right
                    val lineY = with(d) { (topPad - 24.dp.toPx()).toDp() }
                    if (vertical) Box(Modifier.offset(y = if (onRight) first + hw else lineY).fillMaxWidth().height(2.dp).background(T.acc))
                    else Box(Modifier.offset(x = if (onRight) first + hw else 0.dp, y = lineY).width(if (onRight) second else first).height(2.dp).background(T.acc))
                }
            }
        }
        val dk = Y.isDark(); val gray = Y.paperGray()
        LaunchedEffect(dk, gray) { ed.left?.themeChanged(); ed.right?.themeChanged() }

        // 페이지 사이드바
        ed.view?.let { PageSidebar(ed, it.doc, statusTop) }

        // 정답 카드
        ed.card?.let { QuizCard(ed, it.v.doc, it) { closeCard() } }

        // 글상자 고치기
        ed.edit?.let { TextEditor(ed, it) }

        // 페이지 칩 (위쪽 페이지 눈금을 끈 경우만)
        if (!Y.settings.scale) ed.view?.let { PageChip(ed, it.doc) }

        // 여러 쪽 한눈에
        if (ed.overview) ed.view?.let { Overview(ed, it) }

        // 상단: 탭 + 모드 + 펜 막대 + 페이지 눈금
        Chrome(ed, open, statusTop, onExport)

        // 떼어 낸 펜 막대
        FloatingPicker(ed)

        if (Nav.busy) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

private fun hitAct(c: Card, x: Float, y: Float): String? = c.acts.entries.firstOrNull { it.value.contains(androidx.compose.ui.geometry.Offset(x, y)) }?.key
private fun setHot(c: Card, x: Float, y: Float) { val h = hitAct(c, x, y); if (h != c.hot) { c.hot = h; if (h != null) Y.haptic(6) } }
internal fun act(ed: Ed, c: Card, v: String) {
    Quiz.setGrade(c.v.doc, c.idx, v)
    if (v != "clear") c.v.animate(c.idx, listOf("mark"))
    c.v.quizRepaint(); ed.card = null; c.v.setCard(-1, -1); ed.rev++
}

/** 도구 막대 유리판 색: 설정의 투명도 */
@Composable fun glassColor(): Color { Y.setRev; return T.glass.copy(alpha = when (Y.settings.glass) { "clear" -> .45f; "solid" -> 1f; else -> .72f }) }

/** 문서 이름 바꾸기 창 */
internal fun renameModal(cur: String, done: (String) -> Unit) {
    Overlay.modal(width = 420.dp) { close ->
        var v by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(cur, androidx.compose.ui.text.TextRange(0, cur.length))) }
        val fr = remember { androidx.compose.ui.focus.FocusRequester() }
        fun go() { val t = v.text.trim(); if (t.isNotEmpty()) done(t); close() }
        Column {
            BasicTextField(v, { if (it.text.length <= 40) v = it }, Modifier.fillMaxWidth().focusRequester(fr).padding(vertical = 6.dp), singleLine = true,
                textStyle = txt(22.sp, 600, T.snow, -.02f), cursorBrush = SolidColor(T.acc),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { go() }))
            Box(Modifier.fillMaxWidth().height(.5.dp).background(T.hair))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Btn("취소", "ghost") { close() }; Spacer(Modifier.width(4.dp)); Btn("바꾸기") { go() }
            }
        }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    }
}

internal fun toggleDark(ed: Ed) { Y.settings.dark = if (Y.isDark()) "off" else "on"; Y.saveSettings(); QuizLayout.clearCache(); ed.left?.themeChanged(); ed.right?.themeChanged(); Y.haptic(8) }

@Composable
internal fun SearchBox(ed: Ed, v: DocView) {
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

internal fun tplPop(r: Rect, v: DocView, i: Int, after: () -> Unit) {
    Overlay.popover("tpl$i", r, 220.dp, "left") {
        PTitle("템플릿")
        Y.TEMPLATES.forEach { (id, n) -> PItem(n, on = v.doc.pages.getOrNull(i)?.tpl == id) { v.setTemplateUndoable(i, id); Overlay.closePop(); after() } }
    }
}

internal fun tocPop(r: Rect, doc: Doc, v: DocView) {
    Overlay.popover("toc", r, 300.dp, "left") {
        PTitle("목차")
        val bms = doc.bookmarks.sorted()
        if (bms.isNotEmpty()) {
            BasicText("북마크", Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp), style = txt(12.sp, 500, T.ink2))
            bms.forEach { i -> PItem("${i + 1}쪽") { v.scrollToPage(i); Overlay.closePop() } }
        } else BasicText("목차 없음", Modifier.padding(10.dp), style = txt(14.sp, 400, T.ink2))
    }
}

internal fun answerSheet(r: Rect, doc: Doc, v: DocView) {
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

/* =============== 여러 쪽 한눈에 (페이지 보기 방식) =============== */
@Composable
private fun Overview(ed: Ed, v: DocView) {
    ed.rev
    val d = LocalDensity.current
    val doc = v.doc
    val top = with(d) { ed.chromeH.toDp() } + 12.dp
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(320, easing = Out)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a.value }.background(Color(Paper.c().desk)).pointerInput(Unit) { detectTapGestures { } }) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Adaptive(180.dp),
            Modifier.fillMaxSize().padding(top = top),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(doc.pages.size) { i ->
                val pg = doc.pages[i]
                val wpx = with(d) { 180.dp.roundToPx() }
                val bm = remember(pg, v.verOf(pg), Y.setRev, i) { runCatching { v.thumb(i, wpx) }.getOrNull() }
                Column(Modifier.pointerInput(i) { detectTapGestures { ed.overview = false; v.scrollToPage(i, smooth = false) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f / pg.ratio).shadow(4.dp, RoundedCornerShape(6.dp))
                        .border(if (ed.page == i) 2.5.dp else .5.dp, if (ed.page == i) Color(0xFF0088FF) else T.hair, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color.White)) {
                        bm?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText("${i + 1}", style = txt(13.sp, if (ed.page == i) 700 else 500, if (ed.page == i) Color(0xFF0088FF) else T.ink2))
                }
            }
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
                val bm = remember(pg, v.verOf(pg), Y.setRev, i) { if (i < doc.pages.size) runCatching { v.thumb(i, wpx) }.getOrNull() else null }
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
    val before = pages.toList()
    fun after() { v.pagesChanged(before); ed.rev++ }
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
    val v = c.v
    val d = LocalDensity.current
    val pc = Paper.c()
    val k = v.W / QuizLayout.PW                                     // 페이지 1단위 = 픽셀
    val p = doc.quiz!!.problems[c.idx]
    val base = (11f * k / d.density / d.fontScale).coerceIn(14f, 19f)
    val left = v.rootX + v.pageLeft() + 76f * k; val right = v.rootX + v.pageLeft() + v.W - 44f * k
    val pageTop = v.rootY + v.pageTop(c.pi); val pageBottom = pageTop + v.pageH(c.pi)
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
                        .pointerInput(id) { detectTapGestures { act(ed, c, id) } },
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
    val v = e.v
    val d = LocalDensity.current
    val r = v.textRect(e.pi, e.t).apply { offset(v.rootX, v.rootY) }
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

/* =============== 화면 분할 =============== */
object SplitCtl {
    var start: (Doc) -> Unit = {}
    var end: () -> Unit = {}
}

/** 옆에 열 문서 고르기: 새 노트, 또는 서재의 노트·PDF·문제지 */
internal fun splitPicker(r: Rect, cur: Doc) {
    Overlay.popover("split", r, 280.dp, "right") {
        PTitle("옆에 열기")
        PItem("새 노트", "compose") {
            Overlay.closePop()
            val meta = Nav.newNote(Y.clean(cur.title) + " 노트", Nav.nextCover(), "lined", null)
            Nav.load(meta) { d -> if (d != null) SplitCtl.start(d) }
        }
        Y.lib.notes.filter { !it.trashed && it.id != cur.id && it.id != cur.libId }.sortedByDescending { it.updated }.take(30).forEach { n ->
            PItem(Y.clean(n.title).ifEmpty { "제목 없음" }, when (n.kind) { "pdf" -> "pdf"; "quiz" -> "quiz"; else -> "notes" }) {
                Overlay.closePop(); Nav.load(n) { d -> if (d != null) SplitCtl.start(d) else Overlay.toast("열 수 없어요") }
            }
        }
    }
}

/** 가운데 손잡이: 끌어서 크기 조절, 놓으면 0 · 40 · 50 · 60 · 100% 중 가까운 곳에 딸깍 */
@Composable
private fun SplitHandle(ed: Ed, vertical: Boolean, total: androidx.compose.ui.unit.Dp) {
    val d = LocalDensity.current
    var live by remember { mutableStateOf<Float?>(null) }
    val m = Modifier.pointerInput(vertical, total) {
        val tp = total.toPx()
        detectDragGestures(
            onDragStart = { live = ed.ratio },
            onDragEnd = {
                val v = live ?: ed.ratio; live = null
                val nr = listOf(0f, .4f, .5f, .6f, 1f).minBy { kotlin.math.abs(it - v) }
                if (nr != ed.ratio) Y.haptic(12)
                ed.ratio = nr
                if (nr == 0f) ed.view = ed.right else if (nr == 1f) ed.view = ed.left
            },
            onDragCancel = { live = null },
        ) { ch, delta -> ch.consume(); val nv = ((live ?: ed.ratio) + (if (vertical) delta.y else delta.x) / tp).coerceIn(0f, 1f); live = nv; ed.ratio = nv.coerceIn(.08f, .92f) }
    }
    if (vertical) Box(m.fillMaxWidth().height(12.dp).background(Color(Paper.c().desk)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp, 4.dp).clip(CircleShape).background(if (live != null) T.acc else T.ink2.copy(alpha = .5f)))
    } else Box(m.fillMaxHeight().width(12.dp).background(Color(Paper.c().desk)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(4.dp, 40.dp).clip(CircleShape).background(if (live != null) T.acc else T.ink2.copy(alpha = .5f)))
    }
}
