package kr.yeobaek.note

import android.content.Context
import android.content.res.Configuration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kr.yeobaek.note.data.Eraser
import kr.yeobaek.note.data.Library
import kr.yeobaek.note.data.NoteMeta
import kr.yeobaek.note.data.Pen
import kr.yeobaek.note.data.Settings
import kr.yeobaek.note.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class Cover(val id: String, val name: String, val c: Long)

/** 앱 전체 상태 (웹의 window.Y) */
object Y {
    lateinit var app: Context

    val COVERS = listOf(
        Cover("char", "먹", 0xFF2B2D31), Cover("slate", "남색", 0xFF3B4A63),
        Cover("sage", "세이지", 0xFF5F7A6B), Cover("rose", "로즈", 0xFFA2616C),
        Cover("mustard", "머스터드", 0xFFB08636), Cover("terra", "테라코타", 0xFFA35A43),
        Cover("moss", "이끼", 0xFF6C7A4A), Cover("plum", "자두", 0xFF6B5884),
    )
    val PEN_COLORS = listOf("#1A1A20", "#2B4FD8", "#1F9D6B", "#E5484D", "#F2A93B", "#7A5AF8")
    val HL_COLORS = listOf("#FFE45C", "#8FF0C4", "#FF9EC7", "#C9BEFF")
    val TEMPLATES = listOf("lined" to "줄 노트", "grid" to "모눈", "dot" to "점 노트", "planner" to "플래너", "recipe" to "레시피 저널", "blank" to "빈 페이지")
    val PEN_TYPES = listOf("ball" to "볼펜", "fountain" to "만년필", "fine" to "세필", "brush" to "붓펜")
    const val MM = 210f
    const val RATIO = 1.4142f

    var lib = Library()
    var settings = Settings()
    var pens: MutableList<Pen> = ArrayList()
    var eraser = Eraser()
    var penIdx by mutableIntStateOf(0)
    /** pen | eraser | lasso | space | text */
    var mode by mutableStateOf("pen")

    /** 화면을 다시 그리게 하는 신호들 */
    var libRev by mutableIntStateOf(0)
    var setRev by mutableIntStateOf(0)
    var penRev by mutableIntStateOf(0)
    var sysDark by mutableStateOf(false)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Store.init(app)
        settings = Store.get("settings.json", Settings.serializer()) ?: Settings()
        pens = Store.get("pens.json", ListSerializer(Pen.serializer()))?.toMutableList() ?: mutableListOf(
            Pen("pen", "fountain", "#1A1A20", 0.9f, 1f),
            Pen("pen", "ball", "#2B4FD8", 0.8f, 1f),
            Pen("pen", "brush", "#E5484D", 1.4f, 1f),
            Pen("hl", null, "#FFE45C", 6f, 0.4f),
            Pen("hl", null, "#8FF0C4", 6f, 0.4f),
        )
        pens.forEach { if (it.kind == "pen" && it.type == null) it.type = "ball" }
        eraser = Store.get("eraser.json", Eraser.serializer()) ?: Eraser()
        lib = Store.get("lib.json", Library.serializer()) ?: Library()
        sysDark = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // 휴지통 보관 기간이 지난 것은 영구 삭제
        val lim = settings.trashDays * 864e5.toLong()
        val now = System.currentTimeMillis()
        val old = lib.notes.filter { it.trashed && now - (it.trashedAt ?: now) > lim }
        if (old.isNotEmpty()) { old.forEach(::purge); lib.notes.removeAll(old.toSet()); saveLib() }
        Samples.seed()
    }

    fun saveLib() { Store.set("lib.json", Library.serializer(), lib); libRev++ }
    fun saveSettings() { Store.set("settings.json", Settings.serializer(), settings); setRev++ }
    fun savePens() { Store.set("pens.json", ListSerializer(Pen.serializer()), pens); Store.set("eraser.json", Eraser.serializer(), eraser); penRev++ }

    fun isDark(): Boolean { setRev; return settings.dark == "on" || (settings.dark == "auto" && sysDark) }
    fun paperGray(): Boolean = !isDark() && settings.paper == "gray"

    fun uid(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + (1..4).map { "0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)] }.joinToString("")

    fun cover(id: String?) = COVERS.find { it.id == id } ?: COVERS[0]

    /** 화면에 보이는 글자에서 가운뎃점(·) 빼기 */
    fun clean(s: String?): String = (s ?: "").replace(Regex("\\s*[·•]\\s*"), " ").replace(Regex("\\s{2,}"), " ").trim()

    fun fmtDate(ts: Long): String = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(ts))

    /** 영구 삭제: 노트·문제지·PDF의 모든 저장 조각을 한곳에서 */
    fun purge(n: NoteMeta) {
        listOf("n", "q", "qs").forEach { Store.del("$it/${n.id}.json") }
        if (n.kind == "pdf") {
            Store.del("blobs/${n.id}.pdf")
            n.key?.let { Store.del("p/${keyFile(it)}.json") }
            n.thumb?.let { Store.del("thumbs/$it") }
        }
    }
    fun keyFile(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private val vib: Vibrator? by lazy {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 31) (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        }.getOrNull()
    }
    fun haptic(ms: Int) {
        if (settings.haptic == "off") return
        val v = vib ?: return
        runCatching {
            val d = if (settings.haptic == "strong") ms * 2 else ms
            v.vibrate(VibrationEffect.createOneShot(d.toLong().coerceAtLeast(1), if (settings.haptic == "strong") 200 else 90))
        }
    }

    fun curPen(): Pen = pens[penIdx.coerceIn(0, (pens.size - 1).coerceAtLeast(0))]
}
