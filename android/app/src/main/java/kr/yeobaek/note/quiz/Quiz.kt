package kr.yeobaek.note.quiz

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kr.yeobaek.note.Y
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.data.NoteData
import kr.yeobaek.note.data.NoteMeta
import kr.yeobaek.note.data.Page
import kr.yeobaek.note.data.QuizSpec
import kr.yeobaek.note.data.QuizState
import kr.yeobaek.note.data.Store

/** 문제지 동작: 고르기, 별표, 채점, 오답 문제지, 초기화 */
object Quiz {
    class Score(val right: Int, val graded: Int, val total: Int)

    fun score(doc: Doc): Score {
        val ps = doc.quiz!!.problems; val qs = doc.qs!!
        var right = 0; var graded = 0
        ps.forEachIndexed { i, p -> val g = qs.grade["$i"]; if (g != null && !p.isPassage) { graded++; if (g == "o") right++ } }
        return Score(right, graded, ps.count { !it.isPassage })
    }

    fun isRight(doc: Doc, i: Int, sel: Int): Boolean? {
        val p = doc.quiz!!.problems[i]; if (p.type != "choice") return null
        return sel.toString() == p.answer
    }

    fun saveState(doc: Doc) {
        Store.set("qs/${doc.id}.json", QuizState.serializer(), doc.qs!!)
        Y.lib.notes.find { it.id == doc.id }?.let { m ->
            val sc = score(doc)
            m.score = if (sc.graded > 0 && doc.qs!!.scored) "${sc.right}/${sc.total}" else null
            m.updated = System.currentTimeMillis(); Y.saveLib()
        }
    }

    private fun pagesFor(spec: QuizSpec) = QuizLayout.paginate(spec).map { (qi, head) -> Page(Y.RATIO, "quiz", ArrayList(), ArrayList(), qi, head) }.toMutableList()

    /** 라이브러리에 추가 */
    fun addQuiz(spec: QuizSpec, cover: String? = null, folder: String? = null, rawJson: String? = null): NoteMeta {
        val id = Y.uid(); val now = System.currentTimeMillis()
        val pages = pagesFor(spec)
        val meta = NoteMeta(id, spec.title, cover ?: "slate", "quiz", "quiz", folder, created = now, updated = now, pageCount = pages.size)
        Store.setRaw("q/$id.json", rawJson ?: Docs.specJson(spec))
        Store.set("n/$id.json", NoteData.serializer(), NoteData(pages, QuizLayout.LV))
        Y.lib.notes.add(0, meta); Y.saveLib()
        return meta
    }

    /** 필기가 아직 없는 문제지는 이 기기 글꼴로 페이지를 다시 나눠요 (웹에서 만든 배치와 기기 글자 폭이 달라서) */
    fun prep(n: NoteMeta) {
        val spec = Docs.loadSpec(n.id) ?: return
        val data = Store.get("n/${n.id}.json", NoteData.serializer()) ?: NoteData()
        if (data.pages.any { it.strokes.isNotEmpty() || it.texts.isNotEmpty() }) return
        val pages = pagesFor(spec)
        if (data.lv == QuizLayout.LV && pages.map { it.qi } == data.pages.map { it.qi } && Store.flag("qprep.${n.id}") != null) return
        Store.set("n/${n.id}.json", NoteData.serializer(), NoteData(pages, QuizLayout.LV))
        Store.setFlag("qprep.${n.id}", "1")
        n.pageCount = pages.size; Y.saveLib()
    }

    fun quizDoc(n: NoteMeta): Doc? {
        val spec = Docs.loadSpec(n.id) ?: return null
        val data = Store.get("n/${n.id}.json", NoteData.serializer())
        var pages = data?.pages; var lv = data?.lv ?: 1
        if (pages.isNullOrEmpty() || pages[0].qi == null) { pages = pagesFor(spec); lv = QuizLayout.LV }
        pages.forEach { it.tpl = "quiz"; it.ratio = Y.RATIO; if (it.qi == null) it.qi = emptyList() }
        val st = Store.get("qs/${n.id}.json", QuizState.serializer()) ?: QuizState()
        return Doc(n.id, "quiz", n.title, pages.toMutableList(), lv, spec, st, libId = n.id)
    }

