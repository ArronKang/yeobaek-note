package kr.yeobaek.note.library

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kr.yeobaek.note.Nav
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Folder
import kr.yeobaek.note.data.NoteData
import kr.yeobaek.note.data.NoteMeta
import kr.yeobaek.note.data.Store
import kr.yeobaek.note.editor.GlassBtn
import kr.yeobaek.note.editor.glass
import kr.yeobaek.note.editor.gridColorPop
import kr.yeobaek.note.ink.Paper
import kr.yeobaek.note.ink.Templates
import kr.yeobaek.note.ui.Btn
import kr.yeobaek.note.ui.Ic
import kr.yeobaek.note.ui.Out
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.PItem
import kr.yeobaek.note.ui.PTitle
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.TipSpec
import kr.yeobaek.note.ui.txt

/* Apple iOS 27 UI 키트: 사이드바 폭 320·칸 44·고른 칸 #D6D6D8 캡슐, 파랑 #0088FF, 묶음 목록 칸 52·모서리 26 */
private val BLUE = Color(0xFF0088FF)
@Composable private fun sideBg() = if (Y.isDark()) Color(0xFF1C1C1E) else Color(0xFFE4E4E4)
@Composable private fun sideSel() = if (Y.isDark()) Color(0xFF3A3A3C) else Color(0xFFD6D6D8)
@Composable private fun searchBg() = if (Y.isDark()) Color(0xFF2C2C2E) else Color(0xFFD2D2D2)
@Composable private fun mainBg() = if (Y.isDark()) T.bg else Color.White
@Composable private fun groupedBg() = if (Y.isDark()) Color(0xFF000000) else Color(0xFFF2F2F7)
@Composable private fun cellBg() = if (Y.isDark()) Color(0xFF1C1C1E) else Color.White

/** 서재 상태 */
object L {
    var view by mutableStateOf(Store.flag("libview") ?: "recent")
    var mode by mutableStateOf(Store.flag("libmode") ?: "cover")
    var sort by mutableStateOf("updated")
    var q by mutableStateOf("")
    var searching by mutableStateOf(false)
    val sel = mutableStateListOf<String>()
    var selecting by mutableStateOf(false)
    var drawer by mutableStateOf(false)
    var side by mutableStateOf(Store.flag("libside") != "0")
    var foldersOpen by mutableStateOf(true)
    val collapsed = mutableStateListOf<String>()
    var lpAt = 0L
    var importPick: (() -> Unit)? = null

    fun folderNow(): String? = if (view.startsWith("f:")) view.substring(2) else null
    fun go(id: String) {
        view = id; q = ""; searching = false; selecting = false; sel.clear()
        if (!id.startsWith("f:") && id != "trash") Store.setFlag("libview", id)
    }
    fun exitSel() { selecting = false; sel.clear() }
    fun back(): Boolean {
        if (drawer) { drawer = false; return true }
        if (selecting) { exitSel(); return true }
        if (searching || q.isNotEmpty()) { q = ""; searching = false; return true }
        return false
    }
}

private fun descendants(id: String): List<String> = listOf(id) + Y.lib.folders.filter { it.parent == id }.flatMap { descendants(it.id) }

private fun viewTitle(): String = when {
    L.q.isNotEmpty() -> "검색 결과"
    L.view == "all" -> "노트"; L.view == "quiz" -> "문제지"; L.view == "pdf" -> "PDF"; L.view == "recent" -> "최근"
    L.view == "fav" -> "즐겨찾기"; L.view == "trash" -> "휴지통"; L.view == "wrong" -> "오답 모음"
    else -> Y.lib.folders.find { it.id == L.view.removePrefix("f:") }?.name ?: "모든 노트"
}

private fun visible(): List<NoteMeta> {
    val trashView = L.view == "trash" && L.q.isEmpty()
    var a = Y.lib.notes.filter { it.trashed == trashView && it.id != Wrong.ID }
    a = when {
        L.q.isNotEmpty() -> a.filter { !it.trashed && it.title.contains(L.q, true) }
        L.view == "fav" -> a.filter { it.fav }
        L.view == "all" -> a.filter { it.kind == null }
        L.view == "pdf" -> a.filter { it.kind == "pdf" }
        L.view == "quiz" -> a.filter { it.kind == "quiz" }
        L.view.startsWith("f:") -> { val ids = descendants(L.view.substring(2)); a.filter { it.folder in ids } }
        else -> a
    }
    val by = if (L.view == "recent" && L.q.isEmpty()) "updated" else L.sort
    val col = java.text.Collator.getInstance(java.util.Locale.KOREAN)
    a = when (by) { "name" -> a.sortedWith { x, y -> col.compare(x.title, y.title) }; "created" -> a.sortedByDescending { it.created }; else -> a.sortedByDescending { it.updated } }
    if (L.view == "recent" && L.q.isEmpty()) a = a.take(30)
    return a
}
private fun count(f: (NoteMeta) -> Boolean) = Y.lib.notes.count { !it.trashed && it.id != Wrong.ID && f(it) }

@Composable
fun LibraryScreen() {
    Y.libRev
    BoxWithConstraints(Modifier.fillMaxSize().background(mainBg())) {
        val wide = maxWidth >= 700.dp
        val showSide = wide && L.side
        Row(Modifier.fillMaxSize()) {
            val sw by animateFloatAsState(if (showSide) 1f else 0f, tween(380, easing = Out), label = "side")
            if (sw > .01f) Box(Modifier.width((320 * sw).dp).fillMaxHeight()) { Side(Modifier.width(320.dp).fillMaxHeight().graphicsLayer { translationX = -(1 - sw) * size.width }) }
            Main(wide, Modifier.weight(1f).fillMaxHeight())
        }
        // 좁은 화면: 서랍
        val a by animateFloatAsState(if (L.drawer && !wide) 1f else 0f, tween(380, easing = Out), label = "drawer")
        if (a > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f * a)).pointerInput(Unit) { detectTapGestures { L.drawer = false } })
            Side(Modifier.width(320.dp).fillMaxHeight().graphicsLayer { translationX = -(1 - a) * size.width })
        }
        if (L.selecting) SelTools(Modifier.align(Alignment.BottomEnd))
    }
}

