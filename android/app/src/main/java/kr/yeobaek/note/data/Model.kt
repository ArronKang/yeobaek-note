package kr.yeobaek.note.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/* 웹앱과 같은 JSON 모양을 써요. 획 좌표는 페이지 너비 기준(x, y 모두), 굵기는 mm/210 */

/** 점 하나: [x, y, 압력] */
object PtListSer : KSerializer<MutableList<FloatArray>> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(PrimitiveSerialDescriptor("pt", PrimitiveKind.STRING))
    override fun serialize(encoder: Encoder, value: MutableList<FloatArray>) {
        val je = encoder as JsonEncoder
        je.encodeJsonElement(JsonArray(value.map { p ->
            JsonArray(listOf(JsonPrimitive(r4(p[0])), JsonPrimitive(r4(p[1])), JsonPrimitive(r2(p.getOrElse(2) { 0.5f }))))
        }))
    }
    override fun deserialize(decoder: Decoder): MutableList<FloatArray> {
        val jd = decoder as JsonDecoder
        return jd.decodeJsonElement().jsonArray.mapTo(ArrayList()) { e ->
            val a = e.jsonArray
            floatArrayOf(a[0].jsonPrimitive.floatOrNull ?: 0f, a[1].jsonPrimitive.floatOrNull ?: 0f, a.getOrNull(2)?.jsonPrimitive?.floatOrNull ?: 0.5f)
        }
    }
    private fun r4(v: Float) = (v * 10000f).roundToInt() / 10000f
    private fun r2(v: Float) = (v * 100f).roundToInt() / 100f
}

@Serializable
class Stroke(
    var t: String = "pen",          // pen | hl
    var c: String = "#1A1A20",
    var s: Float = 0.8f / 210f,
    var a: Float = 1f,
    var k: String? = null,          // fountain | fine | brush (없으면 볼펜)
    @Serializable(with = PtListSer::class) var p: MutableList<FloatArray> = ArrayList(),
) {
    @kotlinx.serialization.Transient var live = false
    fun copyDeep() = Stroke(t, c, s, a, k, p.mapTo(ArrayList()) { it.copyOf() })
}

@Serializable
class TextBox(
    var id: String = "",
    var x: Float = 0f, var y: Float = 0f, var w: Float = 0.4f,
    var fs: Float = 0.028f,
    var c: String = "#1A1A20",
    var t: String = "",
)

@Serializable
class Page(
    var ratio: Float = 1.4142f,
    var tpl: String = "lined",
    var strokes: MutableList<Stroke> = ArrayList(),
    var texts: MutableList<TextBox> = ArrayList(),
    var qi: List<Int>? = null,
    var head: Boolean? = null,
)

@Serializable
class NoteData(var pages: MutableList<Page> = ArrayList(), var lv: Int? = null)

@Serializable
class PdfInk(var pages: List<PdfInkPage> = emptyList())
@Serializable
class PdfInkPage(var strokes: MutableList<Stroke> = ArrayList(), var texts: MutableList<TextBox> = ArrayList())

@Serializable
class NoteMeta(
    var id: String,
    var title: String = "제목 없음",
    var cover: String = "slate",
    var kind: String? = null,       // null = 노트, quiz, pdf
    var tpl: String? = null,
    var folder: String? = null,
    var fav: Boolean = false,
    var trashed: Boolean = false,
    var trashedAt: Long? = null,
    var created: Long = System.currentTimeMillis(),
    var updated: Long = System.currentTimeMillis(),
    var pageCount: Int = 1,
    var score: String? = null,
    var key: String? = null,        // PDF: 필기 키
    var thumb: String? = null,      // PDF: 표지 그림 파일 이름
)

@Serializable
class Folder(var id: String, var name: String, var parent: String? = null)

@Serializable
class Library(var folders: MutableList<Folder> = ArrayList(), var notes: MutableList<NoteMeta> = ArrayList())

@Serializable
class Settings(
    var finger: Boolean = false,
    var penButton: String = "eraser",
    var snap: Boolean = true,
    var palm: String = "mid",
    var linkPenOnly: Boolean = false,
    var paper: String = "white",
    var trayCount: Int = 5,
    var haptic: String = "mid",
    var autosave: Int = 500,
    var exportDir: String = "files",
    var trashDays: Int = 30,
    var hlSnap: Boolean = true,
    var dark: String = "off",       // off | on | auto
    var glass: String = "mid",      // 도구 막대: clear(투명) | mid(반투명) | solid(불투명)
)

@Serializable
class Pen(
    var kind: String = "pen",       // pen | hl
    var type: String? = "ball",     // ball | fountain | fine | brush
    var color: String = "#1A1A20",
    var mm: Float = 0.8f,
    var alpha: Float = 1f,
)

@Serializable
class Eraser(var mm: Float = 5f, var partial: Boolean = false)

/* ---------- 문제지 ---------- */
@Serializable
class QImg(val src: String, val w: Float, val h: Float)

class Problem(
    val type: String,               // passage | choice | short
    val no: Int,
    val id: String,
    val q: String,
    val text: String,
    val choices: List<String>,
    val answer: String,             // 객관식은 번호 문자열
    val solution: String,
    val points: Double,
    val space: Float,
    val orig: Int,
    val img: QImg?,
) {
    val isPassage get() = type == "passage"
}

class QuizSpec(val title: String, val subject: String, val problems: List<Problem>, val raw: kotlinx.serialization.json.JsonObject)

@Serializable
class QuizState(
    var sel: MutableMap<String, Int> = HashMap(),
    var rev: MutableMap<String, Map<String, Int>> = HashMap(),
    var grade: MutableMap<String, String> = HashMap(),
    var star: MutableMap<String, Int> = HashMap(),
    var focus: Boolean = false,
    var scored: Boolean = false,
)
