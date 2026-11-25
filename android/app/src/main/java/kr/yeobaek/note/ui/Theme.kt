package kr.yeobaek.note.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kr.yeobaek.note.Y

/** style.css 토큰 그대로 (회색 9단계 + 강조색 하나) */
@Immutable
class Tok(
    val dark: Boolean,
    val bg: Color, val night: Color, val raise: Color, val desk: Color,
    val snow: Color, val mist: Color, val ink2: Color,
    val hair: Color, val fill: Color, val fill2: Color,
    val acc: Color, val primary: Color, val onPrimary: Color,
    val floatBg: Color, val floatFg: Color, val glass: Color, val glassEdge: Color,
    val danger: Color, val slate: Color, val switchOff: Color,
)

val Light = Tok(
    false,
    bg = Color(0xFFF8F9FA), night = Color(0xFFE9ECEF), raise = Color.White, desk = Color(0xFFDEE2E6),
    snow = Color(0xFF212529), mist = Color(0xFF6C757D), ink2 = Color(0xFF6C757D),
    hair = Color(0x1F212529), fill = Color(0x0F212529), fill2 = Color(0x1A212529),
    acc = Color(0xFF4C5FD5), primary = Color(0xFF212529), onPrimary = Color(0xFFF8F9FA),
    floatBg = Color(0xFF212529), floatFg = Color(0xFFF8F9FA), glass = Color(0xB8F8F9FA), glassEdge = Color(0x14212529),
    danger = Color(0xFFE03131), slate = Color(0xFFCED4DA), switchOff = Color(0xFFCED4DA),
)
val Dark = Tok(
    true,
    bg = Color(0xFF1D1E22), night = Color(0xFF1D1E22), raise = Color(0xFF2B2C31), desk = Color(0xFF141518),
    snow = Color(0xFFECEEF2), mist = Color(0xFFA4A7B0), ink2 = Color(0xFFA4A7B0),
    hair = Color(0x1AFFFFFF), fill = Color(0x0FFFFFFF), fill2 = Color(0x1CFFFFFF),
    acc = Color(0xFF8FA0FF), primary = Color(0xFFECEEF2), onPrimary = Color(0xFF1D1E22),
    floatBg = Color(0xFFECEEF2), floatFg = Color(0xFF1D1E22), glass = Color(0xB82B2C31), glassEdge = Color(0x1AF8F9FA),
    danger = Color(0xFFFF8787), slate = Color(0xFF45474E), switchOff = Color(0xFF45474E),
)

val LocalTok = staticCompositionLocalOf { Light }
val T: Tok @Composable @ReadOnlyComposable get() = LocalTok.current

/** 모든 움직임은 감속 곡선 (처음 빠르고 끝에서 부드럽게) */
val Out = CubicBezierEasing(.16f, 1f, .3f, 1f)
/** 튀는 것 */
val Spring = CubicBezierEasing(.34f, 1.45f, .55f, 1f)

@OptIn(ExperimentalTextApi::class)
val Pretendard: FontFamily by lazy {
    fun f(w: Int) = Font("pretendard.ttf", Y.app.assets, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    FontFamily(f(400), f(500), f(600), f(700))
}

/**
 * Apple 키트 글꼴: 라틴 글자·숫자·SF Symbols 아이콘은 SF Pro, 한글은 Pretendard (iOS가 SF Pro + 한글 글꼴을 섞는 것과 같은 방식).
 * sfpro.ttf는 공개 저장소에 올리지 않아서, 없으면 Pretendard만 써요.
 */
object AppleFont {
    val ok: Boolean by lazy { runCatching { Y.app.assets.open("sfpro.ttf").close(); true }.getOrDefault(false) }
    private val sym = HashMap<Int, android.graphics.Typeface>()
    private val text = HashMap<Int, android.graphics.Typeface>()
    private val fam = HashMap<Int, FontFamily>()
    private fun font(file: String, w: Int) = android.graphics.fonts.Font.Builder(Y.app.assets, file)
        .setWeight(w.coerceIn(1, 1000)).setFontVariationSettings("'wght' $w").build()

    /** SF Pro만 (아이콘 그리기용) */
    fun sym(w: Int): android.graphics.Typeface? = if (!ok) null else sym.getOrPut(w) {
        android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font("sfpro.ttf", w)).build()).build()
    }
    /** SF Pro → Pretendard 순서로 글자마다 고르는 글꼴 */
    fun text(w: Int): android.graphics.Typeface = text.getOrPut(w) {
        val pret = android.graphics.fonts.FontFamily.Builder(font("pretendard.ttf", w.coerceIn(100, 900))).build()
        if (!ok) android.graphics.Typeface.CustomFallbackBuilder(pret).setSystemFallback("sans-serif").build()
        else android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font("sfpro.ttf", w)).build())
            .addCustomFallback(pret).setSystemFallback("sans-serif").build()
    }
    fun family(w: Int): FontFamily = fam.getOrPut(w) { FontFamily(androidx.compose.ui.text.font.Typeface(text(w))) }
}

fun txt(size: TextUnit = 15.sp, weight: Int = 400, color: Color = Color.Unspecified, ls: Float = 0f) =
    TextStyle(fontFamily = AppleFont.family(weight), fontSize = size, fontWeight = FontWeight(weight), color = color,
        fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.None,
        letterSpacing = if (ls != 0f) (ls * size.value).sp else TextUnit.Unspecified)
