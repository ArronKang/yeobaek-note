package kr.yeobaek.note.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/** 기기 안 파일 저장소. 웹의 localStorage 키를 파일 하나씩으로 옮겼어요 (앱을 업데이트해도 남아요). */
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; isLenient = true; coerceInputValues = true }
    lateinit var root: File

    fun init(ctx: Context) {
        root = File(ctx.filesDir, "yb").apply { mkdirs() }
        listOf("n", "q", "qs", "p", "blobs", "thumbs").forEach { File(root, it).mkdirs() }
    }

    fun file(key: String) = File(root, key)

    fun <T> get(key: String, ser: KSerializer<T>): T? = try {
        val f = file(key); if (f.exists()) json.decodeFromString(ser, f.readText()) else null
    } catch (e: Exception) { null }

    fun <T> set(key: String, ser: KSerializer<T>, v: T): Boolean = try {
        val f = file(key); f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(ser, v))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        true
    } catch (e: Exception) { false }

    fun getRaw(key: String): String? = try { file(key).takeIf { it.exists() }?.readText() } catch (e: Exception) { null }
    fun setRaw(key: String, v: String) { file(key).also { it.parentFile?.mkdirs() }.writeText(v) }
    fun getJson(key: String): JsonElement? = getRaw(key)?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
    fun del(key: String) { file(key).delete() }

    /* 작은 표시들 (처음 한 번 단서, 마지막 보기 등) */
    private val flagFile get() = file("flags.json")
    private var flags: MutableMap<String, String>? = null
    private fun flagsMap(): MutableMap<String, String> = flags ?: (runCatching {
        json.decodeFromString<Map<String, String>>(flagFile.readText()).toMutableMap()
    }.getOrNull() ?: HashMap()).also { flags = it }

    fun flag(k: String): String? = flagsMap()[k]
    fun setFlag(k: String, v: String?) {
        val m = flagsMap(); if (v == null) m.remove(k) else m[k] = v
        runCatching { flagFile.writeText(json.encodeToString(kotlinx.serialization.serializer<Map<String, String>>(), m)) }
    }
}