/* ---------- 사이드바 (Sidebar) ---------- */
@Composable
private fun Side(modifier: Modifier) {
    Y.libRev
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.background(sideBg()).padding(top = top + 6.dp, bottom = bottom + 8.dp)) {
        // 선택 · 사이드바 접기
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(44.dp).glass(RoundedCornerShape(22.dp)).pointerInput(Unit) { detectTapGestures { if (L.selecting) L.exitSel() else L.selecting = true; Y.haptic(6) } }.padding(horizontal = 17.dp), contentAlignment = Alignment.Center) {
                BasicText(if (L.selecting) "완료" else "선택", style = txt(17.sp, 500, T.snow))
            }
            Spacer(Modifier.weight(1f))
            GlassBtn("sidebar", "사이드바 닫기") { L.side = false; L.drawer = false; Store.setFlag("libside", "0") }
        }
        // 검색 (캡슐 44)
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(searchBg()).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Ic("search", size = 19.dp, color = if (Y.isDark()) Color(0x99EBEBF5) else Color(0x993C3C43)); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (L.q.isEmpty()) BasicText("검색", style = txt(17.sp, 400, if (Y.isDark()) Color(0x99EBEBF5) else Color(0x993C3C43)))
                BasicTextField(L.q, { L.q = it; L.selecting = false; L.sel.clear() }, singleLine = true, textStyle = txt(17.sp, 400, T.snow), cursorBrush = SolidColor(BLUE))
            }
            if (L.q.isNotEmpty()) Box(Modifier.size(28.dp).pointerInput(Unit) { detectTapGestures { L.q = "" } }, contentAlignment = Alignment.Center) { Ic("wrongfill", size = 17.dp, color = Color(0x993C3C43)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            SItem("recent", "clock", "최근", null)
            SItem("all", "notes", "노트", count { it.kind == null })
            SItem("quiz", "quiz", "문제지", count { it.kind == "quiz" })
            SItem("pdf", "pdf", "PDF", count { it.kind == "pdf" })
            SItem("fav", "star", "즐겨찾기", count { it.fav })
            SItem("wrong", "wrong", "오답 모음", Wrong.total().takeIf { it > 0 })
            SItem("trash", "trash", "휴지통", Y.lib.notes.count { it.trashed }.takeIf { it > 0 })
            // 폴더 머리 (접기)
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp).fillMaxWidth().height(44.dp).pointerInput(Unit) { detectTapGestures { L.foldersOpen = !L.foldersOpen } }.padding(start = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicText("폴더", Modifier.weight(1f), style = txt(17.sp, 600, Color(0xFF8E8E93)))
                Ic("down", Modifier.graphicsLayer { rotationZ = if (L.foldersOpen) 0f else -90f }, size = 16.dp, color = BLUE, stroke = 2f)
            }
            if (L.foldersOpen) {
                FolderList()
                Row(Modifier.padding(start = 20.dp, end = 12.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).pointerInput(Unit) { detectTapGestures { addFolder(L.folderNow()) } }.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Ic("plus", size = 20.dp, color = BLUE) }
                    Spacer(Modifier.width(12.dp)); BasicText("새 폴더", style = txt(17.sp, 400, BLUE))
                }
            }
        }
        SItem("", "settings", "설정", null, onClick = { L.drawer = false; kr.yeobaek.note.settings.openSettings() })
    }
}

@Composable
private fun SItem(id: String, icon: String, label: String, cnt: Int?, depth: Int = 0, onClick: (() -> Unit)? = null) {
    val on = L.view == id && L.q.isEmpty() && id.isNotEmpty()
    Row(Modifier.padding(start = 20.dp + 14.dp * depth, end = 12.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) sideSel() else Color.Transparent)
        .pointerInput(id) { detectTapGestures { if (onClick != null) onClick() else { L.go(id); L.drawer = false } } }
        .padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Ic(icon, size = 21.dp, color = BLUE) }
        Spacer(Modifier.width(12.dp))
        BasicText(label, Modifier.weight(1f), style = txt(17.sp, 400, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        cnt?.let { BasicText("$it", style = txt(17.sp, 400, Color(0xFF8E8E93))) }
    }
}

