package kr.yeobaek.note.library

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.yeobaek.note.Nav
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.data.NoteData
import kr.yeobaek.note.data.NoteMeta
import kr.yeobaek.note.data.Page
import kr.yeobaek.note.data.QuizState
import kr.yeobaek.note.data.Store
import kr.yeobaek.note.quiz.QuizLayout
import kr.yeobaek.note.ui.Overlay
import kotlinx.coroutines.launch

/**
 * 오답 모음: 모든 문제지에서 틀림(x)·애매(?)로 채점된 문제를 딸린 지문과 함께 자동으로 모은 문제지.
 * 모음에서 다시 채점하면 원래 문제지에도 같은 채점이 들어감(동기화). 원래 문제지에서 맞히면 다음에 열 때 빠짐.
 */
object Wrong {
    const val ID = "wrongbook"

    class Src(val meta: NoteMeta, val wrong: List<Int>)

    fun scan(): List<Src> = Y.lib.notes.filter { (it.kind == "quiz" || (it.kind == "pdf" && it.ov)) && !it.trashed && it.id != ID }.mapNotNull { m ->
        val st = Store.get("qs/${m.id}.json", QuizState.serializer()) ?: return@mapNotNull null
        val w = st.grade.filter { it.value == "x" || it.value == "?" }.keys.mapNotNull { it.toIntOrNull() }.sorted()
        if (w.isEmpty()) null else Src(m, w)
    }

    fun total(): Int = scan().sumOf { it.wrong.size }

    /** 모음 다시 만들기 (모인 문제가 그대로면 필기·채점을 지키려고 그대로 둠) */
    fun rebuild(): NoteMeta? {
        val srcs = scan()
        val sig = srcs.joinToString(",") { s -> s.meta.id + ":" + s.wrong.joinToString("-") }
        var meta = Y.lib.notes.find { it.id == ID }
        if (srcs.isEmpty()) return null
        if (meta != null && Store.flag("wrongsig") == sig && Store.getRaw("q/$ID.json") != null) return meta
        val out = ArrayList<JsonObject>()
        for (s in srcs) {
            if (s.meta.ov) { out.addAll(fromPdf(s)); continue }
            val raw = Store.getJson("q/${s.meta.id}.json")?.jsonObject ?: continue
            val ps = raw["problems"]?.jsonArray ?: continue
            var passage = -1; var no = 0
            val keep = sortedMapOf<Int, Int>()          // 문항 번호 → 원래 번호(지문은 0)
            ps.forEachIndexed { i, e ->
                val t = e.jsonObject["type"]?.jsonPrimitive?.content
                if (t == "passage") { passage = i; return@forEachIndexed }
                no++
                if (i in s.wrong) { keep[i] = e.jsonObject["orig"]?.jsonPrimitive?.intOrNull ?: no; if (passage >= 0) keep.putIfAbsent(passage, 0) }
            }
            keep.forEach { (i, orig) ->
                val o = ps[i].jsonObject.toMutableMap(); o.remove("no"); o.remove("id")
                o["src"] = JsonPrimitive(s.meta.id); o["si"] = JsonPrimitive(i)
                if (orig > 0) o["orig"] = JsonPrimitive(orig)
                out.add(JsonObject(o))
            }
        }
        if (out.isEmpty()) return null
        val raw = JsonObject(mapOf("ybq" to JsonPrimitive(1), "title" to JsonPrimitive("오답 모음"),
            "subject" to JsonPrimitive("틀리거나 애매한 문제"), "problems" to JsonArray(out)))
        val spec = Docs.parseQuiz(raw)
        val pages = QuizLayout.paginate(spec).map { (qi, head) -> Page(Y.RATIO, "quiz", ArrayList(), ArrayList(), qi, head) }.toMutableList()
        Store.setRaw("q/$ID.json", raw.toString())
        Store.set("n/$ID.json", NoteData.serializer(), NoteData(pages, QuizLayout.LV))
        Store.set("qs/$ID.json", QuizState.serializer(), QuizState())
        Store.setFlag("wrongsig", sig); Store.setFlag("qprep.$ID", "1")
        val now = System.currentTimeMillis()
        if (meta == null) { meta = NoteMeta(ID, "오답 모음", "terra", "quiz", "quiz", null, created = now, updated = now, pageCount = pages.size); Y.lib.notes.add(meta) }
        else { meta.pageCount = pages.size; meta.updated = now; meta.score = null }
        Y.saveLib()
        return meta
    }