    /* ---------- 고르기·별표·채점 ---------- */
    fun toggleStar(doc: Doc, i: Int) {
        val qs = doc.qs!!; val k = "$i"
        if (qs.star[k] != null) qs.star.remove(k) else qs.star[k] = 1
        saveState(doc); Y.haptic(8)
    }

    fun select(doc: Doc, i: Int, n: Int) {
        val qs = doc.qs!!; val k = "$i"
        if (qs.sel[k] == n) qs.sel.remove(k) else qs.sel[k] = n
        val s = qs.sel[k]
        if (qs.rev[k]?.get("ans") == 1 && s != null) qs.grade[k] = if (isRight(doc, i, s) == true) "o" else "x"
        else qs.grade.remove(k)
        saveState(doc); Y.haptic(8)
    }

    /** 정답 카드를 처음 열면 답이 드러난 것으로, 고른 보기가 있으면 바로 채점 */
    fun reveal(doc: Doc, i: Int): Boolean {
        val qs = doc.qs!!; val k = "$i"
        if (qs.rev[k]?.get("ans") == 1) return false
        qs.rev[k] = mapOf("ans" to 1)
        val p = doc.quiz!!.problems[i]
        qs.sel[k]?.let { if (p.type == "choice") qs.grade[k] = if (isRight(doc, i, it) == true) "o" else "x" }
        saveState(doc); return true
    }

    fun setGrade(doc: Doc, i: Int, v: String) {
        val qs = doc.qs!!; val k = "$i"
        if (v == "clear") { qs.rev.remove(k); qs.grade.remove(k); saveState(doc); kr.yeobaek.note.library.Wrong.syncBack(doc, i, v); Y.haptic(10); return }
        qs.rev[k] = mapOf("ans" to 1); qs.grade[k] = v
        saveState(doc); kr.yeobaek.note.library.Wrong.syncBack(doc, i, v); Y.haptic(12)
    }

    /** 전체 채점: 고른 보기만. 점수는 이때부터 보여요 */
    fun gradeAll(doc: Doc): Pair<Int, Score> {
        val qs = doc.qs!!; var n = 0
        doc.quiz!!.problems.forEachIndexed { i, p ->
            val s = qs.sel["$i"]
            if (p.type != "choice" || s == null) return@forEachIndexed
            qs.grade["$i"] = if (isRight(doc, i, s) == true) "o" else "x"
            qs.rev["$i"] = mapOf("ans" to 1); n++
            kr.yeobaek.note.library.Wrong.syncBack(doc, i, qs.grade["$i"]!!)
        }
        qs.scored = true
        saveState(doc); Y.haptic(15)
        return n to score(doc)
    }

    fun reset(doc: Doc) {
        doc.qs = QuizState(star = doc.qs!!.star)
        saveState(doc)
    }

    fun setFocus(doc: Doc, on: Boolean?) { doc.qs!!.focus = on ?: !doc.qs!!.focus; saveState(doc) }

    /** 오답 문제지: 틀렸거나 별표한 문제 + 딸린 지문. 원래 번호는 orig로 */
    fun retry(doc: Doc): NoteMeta? {
        val ps = doc.quiz!!.problems; val qs = doc.qs!!
        val keep = sortedSetOf<Int>(); var passage = -1
        ps.forEachIndexed { i, p ->
            if (p.isPassage) { passage = i; return@forEachIndexed }
            if (qs.grade["$i"] == "x" || qs.star["$i"] != null) { keep.add(i); if (passage >= 0) keep.add(passage) }
        }
        if (keep.none { !ps[it].isPassage }) return null
        val rawPs = (doc.quiz.raw["problems"] as JsonArray)
        val out = keep.map { i ->
            val o = rawPs[i].jsonObject.toMutableMap(); o.remove("no"); o.remove("id")
            if (!ps[i].isPassage) o["orig"] = JsonPrimitive(ps[i].no)
            JsonObject(o)
        }
        val raw = JsonObject(mapOf("ybq" to JsonPrimitive(1), "title" to JsonPrimitive(Y.clean(doc.quiz.title) + " 오답"),
            "subject" to JsonPrimitive(Y.clean(doc.quiz.subject)), "problems" to JsonArray(out)))
        val spec = Docs.parseQuiz(raw)
        Y.haptic(10)
        return addQuiz(spec, "rose", rawJson = raw.toString())
    }
}
