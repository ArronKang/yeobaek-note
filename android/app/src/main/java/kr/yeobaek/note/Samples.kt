package kr.yeobaek.note

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.data.Store
import kr.yeobaek.note.quiz.Quiz

/** 처음 실행하면 예제 문제지(수학·영어·국어·통합과학·한국사)를 넣어 둬요 */
object Samples {
    fun seed() {
        if (Store.flag("seeded") != null) return
        runCatching {
            val arr = Store.json.parseToJsonElement(Y.app.assets.open("samples.json").bufferedReader().readText()) as JsonArray
            // 맨 앞이 서재 맨 위에 오도록 뒤에서부터
            arr.reversed().forEach { e ->
                val o = e as JsonObject
                val cover = o["cover"]?.jsonPrimitive?.contentOrNull
                val raw = JsonObject(o - "cover")
                Quiz.addQuiz(Docs.parseQuiz(raw), cover, rawJson = raw.toString())
            }
        }
        Store.setFlag("seeded", "1")
    }
}