/** 폴더: 꾹 눌러 끌면 순서 바꾸기, 꾹 누르고 그냥 떼면 메뉴(이름, 아이콘, 하위 폴더, 삭제) */
@Composable
private fun FolderList() {
    val d = LocalDensity.current
    val rowPx = with(d) { 44.dp.toPx() }
    var dragging by remember { mutableStateOf<Folder?>(null) }
    var dy by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableFloatStateOf(0f) }
    val flat = ArrayList<Pair<Folder, Int>>()
    fun walk(p: String?, depth: Int) { Y.lib.folders.filter { it.parent == p }.forEach { f -> flat.add(f to depth); if (f.id !in L.collapsed) walk(f.id, depth + 1) } }
    walk(null, 0)
    val dIdx = flat.indexOfFirst { it.first === dragging }
    val target = if (dIdx < 0) -1 else (dIdx + (dy / rowPx).let { kotlin.math.round(it).toInt() }).coerceIn(0, flat.size - 1)
    flat.forEachIndexed { i, (f, depth) ->
        val me = f === dragging
        val shift = when { dIdx < 0 || me -> 0f; i in (dIdx + 1)..target -> -rowPx; i in target until dIdx -> rowPx; else -> 0f }
        val sh by animateFloatAsState(shift, tween(200, easing = Out), label = "fs")
        var b by remember { mutableStateOf(Rect.Zero) }
        val on = L.view == "f:" + f.id && L.q.isEmpty()
        val n = count { it.folder in descendants(f.id) }
        val kids = Y.lib.folders.any { it.parent == f.id }
        Row(Modifier.zIndex(if (me) 3f else 0f).graphicsLayer { translationY = if (me) dy else sh; val s = if (me) 1.03f else 1f; scaleX = s; scaleY = s }
            .padding(start = 20.dp + 14.dp * depth, end = 12.dp).fillMaxWidth().height(44.dp)
            .then(if (me) Modifier.shadow(10.dp, RoundedCornerShape(22.dp)) else Modifier)
            .clip(RoundedCornerShape(22.dp)).background(if (me) cellBg() else if (on) sideSel() else Color.Transparent)
            .onGloballyPositioned { b = it.boundsInRoot() }
            .pointerInput(f.id) { detectTapGestures { L.go("f:" + f.id); L.drawer = false } }
            .pointerInput(f.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = f; dy = 0f; moved = 0f; Y.haptic(12) },
                    onDragEnd = {
                        val cur = dragging
                        if (cur != null && moved < 10.dp.toPx()) folderMenu(b, cur)
                        else if (cur != null && target >= 0 && target != dIdx) moveFolder(cur, flat.getOrNull(target)?.first, target > dIdx)
                        dragging = null; dy = 0f
                    },
                    onDragCancel = { dragging = null; dy = 0f },
                ) { ch, a -> ch.consume(); dy += a.y; moved += kotlin.math.abs(a.y) + kotlin.math.abs(a.x) }
            }
            .padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Ic(f.icon ?: "folder", size = 21.dp, color = BLUE) }
            Spacer(Modifier.width(12.dp))
            BasicText(f.name, Modifier.weight(1f), style = txt(17.sp, 400, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (kids) Box(Modifier.size(28.dp).pointerInput(f.id) { detectTapGestures { if (f.id in L.collapsed) L.collapsed.remove(f.id) else L.collapsed.add(f.id) } }, contentAlignment = Alignment.Center) {
                Ic("chevron", Modifier.graphicsLayer { rotationZ = if (f.id in L.collapsed) 0f else 90f }, size = 13.dp, color = Color(0xFF8E8E93), stroke = 2f)
            }
            BasicText("$n", style = txt(17.sp, 400, Color(0xFF8E8E93)))
        }
    }
}

/** 끌어 놓은 자리로: 같은 부모 안에서 순서만 바꿈 (다른 부모의 폴더 위면 그 폴더와 같은 부모로) */
private fun moveFolder(f: Folder, over: Folder?, after: Boolean) {
    if (over == null || over === f || over.id in descendants(f.id)) return
    val list = Y.lib.folders
    list.remove(f)
    f.parent = over.parent
    val i = list.indexOf(over).coerceAtLeast(0)
    list.add(if (after) i + 1 else i, f)
    Y.haptic(10); Y.saveLib()
}

private fun prompt(title: String, ok: String, value: String = "", placeholder: String = "", then: (String?) -> Unit) {
    var done = false
    Overlay.modal(width = 380.dp, onClose = { if (!done) { done = true; then(null) } }) { close ->
        var v by remember { mutableStateOf(value) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(60); runCatching { fr.requestFocus() } }
        Column {
            BasicText(title, style = txt(17.sp, 600, T.snow)); Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0x1F767680)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (v.isEmpty()) BasicText(placeholder, style = txt(17.sp, 400, T.ink2))
                BasicTextField(v, { if (it.length <= 40) v = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true, textStyle = txt(17.sp, 400, T.snow), cursorBrush = SolidColor(BLUE))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Btn("취소", "ghost") { done = true; close(); then(null) }
                Spacer(Modifier.width(4.dp))
                Btn(ok) { if (v.isNotBlank()) { done = true; close(); then(v.trim()) } }
            }
        }
    }
}

private fun addFolder(parent: String?) {
    prompt("새 폴더", "만들기", placeholder = "폴더 이름") { name ->
        name ?: return@prompt
        val f = Folder(Y.uid(), name, parent)
        Y.lib.folders.add(f); Y.saveLib(); L.view = "f:" + f.id; L.q = ""; if (parent != null) L.collapsed.remove(parent)
    }
}

/** 폴더 아이콘 (SF Symbols) */
private val FOLDER_ICONS = listOf("folder", "folderfill", "book", "library", "notes", "quiz", "star", "heart", "flag", "bookmark", "cap", "brain",
    "atom", "fx", "globe", "leaf", "music", "palette", "pencil", "ruler", "clock", "tray", "wrong", "magnify")

private fun folderMenu(r: Rect, f: Folder) {
    Overlay.popover("fm" + f.id, r, 240.dp, "left") {
        PItem("이름 바꾸기", "pencil") { Overlay.closePop(); prompt("폴더 이름", "바꾸기", f.name) { n -> if (n != null) { f.name = n; Y.saveLib() } } }
        PItem("아이콘 바꾸기", f.icon ?: "folder") { Overlay.closePop(); Nav.scope.launchIcon(r, f) }
        PItem("하위 폴더 추가", "folderplus") { Overlay.closePop(); addFolder(f.id) }
        PItem("폴더 삭제", "trash", color = T.danger) {
            Overlay.closePop()
            Overlay.confirm("‘${f.name}’ 폴더를 삭제할까요?", "폴더 삭제", danger = true, text = "안의 노트는 지워지지 않아요") { ok ->
                if (!ok) return@confirm
                val ids = descendants(f.id)
                Y.lib.notes.forEach { if (it.folder in ids) it.folder = null }
                Y.lib.folders.removeAll { it.id in ids }; Y.saveLib(); L.go("all")
            }
        }
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchIcon(r: Rect, f: Folder) = this.launch {
    delay(160)
    Overlay.popover("fi" + f.id, r, 300.dp, "left") {
        PTitle("폴더 아이콘")
        FOLDER_ICONS.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { ic ->
                    val on = (f.icon ?: "folder") == ic
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) BLUE.copy(alpha = .14f) else Color.Transparent)
                        .pointerInput(ic) { detectTapGestures { f.icon = ic; Y.saveLib(); Y.haptic(6); Overlay.closePop() } }, contentAlignment = Alignment.Center) {
                        Ic(ic, size = 22.dp, color = BLUE)
                    }
                }
            }
        }
    }
}

