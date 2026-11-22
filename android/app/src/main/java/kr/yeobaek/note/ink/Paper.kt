package kr.yeobaek.note.ink

import android.graphics.Color
import kr.yeobaek.note.Y

/** 종이 색 (style.css의 --paper, --paper-line, --paper-line2, --paper-label, --ink) */
class PaperColors(
    val dark: Boolean,
    val paper: Int, val line: Int, val line2: Int, val label: Int, val ink: Int,
    val red: Int, val green: Int, val desk: Int, val acc: Int, val hair: Int, val star: Int,
)

object Paper {
    private var cache: PaperColors? = null
    private var key = ""
    /** 내보내기 중에는 늘 밝은 종이 */
    @Volatile var forceLight = false
    fun c(): PaperColors {
        val dark = Y.isDark() && !forceLight; val gray = !forceLight && Y.paperGray()
        val k = "$dark$gray"
        cache?.let { if (key == k) return it }
        key = k
        return (if (dark) PaperColors(
            true, 0xFF26272C.toInt(), 0xFF3B3D44.toInt(), 0xFF303238.toInt(), 0xFF9A9DA6.toInt(), 0xFFE8E9EE.toInt(),
            0xFFFF8787.toInt(), 0xFF8CE99A.toInt(), 0xFF141518.toInt(), 0xFF8FA0FF.toInt(), 0x1AFFFFFF, 0xFFF59F00.toInt(),
        ) else PaperColors(
            false, if (gray) 0xFFF8F9FA.toInt() else Color.WHITE, if (gray) 0xFFCED4DA.toInt() else 0xFFCED4DA.toInt(),
            if (gray) 0xFFDEE2E6.toInt() else 0xFFE9ECEF.toInt(), 0xFF868E96.toInt(), 0xFF212529.toInt(),
            0xFFE03131.toInt(), 0xFF2F9E44.toInt(), 0xFFDEE2E6.toInt(), 0xFF4C5FD5.toInt(), 0x1F212529, 0xFFF59F00.toInt(),
        )).also { cache = it }
    }
}
