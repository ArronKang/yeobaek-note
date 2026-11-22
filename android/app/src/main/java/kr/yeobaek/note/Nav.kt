package kr.yeobaek.note

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.data.NoteData
import kr.yeobaek.note.data.NoteMeta
import kr.yeobaek.note.data.Page
import kr.yeobaek.note.data.PdfInk
import kr.yeobaek.note.data.Store
import kr.yeobaek.note.pdf.PdfSource
import kr.yeobaek.note.quiz.Quiz
import kr.yeobaek.note.ui.Overlay
import java.io.File
import java.security.MessageDigest

/** 열린 문서 탭 하나: 보던 위치를 기억 */
class OpenDoc(val key: String, val doc: Doc) { var scroll = 0f; var zoom: Any = "width" }

/** 화면 이동과 문서 열기 */
object Nav {
    val tabs = mutableStateListOf<OpenDoc>()
    var cur by mutableStateOf<OpenDoc?>(null)
    var inEditor by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var editorRev by mutableIntStateOf(0)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun keyOf(d: Doc) = if (d.type == "pdf") "pdf:" + (d.key ?: d.id) else d.id

    fun enter(doc: Doc) {
        val k = keyOf(doc)
        var t = tabs.find { it.key == k }
        if (t == null) { t = OpenDoc(k, doc); tabs.add(t) }
        cur = t; inEditor = true; editorRev++
        Y.mode = "pen"; Y.haptic(8)
        // 문제지를 처음 열면 한 번: 누를 수 있다는 단서는 엔진이 맡지 않고 건너뜀 (화면 글 없이)
    }

    fun switchTo(t: OpenDoc) { if (cur === t) return; cur = t; editorRev++; Y.haptic(6) }

    fun closeTab(t: OpenDoc) {
        val i = tabs.indexOf(t); if (i < 0) return
        tabs.removeAt(i)
        if (t.doc.type == "pdf") t.doc.pdf?.close()
        if (cur === t) {
            val next = tabs.getOrNull(i) ?: tabs.getOrNull(i - 1)
            if (next != null) { cur = next; editorRev++ } else closeEditor()
        }
    }

    fun closeEditor() { Overlay.closePop(); inEditor = false; Y.libRev++ }

    /** 노트가 지워졌으면 탭도 정리 */
    fun pruneTabs() {
        tabs.removeAll { t -> t.doc.type != "pdf" && Y.lib.notes.none { it.id == t.doc.id && !it.trashed } }
        if (cur != null && cur !in tabs) cur = tabs.firstOrNull()
    }

    /* ---------- 열기 ---------- */
    fun open(n: NoteMeta) {
        tabs.find { it.doc.id == n.id || (n.kind == "pdf" && it.doc.libId == n.id) }?.let { cur = it; inEditor = true; editorRev++; return }
        when (n.kind) {
            "pdf" -> {
                val f = Store.file("blobs/${n.id}.pdf")
                if (!f.exists()) { Overlay.toast("열 수 없어요"); return }
                busy = true
                scope.launch {
                    val doc = withContext(Dispatchers.IO) { pdfDoc(f, n.key ?: n.id, n.title) }
                    busy = false
                    if (doc == null) { Overlay.toast("열 수 없어요"); return@launch }
                    doc.libId = n.id; enter(doc)
                }
            }
            "quiz" -> {
                busy = true
                scope.launch {
                    val doc = withContext(Dispatchers.Default) { Quiz.prep(n); Quiz.quizDoc(n) }
                    busy = false
                    if (doc == null) { Overlay.toast("열 수 없어요"); return@launch }
                    enter(doc)
                }
            }
            else -> enter(Docs.noteDoc(n))
        }
    }

    private fun pdfDoc(f: File, key: String, title: String): Doc? {
        val src = PdfSource.open(f) ?: return null
        val ink = Store.get("p/${Y.keyFile(key)}.json", PdfInk.serializer())
        val pages = (0 until src.count).map { i ->
            Page(src.ratios[i], "blank", ink?.pages?.getOrNull(i)?.strokes ?: ArrayList(), ink?.pages?.getOrNull(i)?.texts ?: ArrayList())
        }.toMutableList()
        return Doc(key, "pdf", title, pages, pdf = src, key = key)
    }

    /* ---------- 새 노트 ---------- */
    fun nextTitle(): String {
        val used = Y.lib.notes.filter { !it.trashed }.map { it.title }.toSet()
        var k = 1; while ("새 노트 $k" in used) k++
        return "새 노트 $k"
    }
    fun nextCover(): String {
        val last = Y.lib.notes.filter { !it.trashed && it.kind != "quiz" }.maxByOrNull { it.created }
        val ci = if (last != null) (Y.COVERS.indexOfFirst { it.id == last.cover } + 1) % Y.COVERS.size else 1
        return Y.COVERS[ci.coerceAtLeast(0)].id
    }
    fun newNote(title: String, cover: String, tpl: String, folder: String?): NoteMeta {
        val now = System.currentTimeMillis(); val id = Y.uid()
        val meta = NoteMeta(id, title, cover, folder = folder, created = now, updated = now, pageCount = 1)
        Y.lib.notes.add(meta)
        Store.set("n/$id.json", NoteData.serializer(), NoteData(mutableListOf(Page(Y.RATIO, tpl))))
        Y.saveLib()
        return meta
    }

    /* ---------- 가져오기 (PDF · .ybq) ---------- */
    fun import(uri: Uri, folder: String?, after: (NoteMeta?) -> Unit = {}) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { importNow(uri, folder) } }
            busy = false
            val m = r.getOrNull()
            if (m == null) Overlay.toast("열 수 없는 파일이에요")
            after(m)
        }
    }

    private fun nameOf(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "문서"
        runCatching {
            Y.app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        return name
    }

    private fun importNow(uri: Uri, folder: String?): NoteMeta? {
        val bytes = Y.app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val name = nameOf(uri)
        val head = String(bytes, 0, minOf(bytes.size, 64), Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
        if (name.endsWith(".ybq", true) || head.startsWith("{")) {
            val text = String(bytes, Charsets.UTF_8).trimStart('﻿')
            val spec = Docs.parseQuiz(text)
            return Quiz.addQuiz(spec, null, folder, rawJson = text)
        }
        if (!head.startsWith("%PDF")) return null
        // 같은 파일이면 같은 항목
        val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val key = "$name:${bytes.size}:$hash"
        Y.lib.notes.find { it.kind == "pdf" && it.key == key && !it.trashed }?.let { return it }
        val id = Y.uid()
        val f = Store.file("blobs/$id.pdf"); f.writeBytes(bytes)
        val src = PdfSource.open(f) ?: run { f.delete(); return null }
        val thumb = "$id.jpg"
        val ok = PdfSource.cover(src, Store.file("thumbs/$thumb"))
        val count = src.count; src.close()
        val now = System.currentTimeMillis()
        val m = NoteMeta(id, name.removeSuffix(".pdf").removeSuffix(".PDF"), "slate", "pdf", folder = folder, created = now, updated = now, pageCount = count, key = key, thumb = if (ok) thumb else null)
        Y.lib.notes.add(0, m)
        Y.saveLib()
        return m
    }
}