/* ---------- 본문 ---------- */
@Composable
private fun Main(wide: Boolean, modifier: Modifier) {
    Y.libRev
    val notes = visible()
    val trash = L.view == "trash" && L.q.isEmpty()
    val sys = WindowInsets.systemBars.asPaddingValues()
    Column(modifier.background(if (L.q.isNotEmpty() || L.view == "wrong") groupedBg() else mainBg()).padding(top = sys.calculateTopPadding() + 6.dp)) {
        // 머리 (Toolbars/iPad/Top: 높이 54, 버튼 44, 간격 12, 좌우 20)
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!wide) { GlassBtn("sidebar", "메뉴") { L.drawer = true }; Spacer(Modifier.width(12.dp)) }
            else if (!L.side) { GlassBtn("sidebar", "사이드바 열기") { L.side = true; Store.setFlag("libside", "1") }; Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                BasicText(if (L.selecting) "${L.sel.size}개 선택됨" else viewTitle(), style = txt(17.sp, 600, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    L.q.isNotEmpty() -> "\"${L.q}\""
                    L.view == "wrong" -> "틀리거나 애매한 문제 ${Wrong.total()}개"
                    else -> listOfNotNull(count { it.kind == null }.takeIf { it > 0 }?.let { "노트 ${it}개" }, count { it.kind == "quiz" }.takeIf { it > 0 }?.let { "문제지 ${it}개" }).joinToString(", ")
                }
                if (sub.isNotEmpty()) BasicText(sub, style = txt(13.sp, 400, Color(0xFF8E8E93)), maxLines = 1)
            }
            Actions(trash, notes)
        }
        val rise = remember(L.view, L.q.isEmpty()) { Animatable(0f) }
        LaunchedEffect(L.view, L.q.isEmpty()) { rise.animateTo(1f, tween(420, easing = Out)) }
        Box(Modifier.weight(1f).graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 10.dp.toPx() }) {
            when {
                L.q.isNotEmpty() -> SearchResults(notes)
                L.view == "wrong" -> WrongView()
                notes.isEmpty() -> Empty(trash)
                else -> LazyVerticalGrid(GridCells.Adaptive(136.dp), Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = sys.calculateBottomPadding() + 90.dp)) {
                    items(notes, key = { it.id }) { n -> CoverItem(n, trash) }
                }
            }
        }
    }
}

@Composable
private fun Actions(trash: Boolean, notes: List<NoteMeta>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (L.selecting) {
            Box(Modifier.height(44.dp).glass(RoundedCornerShape(22.dp)).pointerInput(notes) { detectTapGestures { L.sel.clear(); L.sel.addAll(notes.map { it.id }) } }.padding(horizontal = 17.dp), contentAlignment = Alignment.Center) {
                BasicText("모두 선택", style = txt(17.sp, 500, T.snow))
            }
            Box(Modifier.height(44.dp).shadow(8.dp, RoundedCornerShape(22.dp)).background(BLUE, RoundedCornerShape(22.dp)).pointerInput(Unit) { detectTapGestures { L.exitSel() } }.padding(horizontal = 17.dp), contentAlignment = Alignment.Center) {
                BasicText("완료", style = txt(17.sp, 600, Color.White))
            }
            return@Row
        }
        if (L.view != "recent" && L.view != "wrong" && !trash && L.q.isEmpty()) GlassBtn("sort", "정렬") { r ->
            Overlay.popover("sort", r, 190.dp, "right") {
                PTitle("정렬")
                listOf("updated" to "수정일", "name" to "이름", "created" to "만든 날").forEach { (id, n) -> PItem(n, on = L.sort == id) { L.sort = id; Overlay.closePop() } }
            }
        }
        if (trash) { if (notes.isNotEmpty()) GlassBtn("binx", "휴지통 비우기", tint = T.danger) { emptyTrash() } }
        else if (L.q.isEmpty()) {
            GlassBtn("importf", "가져오기") { L.importPick?.invoke() }
            // 주 버튼 (Glass Prominent): 새 노트
            var down by remember { mutableStateOf(false) }
            val s by animateFloatAsState(if (down) .9f else 1f, tween(if (down) 80 else 220, easing = Out), label = "p")
            Box(Modifier.size(44.dp).graphicsLayer { scaleX = s; scaleY = s }.shadow(10.dp, CircleShape, ambientColor = BLUE, spotColor = BLUE).background(BLUE, CircleShape)
                .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { newNoteModal() }) }, contentAlignment = Alignment.Center) {
                Ic("compose", size = 21.dp, color = Color.White)
            }
        } else Box(Modifier.height(44.dp).glass(RoundedCornerShape(22.dp)).pointerInput(Unit) { detectTapGestures { L.q = ""; L.searching = false } }.padding(horizontal = 17.dp), contentAlignment = Alignment.Center) {
            BasicText("취소", style = txt(17.sp, 500, T.snow))
        }
    }
}

