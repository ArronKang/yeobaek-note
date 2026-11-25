package kr.yeobaek.note.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.yeobaek.note.BuildConfigInfo
import kr.yeobaek.note.Y
import kr.yeobaek.note.editor.GlassBtn
import kr.yeobaek.note.editor.GlassSlider
import kr.yeobaek.note.editor.MiniSeg
import kr.yeobaek.note.quiz.QuizLayout
import kr.yeobaek.note.ui.Ic
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.Spring
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.txt
import kotlin.math.roundToInt

/* Apple iOS 27 UI 키트: 시트 모서리 34, 묶음 목록 칸 52·모서리 26·구분선 12%, 토글 64×28(켬 초록), 세그먼트 32 */

/** 설정 시트 (18 설정). 이름은 사용자가 캔버스에서 고친 그대로 */
fun openSettings() {
    Overlay.modal(width = 704.dp, bare = true) { close -> SettingsSheet(close) }
}

@Composable
private fun SettingsSheet(close: () -> Unit) {
    Y.setRev; Y.penRev
    val S = Y.settings
    fun save() { Y.saveSettings() }
    fun redraw() { QuizLayout.clearCache(); save() }
    val maxH = (LocalConfiguration.current.screenHeightDp * .86f).dp
    Column(Modifier.heightIn(max = maxH)) {
        // 머리: 닫기 · 설정 · 완료
        Box(Modifier.fillMaxWidth().height(64.dp).padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
            GlassBtn("close", "닫기", Modifier.align(Alignment.CenterStart)) { close() }
            BasicText("설정", Modifier.align(Alignment.Center), style = txt(17.sp, 600, T.snow))
            Box(Modifier.align(Alignment.CenterEnd).size(44.dp).shadow(10.dp, CircleShape, ambientColor = Color(0xFF0088FF), spotColor = Color(0xFF0088FF)).background(Color(0xFF0088FF), CircleShape)
                .pointerInput(Unit) { detectTapGestures { close() } }, contentAlignment = Alignment.Center) { Ic("check", size = 20.dp, color = Color.White, stroke = 2.2f) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Group("필기") {
                Row1("손가락으로 쓰기") { Toggle(S.finger) { S.finger = it; save() } }
                Row1("손바닥 무시 감도") { MiniSeg(S.palm, listOf("weak" to "약", "mid" to "중", "strong" to "강")) { S.palm = it; save() } }
                Row1("필기 보정") { Toggle(S.snap) { S.snap = it; save() } }
                Row1("필압 민감도") { MiniSeg(S.pressure, listOf("low" to "낮음", "mid" to "보통", "high" to "높음")) { S.pressure = it; save() } }
                Row1("펜 개수") { Stepper(S.trayCount, 1, 12) { n -> while (Y.pens.size < n) Y.pens.add(kr.yeobaek.note.data.Pen("pen", "ball", kr.yeobaek.note.editor.PK.ORIG[Y.pens.size % 5], 0.8f, 1f)); S.trayCount = n; save(); Y.savePens() } }
                Row1("펜 막대에 자") { Toggle(S.ruler) { S.ruler = it; save() } }
                Row1("S Pen 버튼") { MiniSeg(S.penButton, listOf("eraser" to "지우개", "lasso" to "올가미")) { S.penButton = it; save() } }
            }
            Group("화면") {
                Row1("다크 모드") { MiniSeg(S.dark, listOf("off" to "Off", "on" to "On", "auto" to "기기")) { S.dark = it; redraw() } }
                Row1("배경 색깔") { MiniSeg(S.paper, listOf("white" to "흰색", "gray" to "회색")) { S.paper = it; redraw() } }
                // 투명도: 퍼센트로
                val pct = if (S.glassPct >= 0) S.glassPct else when (S.glass) { "clear" -> 20; "solid" -> 100; else -> 60 }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("투명도", style = txt(17.sp, 400, T.snow))
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) { GlassSlider(pct.toFloat(), 0f, 100f, Color(0xFF0088FF)) { S.glassPct = it.roundToInt(); save() } }
                    BasicText("$pct%", Modifier.width(52.dp), style = txt(17.sp, 400, Color(0xFF8E8E93)).copy(textAlign = TextAlign.End))
                }
                Row1("페이지 눈금") { Toggle(S.scale) { S.scale = it; save() } }
            }
            Group("저장") {
                Row1("자동 저장") { MiniSeg(S.autosave, listOf(0 to "즉시", 1000 to "1초", 5000 to "5초")) { S.autosave = it; save() } }
                Row1("내보내기") { MiniSeg(S.exportDir, listOf("files" to "파일", "share" to "공유")) { S.exportDir = it; save() } }
                Row1("휴지통 보관") { MiniSeg(S.trashDays, listOf(7 to "7일", 30 to "30일", 90 to "90일")) { S.trashDays = it; save() } }
            }
            BasicText(BuildConfigInfo.version, Modifier.fillMaxWidth().padding(bottom = 8.dp), style = txt(13.sp, 400, Color(0xFF8E8E93)).copy(textAlign = TextAlign.Center))
        }
    }
}

/** 묶음 목록 (Lists/Grouped): 머리 + 흰 묶음(모서리 26), 칸 사이 구분선 */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        BasicText(title, Modifier.padding(start = 20.dp, bottom = 8.dp), style = txt(17.sp, 600, if (Y.isDark()) Color(0x99EBEBF5) else Color(0x993C3C43)))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(if (Y.isDark()) Color(0xFF2C2C2E) else Color.White), content = content)
    }
}

@Composable
private fun Row1(label: String, ctrl: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = txt(17.sp, 400, T.snow))
            ctrl()
        }
        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(.5.dp).background(if (Y.isDark()) Color(0x26FFFFFF) else Color(0x1F000000)))
    }
}

/** 토글 (Toggles: 64×28, 켬 초록 #34C759, 손잡이 38×24 흰 캡슐이 딸깍 미끄러짐) */
@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = Spring), label = "tg")
    Box(Modifier.size(64.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFF34C759) else Color(0x29787880))
        .pointerInput(on) { detectTapGestures { Y.haptic(6); onChange(!on) } }) {
        Box(Modifier.offset(x = 2.dp + 22.dp * x, y = 2.dp).size(38.dp, 24.dp).shadow(3.dp, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun Stepper(v: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x1F767680)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp, 32.dp).pointerInput(v) { detectTapGestures { if (v > min) { Y.haptic(6); onChange(v - 1) } } }, contentAlignment = Alignment.Center) { Ic("minus", size = 15.dp, color = T.snow, stroke = 2f) }
        BasicText("$v", Modifier.width(28.dp), style = txt(15.sp, 600, T.snow).copy(textAlign = TextAlign.Center))
        Box(Modifier.size(46.dp, 32.dp).pointerInput(v) { detectTapGestures { if (v < max) { Y.haptic(6); onChange(v + 1) } } }, contentAlignment = Alignment.Center) { Ic("plus", size = 15.dp, color = T.snow, stroke = 2f) }
    }
}
