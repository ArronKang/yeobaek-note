package kr.yeobaek.note.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.yeobaek.note.Y
import kr.yeobaek.note.pdf.PdfSource

/** 열려 있는 문서 하나 (노트·문제지·PDF) */
class Doc(
    val id: String,
    val type: String,               // note | quiz | pdf
    var title: String,
    val pages: MutableList<Page>,
    var lv: Int? = null,
    val quiz: QuizSpec? = null,
    var qs: QuizState? = null,
    val pdf: PdfSource? = null,
    val key: String? = null,
    var libId: String? = null,
    /** 원본 PDF 문제지(.ybq 2)의 답안 정보 층 */
    val ov: kr.yeobaek.note.quiz.QOverlay? = null,
) {
    val bookmarks = HashSet<Int>()
    /** 실행취소 기록 (DocView.Entry) */
    val undo = ArrayList<Any>(); val redo = ArrayList<Any>()
    /** 페이지 구성이 바뀐 횟수 (화면이 다시 짜이게) */
    var rev = 0
}

object Docs {
    fun makePage(tpl: String = "lined") = Page(Y.RATIO, tpl)

    fun noteDoc(n: NoteMeta): Doc {
        val d = Store.get("n/${n.id}.json", NoteData.serializer())
        val pages = d?.pages?.takeIf { it.isNotEmpty() }?.onEach { if (it.ratio <= 0f) it.ratio = Y.RATIO }?.toMutableList() ?: mutableListOf(makePage())
        return Doc(n.id, "note", n.title, pages, libId = n.id)
    }

    fun save(doc: Doc) {
        when (doc.type) {
            "note", "quiz" -> {
                // 서재에서 지운 뒤 늦게 도착한 저장은 버림 (안 그러면 목록에 없는 파일이 다시 생김)
                if (Y.lib.notes.none { it.id == doc.id }) return
                val pages = doc.pages.map { p -> Page(p.ratio, p.tpl, p.strokes, p.texts, p.qi, p.head) }.toMutableList()
                if (!Store.set("n/${doc.id}.json", NoteData.serializer(), NoteData(pages, doc.lv))) return
                Y.lib.notes.find { it.id == doc.id }?.let { it.title = doc.title; it.updated = System.currentTimeMillis(); it.pageCount = pages.size; Y.saveLib() }
            }
            "pdf" -> {
                val key = doc.key ?: return
                val any = doc.pages.any { it.strokes.isNotEmpty() || it.texts.isNotEmpty() }
                val f = "p/${Y.keyFile(key)}.json"
                if (any) Store.set(f, PdfInk.serializer(), PdfInk(doc.pages.map { PdfInkPage(it.strokes, it.texts) }))
                else Store.del(f)
                Y.lib.notes.find { it.kind == "pdf" && it.key == key }?.let { it.updated = System.currentTimeMillis(); it.title = doc.title; Y.saveLib() }
            }
        }
    }

    /* ---------- .ybq 읽기 ---------- */
    class BadQuiz : Exception("형식")

    fun parseQuiz(text: String): QuizSpec {
        val j = runCatching { Store.json.parseToJsonElement(text).jsonObject }.getOrElse { throw BadQuiz() }
        return parseQuiz(j)
    }

    private fun str(e: JsonElement?): String = when (e) {
        null -> ""
        is JsonPrimitive -> e.contentOrNull ?: ""
        else -> e.toString()
    }

    fun parseQuiz(j: JsonObject): QuizSpec {
        val arr = (j["problems"] as? JsonArray) ?: throw BadQuiz()
        if (arr.isEmpty()) throw BadQuiz()
        var no = 0
        fun img(e: JsonElement?): QImg? {
            val o = e as? JsonObject ?: return null
            val src = str(o["src"]); val w = o["w"]?.jsonPrimitive?.doubleOrNull ?: 0.0; val h = o["h"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (!Regex("^data:image/(png|jpeg|webp|svg\\+xml);").containsMatchIn(src) || w <= 0 || h <= 0) return null
            val k = minOf(1.0, 472.0 / w)
            return QImg(src, minOf(472.0, w).toFloat(), (h * k).toFloat())
        }
        val ps = arr.mapIndexed { i, e ->
            val p = e.jsonObject
            val type = str(p["type"])
            if (type == "passage") Problem("passage", 0, str(p["id"]).ifEmpty { "p$i" }, str(p["q"]), str(p["text"]), emptyList(), "", "", 0.0, 0f, 0, img(p["img"]))
            else {
                val short = type == "short"
                val sp = p["space"]?.jsonPrimitive?.doubleOrNull
                Problem(
                    if (short) "short" else "choice", ++no, str(p["id"]).ifEmpty { "${i + 1}" }, str(p["q"]), "",
                    (p["choices"] as? JsonArray)?.take(8)?.map { str(it) } ?: emptyList(),
                    str(p["answer"]).let { a -> if (!short) a.toDoubleOrNull()?.toInt()?.toString() ?: a else a },
                    str(p["solution"]), p["points"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    (sp?.takeIf { it > 0 } ?: if (short) 130.0 else 70.0).coerceIn(30.0, 500.0).toFloat(),
                    p["orig"]?.jsonPrimitive?.intOrNull ?: 0, img(p["img"]),
                )
            }
        }
        return QuizSpec(str(j["title"]).ifEmpty { "문제지" }, str(j["subject"]), ps, j)
    }

    fun specJson(spec: QuizSpec): String = spec.raw.toString()

    fun loadSpec(id: String): QuizSpec? = Store.getRaw("q/$id.json")?.let { runCatching { parseQuiz(it) }.getOrNull() }
}