/** 선택 모드 아래 도구 (Toolbars/iPad/Bottom: 유리 캡슐 44) */
@Composable
private fun SelTools(modifier: Modifier) {
    val trash = L.view == "trash"
    val sys = WindowInsets.systemBars.asPaddingValues()
    val none = L.sel.isEmpty()
    Row(modifier.padding(end = 20.dp, bottom = sys.calculateBottomPadding() + 20.dp).height(48.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        @Composable fun B(icon: String, label: String, c: Color = T.snow, f: (Rect) -> Unit) {
            var b by remember { mutableStateOf(Rect.Zero) }
            Row(Modifier.height(40.dp).onGloballyPositioned { b = it.boundsInRoot() }.clip(RoundedCornerShape(20.dp)).graphicsLayer { alpha = if (none) .35f else 1f }
                .pointerInput(label, none) { detectTapGestures(onLongPress = { Overlay.tip = TipSpec(System.nanoTime(), label, b) }, onTap = { if (!none) f(b) }) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Ic(icon, size = 20.dp, color = c); Spacer(Modifier.width(6.dp)); BasicText(label, style = txt(15.sp, 600, c))
            }
        }
        if (trash) {
            B("restore", "복원") { L.sel.forEach { id -> Y.lib.notes.find { it.id == id }?.let { it.trashed = false; it.trashedAt = null } }; Y.saveLib(); L.exitSel() }
            B("trash", "영구 삭제", T.danger) {
                Overlay.confirm("${L.sel.size}개를 영구 삭제할까요?", "영구 삭제", danger = true) { ok ->
                    if (ok) { val ids = L.sel.toSet(); Y.lib.notes.filter { it.id in ids }.forEach(Y::purge); Y.lib.notes.removeAll { it.id in ids }; Y.saveLib(); L.exitSel() }
                }
            }
        } else {
            B("folder", "이동") { r -> movePop(r) }
            B("star", "즐겨찾기") { val all = L.sel.all { id -> Y.lib.notes.find { it.id == id }?.fav == true }; L.sel.forEach { id -> Y.lib.notes.find { it.id == id }?.fav = !all }; Y.saveLib(); L.exitSel() }
            B("dup", "복제") { dupSel() }
            B("cover", "표지") { r -> coverSel(r) }
            B("trash", "삭제", T.danger) { trashSel() }
        }
    }
}

private fun dupSel() {
    val ids = L.sel.toList()
    ids.forEach { id ->
        val n = Y.lib.notes.find { it.id == id } ?: return@forEach
        val nid = Y.uid()
        listOf("n", "q", "qs").forEach { k -> Store.getRaw("$k/$id.json")?.let { Store.setRaw("$k/$nid.json", it) } }
        if (n.kind == "pdf") Store.file("blobs/$id.pdf").takeIf { it.exists() }?.copyTo(Store.file("blobs/$nid.pdf"), true)
        val key = if (n.kind == "pdf") (n.key ?: id) + ":" + nid else n.key
        if (n.kind == "pdf") n.key?.let { k -> Store.getRaw("p/${Y.keyFile(k)}.json")?.let { Store.setRaw("p/${Y.keyFile(key!!)}.json", it) } }
        val now = System.currentTimeMillis()
        Y.lib.notes.add(0, NoteMeta(nid, Y.clean(n.title) + " 사본", n.cover, n.kind, n.tpl, n.folder, false, false, null, now, now, n.pageCount, n.score, key, n.thumb))
    }
    Y.saveLib(); L.exitSel(); Y.haptic(10)
    Overlay.toast("${ids.size}개를 복제했어요")
}

private fun coverSel(r: Rect) {
    Overlay.popover("coversel", r, 300.dp, "right") {
        PTitle("표지")
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (listOf("auto") + Y.COVERS.map { it.id }).forEach { id ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (id == "auto") Color.White else Color(Y.cover(id).c)).border(.5.dp, T.hair, CircleShape)
                    .pointerInput(id) { detectTapGestures { L.sel.forEach { sid -> Y.lib.notes.find { it.id == sid }?.cover = id }; Y.saveLib(); Overlay.closePop(); L.exitSel() } }, contentAlignment = Alignment.Center) {
                    if (id == "auto") Ic("doc", size = 15.dp, color = Color(0xFF8E8E93))
                }
            }
        }
    }
}

/* ---------- 검색 결과: 제목 먼저, 그 아래 노트 안 글자 ---------- */
private class Hit(val n: NoteMeta, val where: String, val text: String)

@Composable
private fun SearchResults(titles: List<NoteMeta>) {
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    val q = L.q
    LaunchedEffect(q) {
        delay(220)
        hits = withContext(Dispatchers.IO) { contentSearch(q) }
    }
    val sys = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = sys.calculateBottomPadding() + 40.dp)) {
        if (titles.isEmpty() && hits.isEmpty()) item { Empty(false) }
        if (titles.isNotEmpty()) {
            item { ListHead("제목 (${titles.size})") }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cellBg())) {
                    titles.forEachIndexed { i, n ->
                        if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(.5.dp).background(T.hair))
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pointerInput(n.id) { detectTapGestures { Nav.open(n) } }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Ic(kindIcon(n), size = 20.dp, color = BLUE) }
                            Spacer(Modifier.width(12.dp))
                            BasicText(hl(Y.clean(n.title), q), Modifier.weight(1f), style = txt(17.sp, 400, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(Y.fmtDate(n.updated), style = txt(15.sp, 400, Color(0xFF8E8E93)))
                        }
                    }
                }
            }
        }
        if (hits.isNotEmpty()) {
            item { Spacer(Modifier.height(26.dp)); ListHead("노트 안 글자 (${hits.size})") }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cellBg())) {
                    hits.forEachIndexed { i, h ->
                        if (i > 0) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(.5.dp).background(T.hair))
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).pointerInput(h) { detectTapGestures { Nav.open(h.n) } }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(40.dp)) { Cover(h.n, small = true) }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                // 제목이 먼저, 그 아래 찾은 글
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(Y.clean(h.n.title), Modifier.weight(1f, fill = false), style = txt(17.sp, 400, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(6.dp)); BasicText(h.where, style = txt(13.sp, 400, Color(0xFF8E8E93)))
                                }
                                BasicText(hl(h.text, q), style = txt(13.sp, 400, Color(0xFF8E8E93)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ListHead(t: String) = BasicText(t, Modifier.padding(start = 20.dp, bottom = 8.dp), style = txt(17.sp, 600, Color(0xFF8E8E93)))

private fun hl(s: String, q: String) = buildAnnotatedString {
    val i = s.indexOf(q, ignoreCase = true)
    if (i < 0 || q.isEmpty()) { append(s); return@buildAnnotatedString }
    append(s.substring(0, i)); withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight(700))) { append(s.substring(i, i + q.length)) }; append(s.substring(i + q.length))
}

private fun kindIcon(n: NoteMeta) = when (n.kind) { "pdf" -> "pdf"; "quiz" -> "quiz"; else -> "notes" }