    /** 원본 PDF 문제지의 틀린 문항: 그 영역을 원본에서 그림으로 잘라 문제로 (보기는 ①②③…) */
    private fun fromPdf(s: Src): List<JsonObject> {
        val ov = Store.getRaw("q/${s.meta.id}.json")?.let { kr.yeobaek.note.quiz.QOv.parse(it) } ?: return emptyList()
        val src = kr.yeobaek.note.pdf.PdfSource.open(Store.file("blobs/${s.meta.id}.pdf")) ?: return emptyList()
        val out = ArrayList<JsonObject>()
        try {
            val pages = HashMap<Int, android.graphics.Bitmap?>()
            for (i in s.wrong) {
                val p = ov.problems.getOrNull(i) ?: continue
                val bm = pages.getOrPut(p.page) { src.renderSync(p.page, 1100) } ?: continue
                val a = p.area ?: android.graphics.RectF(0f, p.num.top - .005f, 1f, (p.choices.maxOfOrNull { it.bottom } ?: p.num.bottom) + .012f)
                val x0 = (a.left * bm.width).toInt().coerceIn(0, bm.width - 1); val y0 = (a.top * bm.height).toInt().coerceIn(0, bm.height - 1)
                val x1 = (a.right * bm.width).toInt().coerceIn(x0 + 1, bm.width); val y1 = (a.bottom * bm.height).toInt().coerceIn(y0 + 1, bm.height)
                val crop = android.graphics.Bitmap.createBitmap(bm, x0, y0, x1 - x0, (y1 - y0).coerceAtMost(1600))
                val bos = java.io.ByteArrayOutputStream(); crop.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, bos)
                val w = 472f; val h = w * crop.height / crop.width
                val img = JsonObject(mapOf("src" to JsonPrimitive("data:image/jpeg;base64," + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)), "w" to JsonPrimitive(w), "h" to JsonPrimitive(h)))
                val label = (if (p.sec.isNotBlank()) p.sec + " " else "") + "${p.no}번"
                val m = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                    "type" to JsonPrimitive(if (p.type == "choice") "choice" else "short"), "q" to JsonPrimitive(Y.clean(s.meta.title) + " " + label), "img" to img,
                    "answer" to JsonPrimitive(p.answer), "solution" to JsonPrimitive(p.solution), "src" to JsonPrimitive(s.meta.id), "si" to JsonPrimitive(i), "orig" to JsonPrimitive(p.no))
                if (p.type == "choice") m["choices"] = JsonArray(p.choices.indices.map { JsonPrimitive(" ") })   // 보기 글은 그림 안에 있으니 번호만
                out.add(JsonObject(m))
            }
        } finally { src.close() }
        return out
    }

    fun open() {
        Nav.tabs.find { it.doc.id == ID }?.let { t -> Nav.switchTo(t); Nav.inEditor = true; return }
        Nav.busy = true
        Nav.scope.launch {
            val m = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { rebuild() }.getOrNull() }
            Nav.busy = false
            if (m == null) Overlay.toast("틀린 문제가 없어요") else Nav.open(m)
        }
    }

    /** 모음에서 채점하면 원래 문제지에도 */
    fun syncBack(doc: Doc, i: Int, v: String) {
        if (doc.id != ID) return
        val o = (doc.quiz?.raw?.get("problems") as? JsonArray)?.getOrNull(i)?.jsonObject ?: return
        val src = o["src"]?.jsonPrimitive?.content ?: return
        val si = o["si"]?.jsonPrimitive?.intOrNull ?: return
        val open = Nav.tabs.firstOrNull { it.doc.id == src }?.doc
        val st = open?.qs ?: Store.get("qs/$src.json", QuizState.serializer()) ?: QuizState()
        if (v == "clear") { st.grade.remove("$si"); st.rev.remove("$si") } else { st.grade["$si"] = v; st.rev["$si"] = mapOf("ans" to 1) }
        Store.set("qs/$src.json", QuizState.serializer(), st)
    }
}
