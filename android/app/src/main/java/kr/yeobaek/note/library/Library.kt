package kr.yeobaek.note.library

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.yeobaek.note.Nav
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Folder
import kr.yeobaek.note.data.NoteMeta
import kr.yeobaek.note.data.Store
import kr.yeobaek.note.ink.Paper
import kr.yeobaek.note.ink.Templates
import kr.yeobaek.note.ui.Btn
import kr.yeobaek.note.ui.Ic
import kr.yeobaek.note.ui.IconBtn
import kr.yeobaek.note.ui.Out
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.PItem
import kr.yeobaek.note.ui.PTitle
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.txt

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
        if (searching) { q = ""; searching = false; return true }
        return false
    }
}

private fun descendants(id: String): List<String> = listOf(id) + Y.lib.folders.filter { it.parent == id }.flatMap { descendants(it.id) }

private fun viewTitle(): String = when {
    L.q.isNotEmpty() -> "검색 결과"
    L.view == "all" -> "노트"; L.view == "quiz" -> "문제지"; L.view == "pdf" -> "PDF"; L.view == "recent" -> "최근"
    L.view == "fav" -> "즐겨찾기"; L.view == "trash" -> "휴지통"
    else -> Y.lib.folders.find { it.id == L.view.removePrefix("f:") }?.name ?: "모든 노트"
}

private fun visible(): List<NoteMeta> {
    val trashView = L.view == "trash" && L.q.isEmpty()
    var a = Y.lib.notes.filter { it.trashed == trashView }
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
private fun count(f: (NoteMeta) -> Boolean) = Y.lib.notes.count { !it.trashed && f(it) }

@Composable
fun LibraryScreen() {
    Y.libRev
    BoxWithConstraints(Modifier.fillMaxSize().background(T.bg)) {
        val wide = maxWidth >= 820.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Side(Modifier.width(260.dp).fillMaxHeight())
            Main(wide, Modifier.weight(1f).fillMaxHeight())
        }
        // 좁은 화면: 서랍
        val a by animateFloatAsState(if (L.drawer && !wide) 1f else 0f, tween(380, easing = Out), label = "drawer")
        if (a > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color(0x38212529).copy(alpha = .22f * a)).pointerInput(Unit) { detectTapGestures { L.drawer = false } })
            Side(Modifier.width(280.dp).fillMaxHeight().graphicsLayer { translationX = -(1 - a) * size.width }.background(T.bg))
        }
    }
}

/* ---------- 사이드바 ---------- */
@Composable
private fun Side(modifier: Modifier) {
    Y.libRev
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier.background(T.bg).border(.5.dp, T.hair).padding(top = top + 36.dp, start = 12.dp, end = 12.dp, bottom = 16.dp).verticalScroll(rememberScrollState())) {
        BasicText("여백", Modifier.padding(start = 12.dp, bottom = 20.dp), style = txt(20.sp, 600, T.snow, -.01f))
        // 검색
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(36.dp).border(0.dp, Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
            Ic("search", size = 18.dp); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (L.q.isEmpty()) BasicText("노트 검색", style = txt(15.sp, 400, T.ink2))
                BasicTextField(L.q, { L.q = it; L.selecting = false; L.sel.clear() }, singleLine = true, textStyle = txt(15.sp, 400, T.snow), cursorBrush = SolidColor(T.acc))
            }
        }
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(.5.dp).background(T.hair))
        Spacer(Modifier.height(16.dp))
        SItem("recent", "clock", "최근")
        SItem("all", "notes", "노트")
        SItem("quiz", "quiz", "문제지")
        SItem("pdf", "pdf", "PDF")
        SItem("fav", "star", "즐겨찾기")
        SItem("trash", "trash", "휴지통")
        Row(Modifier.padding(start = 12.dp, top = 28.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("폴더", Modifier.weight(1f), style = txt(12.sp, 500, T.ink2))
            IconBtn("plus", "폴더 추가", size = 32.dp, iconSize = 18.dp) { addFolder(L.folderNow()) }
        }
        @Composable fun walk(parent: String?, depth: Int) {
            Y.lib.folders.filter { it.parent == parent }.forEach { f ->
                val kids = Y.lib.folders.any { it.parent == f.id }; val closed = f.id in L.collapsed
                SItem("f:" + f.id, "folder", f.name, depth, chevron = if (kids) !closed else null, onChevron = { if (closed) L.collapsed.remove(f.id) else L.collapsed.add(f.id) }, folder = f)
                if (kids && !closed) walk(f.id, depth + 1)
            }
        }
        walk(null, 0)
        Spacer(Modifier.height(20.dp))
        SItem("", "settings", "설정", onClick = { L.drawer = false; kr.yeobaek.note.settings.openSettings() })
    }
}