/** 노트의 글상자와 문제지 글에서 찾기 */
private fun contentSearch(q: String): List<Hit> {
    if (q.isBlank()) return emptyList()
    val out = ArrayList<Hit>()
    fun snip(t: String): String { val i = t.indexOf(q, ignoreCase = true); val a = (i - 18).coerceAtLeast(0); return (if (a > 0) "…" else "") + t.substring(a).replace('\n', ' ').take(80) }
    for (n in Y.lib.notes) {
        if (n.trashed || n.id == Wrong.ID) continue
        if (out.size > 60) break
        when (n.kind) {
            null -> Store.get("n/${n.id}.json", NoteData.serializer())?.pages?.forEachIndexed { pi, pg ->
                pg.texts.firstOrNull { it.t.contains(q, true) }?.let { out.add(Hit(n, "${pi + 1}쪽", snip(it.t))) }
            }
            "quiz" -> {
                val raw = Store.getJson("q/${n.id}.json") ?: continue
                val strs = ArrayList<String>()
                fun walk(e: JsonElement) { when (e) { is JsonObject -> e.forEach { (k, v) -> if (k != "img" && k != "src") walk(v) }; is JsonArray -> e.forEach { walk(it) }; is JsonPrimitive -> if (e.isString) strs.add(e.content); else -> {} } }
                walk(raw)
                strs.firstOrNull { it.contains(q, true) }?.let { out.add(Hit(n, "문제지", snip(it))) }
            }
        }
    }
    return out
}

