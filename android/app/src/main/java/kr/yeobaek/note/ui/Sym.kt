package kr.yeobaek.note.ui

import android.graphics.Paint
import android.graphics.Rect as ARect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * SF Symbols (Apple iOS 27 UI 키트 안 SF Pro 글꼴의 U+100000~ 글리프).
 * 앱 아이콘 이름 → 글리프 번호. 번호는 키트 글꼴에서 글리프 표를 뽑아 확인한 값.
 */
object Sym {
    private const val B = 0x100000
    val map: Map<String, Int> = mapOf(
        "back" to 0x189, "chevron" to 0x18a, "down" to 0x188, "up" to 0x187, "updown" to 0x18f,
        "search" to 0x2ab, "mic" to 0x2b0, "share" to 0x202, "importf" to 0x204, "compose" to 0x20e, "pencil" to 0x20a,
        "settings" to 0x8cb, "collapse" to 0x187, "plus" to 0x17c, "minus" to 0x17d, "insert" to 0x17c,
        "home" to 0x39e, "draw" to 0x20a, "eye" to 0x2ed, "check" to 0x185, "retry" to 0x149, "focus" to 0x2ed,
        "quiz" to 0x2f4, "reset" to 0x149, "answer" to 0x3cb, "hand" to 0x27b, "handfill" to 0x27c, "finger" to 0x939,
        "undo" to 0x134, "redo" to 0x135, "text" to 0x151, "lasso" to 0x4e9, "space" to 0x479,
        "library" to 0xb12, "page" to 0x237, "pageplus" to 0x3c7, "moon" to 0x966, "sun" to 0x1ab,
        "sidebar" to 0x3da, "split" to 0x9c8, "template" to 0x6e6, "image" to 0x3c5, "shape" to 0x4c2, "link" to 0x263,
        "fit" to 0x14a, "width" to 0x13e, "grid" to 0x6e6, "paper" to 0x23f, "trash" to 0x211, "star" to 0x2c2, "starfill" to 0x2c3,
        "folder" to 0x215, "folderplus" to 0x219, "clock" to 0x42b, "notes" to 0x23f, "list" to 0x2f1, "cover" to 0x25a,
        "sort" to 0x12c, "copy" to 0x241, "palette" to 0x765, "close" to 0x184, "bookmark" to 0x25e, "bookmarkfill" to 0x25f,
        "more" to 0x360, "menu" to 0x268, "move" to 0x215, "up2" to 0x187, "dup" to 0x405, "restore" to 0x134, "pdf" to 0x245,
        "ruler" to 0x7c0, "triangle" to 0x4c2, "wrong" to 0x060, "wrongfill" to 0x061, "rightfill" to 0x063, "right" to 0x062,
        "sliders" to 0x306, "wand" to 0x371, "rows" to 0x9ca, "cols" to 0x9c8, "grid4" to 0x9cc,
        "tray" to 0x223, "doc" to 0x237, "docfill" to 0x238, "binx" to 0x231, "warn" to 0x1fe, "book" to 0x25a,
        "pencilcircle" to 0x20b, "magnify" to 0x579, "zoomin" to 0x2ac, "zoomout" to 0x2ad,
    )
    fun cp(name: String): String? = map[name]?.let { String(Character.toChars(B + it)) }

    private val paint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT } }
    private val r = ThreadLocal.withInitial { ARect() }

    /** 상자(size) 가운데에 글리프를 그림. 글자 크기 = 상자 × scale */
    fun draw(ds: DrawScope, name: String, color: Color, weight: Int = 400, scale: Float = .84f): Boolean {
        val s = cp(name) ?: return false
        val tf = AppleFont.sym(weight) ?: return false
        val p = paint.get()!!; val b = r.get()!!
        p.typeface = tf; p.textSize = ds.size.minDimension * scale; p.color = color.toArgb()
        p.getTextBounds(s, 0, s.length, b)
        val x = (ds.size.width - b.width()) / 2f - b.left
        val y = (ds.size.height - b.height()) / 2f - b.top
        ds.drawContext.canvas.nativeCanvas.drawText(s, x, y, p)
        return true
    }
}