@Composable
private fun SItem(id: String, icon: String, label: String, depth: Int = 0, chevron: Boolean? = null, onChevron: () -> Unit = {}, folder: Folder? = null, onClick: (() -> Unit)? = null) {
    val on = L.view == id && L.q.isEmpty() && id.isNotEmpty()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().height(40.dp).onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(id) {
            detectTapGestures(
                onLongPress = { if (folder != null) { Y.haptic(12); folderMenu(bounds, folder) } },
                onTap = { if (onClick != null) onClick() else { L.go(id); L.drawer = false } })
        }.padding(start = 12.dp + 12.dp * depth, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (chevron != null) Box(Modifier.size(20.dp).pointerInput(id) { detectTapGestures { onChevron() } }, contentAlignment = Alignment.Center) {
            Ic("chevron", Modifier.graphicsLayer { rotationZ = if (chevron) 90f else 0f }, size = 14.dp)
        } else if (folder != null) Spacer(Modifier.width(20.dp))
        Ic(icon, size = 20.dp, color = if (on) T.snow else T.ink2)
        Spacer(Modifier.width(12.dp))
        BasicText(label, Modifier.weight(1f), style = txt(15.sp, if (on) 600 else 400, if (on) T.snow else T.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (on) Box(Modifier.size(5.dp).background(T.acc, CircleShape))
    }
}

private fun prompt(title: String, ok: String, value: String = "", placeholder: String = "", then: (String?) -> Unit) {
    var done = false
    Overlay.modal(width = 380.dp, onClose = { if (!done) { done = true; then(null) } }) { close ->
        var v by remember { mutableStateOf(value) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { fr.requestFocus() } }
        Column {
            BasicText(title, style = txt(17.sp, 600, T.snow)); Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.fill).padding(12.dp)) {
                if (v.isEmpty()) BasicText(placeholder, style = txt(15.sp, 400, T.ink2))
                BasicTextField(v, { if (it.length <= 40) v = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true, textStyle = txt(15.sp, 400, T.snow), cursorBrush = SolidColor(T.acc))
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

private fun folderMenu(r: Rect, f: Folder) {
    Overlay.popover("fm" + f.id, r, 210.dp, "left") {
        PItem("이름 바꾸기", "page") { Overlay.closePop(); prompt("폴더 이름", "바꾸기", f.name) { n -> if (n != null) { f.name = n; Y.saveLib() } } }
        PItem("하위 폴더 추가", "plus") { Overlay.closePop(); addFolder(f.id) }
        PItem("폴더 삭제", "trash", color = T.danger) {
            Overlay.closePop()
            Overlay.confirm("‘${f.name}’ 폴더를 삭제할까요?", "폴더 삭제", danger = true) { ok ->
                if (!ok) return@confirm
                val ids = descendants(f.id)
                Y.lib.notes.forEach { if (it.folder in ids) it.folder = null }
                Y.lib.folders.removeAll { it.id in ids }; Y.saveLib(); L.go("all")
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
    val side = if (wide) 48.dp else 24.dp
    Column(modifier.padding(top = sys.calculateTopPadding() + 40.dp, start = side, end = if (wide) 40.dp else 20.dp)) {
        // 머리
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!wide) { IconBtn("menu", "메뉴 열기") { L.drawer = true }; Spacer(Modifier.width(4.dp)) }
            if (L.searching) SearchField(Modifier.weight(1f))
            else BasicText(viewTitle(), Modifier.weight(1f), style = txt(28.sp, 600, T.snow, -.02f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Actions(wide, trash, notes)
        }
        Spacer(Modifier.height(if (wide) 40.dp else 16.dp))
        // 세로 화면 보기 바: 고른 것만 진한 글자 + 점
        if (!wide && L.q.isEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("recent" to "최근", "all" to "노트", "quiz" to "문제지", "pdf" to "PDF", "fav" to "즐겨찾기", "trash" to "휴지통").forEach { (id, n) ->
                    val on = L.view == id
                    Box(Modifier.height(34.dp).pointerInput(id) { detectTapGestures { L.go(id) } }, contentAlignment = Alignment.Center) {
                        BasicText(n, style = txt(15.sp, if (on) 600 else 500, if (on) T.snow else T.ink2))
                        if (on) Box(Modifier.align(Alignment.BottomCenter).size(5.dp).background(T.acc, CircleShape))
                    }
                }
            }
        }
        // 내용: 보기를 바꾸면 살짝 올라오며 나타남
        val rise = remember(L.view) { Animatable(0f) }
        LaunchedEffect(L.view) { rise.animateTo(1f, tween(420, easing = Out)) }
        Box(Modifier.weight(1f).graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 10.dp.toPx() }) {
            if (notes.isEmpty()) Empty(trash)
            else if (L.mode == "cover") {
                LazyVerticalGrid(GridCells.FixedSize(160.dp), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.Start), verticalArrangement = Arrangement.spacedBy(40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = sys.calculateBottomPadding() + 40.dp)) {
                    items(notes, key = { it.id }) { n -> CoverItem(n, trash) }
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = sys.calculateBottomPadding() + 40.dp)) {
                    items(notes, key = { it.id }) { n -> RowItem(n, trash) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(modifier: Modifier) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Row(modifier.height(44.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic("search", size = 20.dp); Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (L.q.isEmpty()) BasicText("검색", style = txt(20.sp, 600, T.ink2))
            BasicTextField(L.q, { L.q = it; L.selecting = false; L.sel.clear() }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true, textStyle = txt(20.sp, 600, T.snow), cursorBrush = SolidColor(T.acc))
        }
        IconBtn("close", "검색 닫기") { L.q = ""; L.searching = false }
    }
}

@Composable
private fun Actions(wide: Boolean, trash: Boolean, notes: List<NoteMeta>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (L.selecting) {
            BasicText("${L.sel.size}개 선택됨", Modifier.padding(end = 8.dp), style = txt(13.sp, 500, T.ink2))
            if (trash) {
                IconBtn("restore", "복원") { L.sel.forEach { id -> Y.lib.notes.find { it.id == id }?.let { it.trashed = false; it.trashedAt = null } }; Y.saveLib(); L.exitSel() }
                IconBtn("trash", "영구 삭제", color = T.danger) {
                    Overlay.confirm("${L.sel.size}개 노트를 영구 삭제할까요?", "영구 삭제", danger = true) { ok ->
                        if (ok) { val ids = L.sel.toSet(); Y.lib.notes.filter { it.id in ids }.forEach(Y::purge); Y.lib.notes.removeAll { it.id in ids }; Y.saveLib(); L.exitSel() }
                    }
                }
            } else {
                IconBtn("star", "즐겨찾기") { val all = L.sel.all { id -> Y.lib.notes.find { it.id == id }?.fav == true }; L.sel.forEach { id -> Y.lib.notes.find { it.id == id }?.fav = !all }; Y.saveLib(); L.exitSel() }
                IconBtn("move", "이동") { r -> movePop(r) }
                IconBtn("trash", "휴지통으로 이동", color = T.danger) { trashSel() }
            }
            IconBtn("close", "취소") { L.exitSel() }
            return@Row
        }
        if (!wide && !L.searching) IconBtn("search", "검색") { L.searching = true }
        IconBtn(if (L.mode == "cover") "list" else "cover", if (L.mode == "cover") "목록으로 보기" else "커버로 보기") {
            L.mode = if (L.mode == "cover") "list" else "cover"; Store.setFlag("libmode", L.mode)
        }
        if (L.view != "recent" && !trash) IconBtn("sort", "정렬") { r ->
            Overlay.popover("sort", r, 170.dp, "right") {
                PTitle("정렬")
                listOf("updated" to "수정일", "name" to "이름", "created" to "만든 날").forEach { (id, n) -> PItem(n, on = L.sort == id) { L.sort = id; Overlay.closePop() } }
            }
        }
        if (trash) { if (notes.isNotEmpty()) IconBtn("trash", "휴지통 비우기", color = T.danger) { emptyTrash() } }
        else {
            IconBtn("importf", "가져오기") { L.importPick?.invoke() }
            IconBtn("plus", "새 노트") { newNoteModal() }
        }
    }
}

@Composable
private fun Empty(trash: Boolean) {
    Column(Modifier.fillMaxSize().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val (ic, t) = when {
            L.q.isNotEmpty() -> "search" to "검색 결과가 없어요"
            trash -> "trash" to "휴지통이 비어 있어요"
            L.view == "fav" -> "star" to "즐겨찾기한 노트가 없어요"
            else -> "notes" to "아직 노트가 없어요"
        }
        Ic(ic, size = 40.dp, color = T.ink2, stroke = 1.4f)
        Spacer(Modifier.height(16.dp))
        BasicText(t, style = txt(17.sp, 600, T.snow))
        if (!trash && L.q.isEmpty() && L.view != "fav") {
            Spacer(Modifier.height(20.dp))
            Row { Btn("새 노트") { newNoteModal() }; Spacer(Modifier.width(8.dp)); Btn("가져오기", "ghost") { L.importPick?.invoke() } }
        }
    }
}

/* ---------- 항목 ---------- */
private val thumbCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()
private fun thumbOf(n: NoteMeta) = n.thumb?.let { t -> thumbCache.getOrPut(t) { runCatching { BitmapFactory.decodeFile(Store.file("thumbs/$t").path)?.asImageBitmap() }.getOrNull() } }

@Composable
fun Cover(n: NoteMeta, small: Boolean = false, modifier: Modifier = Modifier) {
    val c = Color(Y.cover(n.cover).c)
    val th = if (n.kind == "pdf") thumbOf(n) else null
    Box(modifier.then(if (small) Modifier.width(34.dp) else Modifier.fillMaxWidth()).aspectRatio(3f / 4f).clip(RoundedCornerShape(if (small) 3.dp else 4.dp))
        .background(if (th != null) Color.White else c).then(if (T.dark) Modifier.border(.5.dp, Color(0x2EF8F9FA), RoundedCornerShape(4.dp)) else Modifier)) {
        val dk = T.dark
        if (th != null) Image(th, null, Modifier.fillMaxSize().graphicsLayer { alpha = if (dk) .9f else 1f }, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        if (small) return@Box
        if (n.kind == "pdf") {
            BasicText("PDF", Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xB8212529), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp), style = txt(10.sp, 700, Color.White, .04f))
            return@Box
        }
        BasicText(Y.clean(n.title).ifEmpty { "제목 없음" }, Modifier.align(Alignment.BottomStart).padding(12.dp), style = txt(15.sp, 600, Color(0xFFF8F9FA), -.01f).copy(lineHeight = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (n.kind == "quiz") {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).graphicsLayer { alpha = .7f }) { Ic("quiz", size = 16.dp, color = Color(0xFFF8F9FA)) }
            n.score?.let { BasicText(it, Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 10.dp), style = txt(15.sp, 700, Color(0xFFFF8787), .02f)) }
        }
    }
}

@Composable
private fun bindItem(n: NoteMeta, trash: Boolean, onDown: (Boolean) -> Unit = {}): Modifier = Modifier.pointerInput(n.id, trash, L.selecting) {
    detectTapGestures(
        onPress = { onDown(true); tryAwaitRelease(); onDown(false) },
        onLongPress = { Y.haptic(12); L.lpAt = System.currentTimeMillis(); L.selecting = true; if (n.id !in L.sel) L.sel.add(n.id) },
        onTap = {
            // 길게 눌러 선택한 직후의 톡은 무시
            if (System.currentTimeMillis() - L.lpAt < 700) return@detectTapGestures
            if (L.selecting) { if (n.id in L.sel) L.sel.remove(n.id) else L.sel.add(n.id); if (L.sel.isEmpty()) L.selecting = false; return@detectTapGestures }
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
            if (n.fav) Box(Modifier.padding(start = 12.dp, top = 8.dp)) { Ic("star", size = 14.dp, color = Color.White, fill = true) }
            if (L.selecting) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape)
                .background(if (sel) T.acc else Color(0x59000000)).border(2.dp, if (sel) T.acc else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                if (sel) Ic("check", size = 14.dp, color = Color.White, stroke = 2.4f)
            }
        }
        Spacer(Modifier.height(12.dp))
        BasicText(n.title.ifEmpty { "제목 없음" }, style = txt(15.sp, 500, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        BasicText(Y.fmtDate(n.updated), style = txt(12.sp, 400, T.ink2))
    }
}

@Composable
private fun RowItem(n: NoteMeta, trash: Boolean) {
    val sel = n.id in L.sel
    Row(bindItem(n, trash).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) T.fill else Color.Transparent).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Cover(n, small = true)
        Column(Modifier.weight(1f)) {
            BasicText(n.title.ifEmpty { "제목 없음" }, style = txt(15.sp, 500, T.snow), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(Y.fmtDate(n.updated), style = txt(12.sp, 400, T.ink2))
        }
        BasicText("${n.pageCount} 페이지", style = txt(13.sp, 400, T.ink2))
        if (L.selecting) Box(Modifier.size(22.dp).clip(CircleShape).background(if (sel) T.acc else Color.Transparent).border(2.dp, if (sel) T.acc else T.slate, CircleShape), contentAlignment = Alignment.Center) {
            if (sel) Ic("check", size = 14.dp, color = Color.White, stroke = 2.4f)
        } else if (n.fav) Ic("star", size = 16.dp, color = T.ink2, fill = true)
    }
}

private fun trashSel() {
    val ids = L.sel.toList(); val one = if (ids.size == 1) Y.lib.notes.find { it.id == ids[0] } else null
    Overlay.confirm(if (one != null) "‘${one.title}’를 휴지통으로 옮길까요?" else "${ids.size}개 노트를 휴지통으로 옮길까요?", "휴지통으로 이동") { ok ->
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
    Overlay.popover("move", r, 220.dp, "right") {
        PTitle("이동할 폴더")
        fun put(fid: String?) { L.sel.forEach { id -> Y.lib.notes.find { it.id == id }?.folder = fid }; Y.saveLib(); Overlay.closePop(); L.exitSel() }
        PItem("폴더 없음") { put(null) }
        @Composable fun walk(p: String?, d: Int) { Y.lib.folders.filter { it.parent == p }.forEach { f -> PItem(f.name, indent = (14 * d).dp) { put(f.id) }; walk(f.id, d + 1) } }
        walk(null, 0)
    }
}

/* ---------- 새 노트 ---------- */
fun newNoteModal() {
    val auto = Nav.nextTitle()
    Overlay.modal(width = 560.dp) { close ->
        var title by remember { mutableStateOf("") }
        var cover by remember { mutableStateOf(Nav.nextCover()) }
        var tpl by remember { mutableStateOf("lined") }
        val fr = remember { FocusRequester() }
        fun go() {
            val meta = Nav.newNote(title.trim().ifEmpty { auto }, cover, tpl, L.folderNow())
            // 돌아왔을 때 방금 만든 노트가 보이는 보기로
            if (L.view !in listOf("all", "recent") && !L.view.startsWith("f:")) L.go("all")
            L.q = ""; L.searching = false
            close(); Nav.open(meta)
        }
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(96.dp)) { Cover(NoteMeta("p", title.ifEmpty { auto }, cover)) }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
                        if (title.isEmpty()) BasicText(auto, style = txt(24.sp, 600, T.ink2, -.02f))
                        BasicTextField(title, { if (it.length <= 40) title = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                            textStyle = txt(24.sp, 600, T.snow, -.02f), cursorBrush = SolidColor(T.acc),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { go() }))
                    }
                    Box(Modifier.fillMaxWidth().height(.5.dp).background(T.hair))
                    BasicText("표지", Modifier.padding(top = 24.dp, bottom = 10.dp), style = txt(12.sp, 500, T.ink2))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Y.COVERS.forEach { c -> kr.yeobaek.note.ui.Dot(Color(c.c), cover == c.id, 32.dp) { cover = c.id } }
                    }
                }
            }
            BasicText("양식", Modifier.padding(top = 24.dp, bottom = 10.dp), style = txt(12.sp, 500, T.ink2))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Y.TEMPLATES.forEach { (id, name) ->
                    val on = tpl == id
                    Column(Modifier.pointerInput(id) { detectTapGestures { tpl = id; Y.haptic(6) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                        TplMini(id, Modifier.width(64.dp).aspectRatio(1 / 1.4142f).clip(RoundedCornerShape(4.dp)).border(if (on) 1.5.dp else .5.dp, if (on) T.acc else T.hair, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(8.dp))
                        BasicText(name, style = txt(12.sp, if (on) 600 else 400, if (on) T.snow else T.ink2))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Btn("취소", "ghost") { close() }; Spacer(Modifier.width(4.dp)); Btn("만들기") { go() }
            }
        }
    }
}

/** 양식 미리보기: 줄·모눈·점은 무늬가 보이게 크게 */
@Composable
fun TplMini(id: String, modifier: Modifier) {
    val pc = Paper.c()
    androidx.compose.foundation.Canvas(modifier.background(Color(pc.paper))) {
        Templates.draw(drawContext.canvas.nativeCanvas, id, size.width, size.height, 1f, pc)
    }
}