/* ---------- 오답 모음 ---------- */
@Composable
private fun WrongView() {
    val srcs = remember(Y.libRev) { Wrong.scan() }
    val total = srcs.sumOf { it.wrong.size }
    val sys = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = sys.calculateBottomPadding() + 40.dp)) {
        if (total == 0) { item { Empty(false, wrong = true) }; return@LazyColumn }
        item {
            // 모음 열기: 모든 문제지의 틀린·애매한 문제가 한 문제지로
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cellBg()).pointerInput(Unit) { detectTapGestures { Wrong.open() } }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(Y.cover("terra").c)), contentAlignment = Alignment.Center) { Ic("wrong", size = 26.dp, color = Color.White) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    BasicText("오답 모음 풀기", style = txt(17.sp, 600, T.snow))
                    BasicText("${srcs.size}개 문제지에서 ${total}문항", style = txt(15.sp, 400, Color(0xFF8E8E93)))
                }
                Ic("chevron", size = 15.dp, color = Color(0xFFC7C7CC), stroke = 2f)
            }
        }
        item { Spacer(Modifier.height(26.dp)); ListHead("문제지별") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cellBg())) {
                srcs.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(.5.dp).background(T.hair))
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).pointerInput(s.meta.id) { detectTapGestures { Nav.open(s.meta) } }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(40.dp)) { Cover(s.meta, small = true) }
                        Spacer(Modifier.width(16.dp))
                        BasicText(Y.clean(s.meta.title), Modifier.weight(1f), style = txt(17.sp, 400, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText("${s.wrong.size}문항", style = txt(17.sp, 400, Color(0xFF8E8E93)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(trash: Boolean, wrong: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val (ic, t) = when {
            wrong -> "rightfill" to "틀린 문제가 없어요"
            L.q.isNotEmpty() -> "search" to "검색 결과가 없어요"
            trash -> "trash" to "휴지통이 비어 있어요"
            L.view == "fav" -> "star" to "즐겨찾기한 노트가 없어요"
            else -> "notes" to "아직 노트가 없어요"
        }
        Ic(ic, size = 44.dp, color = Color(0xFF8E8E93))
        Spacer(Modifier.height(16.dp))
        BasicText(t, style = txt(20.sp, 600, T.snow))
        if (!trash && !wrong && L.q.isEmpty() && L.view != "fav") {
            Spacer(Modifier.height(20.dp))
            Row { Btn("새 노트") { newNoteModal() }; Spacer(Modifier.width(8.dp)); Btn("가져오기", "ghost") { L.importPick?.invoke() } }
        }
    }
}

/* ---------- 표지 ---------- */
private val thumbCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()
private fun thumbOf(n: NoteMeta): androidx.compose.ui.graphics.ImageBitmap? = n.thumb?.let { t ->
    val f = Store.file("thumbs/$t"); val k = t + ":" + f.lastModified()
    thumbCache.getOrPut(k) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
}

/**
 * 표지: 색 표지(제목 라벨), PDF는 첫 쪽, '자동'은 노트 첫 쪽 그대로(필기까지). 첫 쪽 그림이 아직 없으면 양식 종이 위에 제목·날짜를 적은 쪽.
 */
@Composable
fun Cover(n: NoteMeta, small: Boolean = false, modifier: Modifier = Modifier) {
    val auto = n.cover == "auto" && n.kind == null
    val th = if (n.kind == "pdf" || auto) thumbOf(n) else null
    val c = Color(Y.cover(n.cover).c)
    val r = if (small) 4.dp else 10.dp
    Box(modifier.then(if (small) Modifier.width(40.dp) else Modifier.fillMaxWidth()).aspectRatio(3f / 4f)
        .then(if (small) Modifier else Modifier.shadow(3.dp, RoundedCornerShape(r)))
        .clip(RoundedCornerShape(r)).background(if (th != null || auto) Color.White else c)
        .border(.5.dp, if (T.dark) Color(0x2EF8F9FA) else Color(0x14000000), RoundedCornerShape(r))) {
        val dk = T.dark
        if (th != null) Image(th, null, Modifier.fillMaxSize().graphicsLayer { alpha = if (dk) .9f else 1f }, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else if (auto) {
            TplMini(n.tpl ?: "lined", Modifier.fillMaxSize())
            if (!small) Column(Modifier.padding(10.dp)) {
                BasicText(Y.clean(n.title).ifEmpty { "제목 없음" }, style = txt(12.sp, 700, Color(0xFF1C1C1E)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(Y.fmtDate(n.created), style = txt(9.sp, 500, Color(0xFF8E8E93)))
            }
        }
        if (small) return@Box
        if (n.kind == "pdf") {
            if (!L.selecting) BasicText("PDF", Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xB81C1C1E), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp), style = txt(11.sp, 700, Color.White))
            return@Box
        }
        if (auto || th != null) return@Box
        // 색 표지: 왼쪽 제본 그늘 + 아래 제목
        Box(Modifier.width(6.dp).fillMaxHeight().background(Color.Black.copy(alpha = .14f)))
        BasicText(Y.clean(n.title).ifEmpty { "제목 없음" }, Modifier.align(Alignment.BottomStart).padding(12.dp), style = txt(15.sp, 600, Color(0xFFF8F9FA)).copy(lineHeight = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (n.kind == "quiz") {
            n.score?.let { BasicText(it, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = .92f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp), style = txt(11.sp, 700, Color(0xFF1C1C1E))) }
                ?: Box(Modifier.align(Alignment.TopEnd).padding(9.dp).graphicsLayer { alpha = .75f }) { Ic("quiz", size = 16.dp, color = Color(0xFFF8F9FA)) }
        }
    }
}

@Composable
private fun bindItem(n: NoteMeta, trash: Boolean, onDown: (Boolean) -> Unit = {}): Modifier = Modifier.pointerInput(n.id, trash, L.selecting) {
    detectTapGestures(
        onPress = { onDown(true); tryAwaitRelease(); onDown(false) },
        onLongPress = { Y.haptic(12); L.lpAt = System.currentTimeMillis(); L.selecting = true; if (n.id !in L.sel) L.sel.add(n.id) },
        onTap = {
            if (System.currentTimeMillis() - L.lpAt < 700) return@detectTapGestures
            if (L.selecting) { if (n.id in L.sel) L.sel.remove(n.id) else L.sel.add(n.id); return@detectTapGestures }
            if (trash) { Overlay.toast("복원한 뒤 열 수 있어요"); return@detectTapGestures }
            Nav.open(n)
        })
}

@Composable
private fun CoverItem(n: NoteMeta, trash: Boolean) {
    val sel = n.id in L.sel
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) .97f else 1f, tween(if (down) 100 else 220, easing = Out), label = "c")
    Column(bindItem(n, trash) { down = it }) {
        Box(Modifier.scale(s)) {
            Cover(n)
            if (sel) Box(Modifier.matchParentSize().border(3.dp, BLUE, RoundedCornerShape(10.dp)))
            if (n.fav && !L.selecting) Box(Modifier.padding(start = 12.dp, top = 9.dp)) { Ic("starfill", size = 14.dp, color = Color.White) }
            // 선택 표시 (Rows/Accessories 체크 원)
            if (L.selecting) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(26.dp).clip(CircleShape)
                .background(if (sel) BLUE else Color.White.copy(alpha = .7f)).border(if (sel) 2.dp else 1.5.dp, if (sel) Color.White else Color(0x40000000), CircleShape), contentAlignment = Alignment.Center) {
                if (sel) Ic("check", size = 14.dp, color = Color.White, stroke = 2.4f)
            }
        }
        Spacer(Modifier.height(10.dp))
        BasicText(Y.clean(n.title).ifEmpty { "제목 없음" }, style = txt(15.sp, 600, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        BasicText(Y.fmtDate(n.updated), style = txt(13.sp, 400, Color(0xFF8E8E93)))
    }
}

private fun trashSel() {
    val ids = L.sel.toList(); val one = if (ids.size == 1) Y.lib.notes.find { it.id == ids[0] } else null
    Overlay.confirm(if (one != null) "‘${one.title}’를 휴지통으로 옮길까요?" else "${ids.size}개를 휴지통으로 옮길까요?", "휴지통으로 이동") { ok ->
        if (!ok) return@confirm
        val now = System.currentTimeMillis()
        ids.forEach { id -> Y.lib.notes.find { it.id == id }?.let { it.trashed = true; it.trashedAt = now } }
        Y.saveLib(); L.exitSel(); Nav.pruneTabs()
        Overlay.toast("휴지통으로 옮겼어요", "되돌리기", 4500) { ids.forEach { id -> Y.lib.notes.find { it.id == id }?.let { it.trashed = false; it.trashedAt = null } }; Y.saveLib() }
    }
}

private fun emptyTrash() {
    Overlay.confirm("휴지통을 비울까요?", "휴지통 비우기", danger = true) { ok ->
        if (!ok) return@confirm
        Y.lib.notes.filter { it.trashed }.forEach(Y::purge); Y.lib.notes.removeAll { it.trashed }; Y.saveLib()
    }
}

private fun movePop(r: Rect) {
    Overlay.popover("move", r, 240.dp, "right") {
        PTitle("이동할 폴더")
        fun put(fid: String?) { L.sel.forEach { id -> Y.lib.notes.find { it.id == id }?.folder = fid }; Y.saveLib(); Overlay.closePop(); L.exitSel() }
        PItem("폴더 없음", "tray") { put(null) }
        @Composable fun walk(p: String?, d: Int) { Y.lib.folders.filter { it.parent == p }.forEach { f -> PItem(f.name, f.icon ?: "folder", indent = (14 * d).dp) { put(f.id) }; walk(f.id, d + 1) } }
        walk(null, 0)
    }
}

/* ---------- 새 노트 (Sheets/iPad: 모서리 34, 머리 닫기 · 제목 · 완료) ---------- */
fun newNoteModal() {
    val auto = Nav.nextTitle()
    Overlay.modal(width = 640.dp) { close ->
        var title by remember { mutableStateOf("") }
        var cover by remember { mutableStateOf(Nav.nextCover()) }
        var tpl by remember { mutableStateOf("lined") }
        var folder by remember { mutableStateOf(L.folderNow()) }
        var moreTpl by remember { mutableStateOf(false) }
        val fr = remember { FocusRequester() }
        fun go() {
            val meta = Nav.newNote(title.trim().ifEmpty { auto }, cover, tpl, folder)
            if (L.view !in listOf("all", "recent") && !L.view.startsWith("f:")) L.go("all")
            L.q = ""; L.searching = false
            close(); Nav.open(meta)
        }
        Column {
            // 머리
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                GlassBtn("close", "닫기", Modifier.align(Alignment.CenterStart)) { close() }
                BasicText("새 노트", Modifier.align(Alignment.Center), style = txt(17.sp, 600, T.snow))
                Box(Modifier.align(Alignment.CenterEnd).size(44.dp).shadow(10.dp, CircleShape, ambientColor = BLUE, spotColor = BLUE).background(BLUE, CircleShape)
                    .pointerInput(Unit) { detectTapGestures { go() } }, contentAlignment = Alignment.Center) { Ic("check", size = 20.dp, color = Color.White, stroke = 2.2f) }
            }
            Spacer(Modifier.height(18.dp))
            // 이름
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("이름", style = txt(17.sp, 400, T.snow))
                Spacer(Modifier.width(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (title.isEmpty()) BasicText(auto, style = txt(17.sp, 400, Color(0xFF8E8E93)))
                    BasicTextField(title, { if (it.length <= 40) title = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                        textStyle = txt(17.sp, 400, T.snow).copy(textAlign = TextAlign.End), cursorBrush = SolidColor(BLUE),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { go() }))
                }
            }
            // 표지: 자동(첫 쪽), 8색, 사용자 색, + (Apple 색 격자)
            SheetHead("표지")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                @Composable fun Dot(id: String, content: @Composable () -> Unit = {}) {
                    val on = cover == id
                    Box(Modifier.size(40.dp).border(if (on) 2.5.dp else 0.dp, if (on) BLUE else Color.Transparent, CircleShape).padding(5.dp).clip(CircleShape)
                        .background(if (id == "auto") Color.White else Color(Y.cover(id).c)).border(.5.dp, Color(0x26000000), CircleShape)
                        .pointerInput(id) { detectTapGestures { cover = id; Y.haptic(6) } }, contentAlignment = Alignment.Center) { content() }
                }
                Dot("auto") { Ic("doc", size = 15.dp, color = Color(0xFF8E8E93)) }
                Y.COVERS.forEach { Dot(it.id) }
                (Y.lib.notes.map { it.cover } + cover).filter { it.startsWith("#") }.distinct().forEach { Dot(it) }
                var b by remember { mutableStateOf(Rect.Zero) }
                Box(Modifier.size(40.dp).onGloballyPositioned { b = it.boundsInRoot() }.padding(5.dp).clip(CircleShape).background(Color(0x1F787880))
                    .pointerInput(Unit) { detectTapGestures { gridColorPop(b, cover, "표지 색") { c -> cover = c } } }, contentAlignment = Alignment.Center) { Ic("plus", size = 16.dp, color = T.snow, stroke = 2f) }
            }
            if (cover.startsWith("#") && Y.COVERS.none { it.id == cover }) BasicText("사용자 색 $cover", Modifier.padding(start = 10.dp, top = 6.dp), style = txt(13.sp, 400, Color(0xFF8E8E93)))
            // 양식: 줄, 모눈, 점, 빈 페이지 + 더보기
            SheetHead("양식")
            val basic = listOf("lined", "grid", "dot", "blank")
            val list = if (moreTpl) Y.TEMPLATES else Y.TEMPLATES.filter { it.first in basic }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                list.forEach { (id, name) ->
                    val on = tpl == id
                    Column(Modifier.width(108.dp).pointerInput(id) { detectTapGestures { tpl = id; Y.haptic(6) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                        TplMini(id, Modifier.fillMaxWidth().aspectRatio(1 / 1.4142f).clip(RoundedCornerShape(10.dp)).border(if (on) 2.5.dp else .5.dp, if (on) BLUE else T.hair, RoundedCornerShape(10.dp)))
                        Spacer(Modifier.height(8.dp))
                        BasicText(name, style = txt(13.sp, if (on) 600 else 400, if (on) BLUE else Color(0xFF8E8E93)))
                    }
                }
                if (!moreTpl) Column(Modifier.width(108.dp).pointerInput(Unit) { detectTapGestures { moreTpl = true } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1 / 1.4142f).clip(RoundedCornerShape(10.dp)).background(Color(0x14787880)), contentAlignment = Alignment.Center) { Ic("more", size = 24.dp, color = T.snow) }
                    Spacer(Modifier.height(8.dp))
                    BasicText("템플릿 더보기", style = txt(13.sp, 400, Color(0xFF8E8E93)))
                }
            }
            Spacer(Modifier.height(18.dp))
            // 폴더
            var fb by remember { mutableStateOf(Rect.Zero) }
            Row(Modifier.fillMaxWidth().height(52.dp).onGloballyPositioned { fb = it.boundsInRoot() }.clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures {
                        Overlay.popover("nnf", fb, 240.dp, "right") {
                            PItem("없음", "tray", on = folder == null) { folder = null; Overlay.closePop() }
                            Y.lib.folders.forEach { f -> PItem(f.name, f.icon ?: "folder", on = folder == f.id) { folder = f.id; Overlay.closePop() } }
                        }
                    }
                }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("폴더", Modifier.weight(1f), style = txt(17.sp, 400, T.snow))
                BasicText(Y.lib.folders.find { it.id == folder }?.name ?: "없음", style = txt(17.sp, 400, Color(0xFF8E8E93)))
                Spacer(Modifier.width(6.dp)); Ic("updown", size = 14.dp, color = Color(0xFF8E8E93), stroke = 2f)
            }
        }
    }
}

@Composable private fun SheetHead(t: String) = BasicText(t, Modifier.padding(start = 10.dp, top = 18.dp, bottom = 10.dp), style = txt(17.sp, 600, Color(0xFF8E8E93)))

/** 양식 미리보기: 줄·모눈·점은 무늬가 보이게 크게 */
@Composable
fun TplMini(id: String, modifier: Modifier) {
    val pc = Paper.c()
    androidx.compose.foundation.Canvas(modifier.background(Color(pc.paper))) {
        Templates.draw(drawContext.canvas.nativeCanvas, id, size.width, size.height, 1f, pc)
    }
}
