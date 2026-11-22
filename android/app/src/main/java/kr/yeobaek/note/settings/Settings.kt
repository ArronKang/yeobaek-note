package kr.yeobaek.note.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.yeobaek.note.BuildConfigInfo
import kr.yeobaek.note.Y
import kr.yeobaek.note.quiz.QuizLayout
import kr.yeobaek.note.ui.IconBtn
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.Seg
import kr.yeobaek.note.ui.Switch
import kr.yeobaek.note.ui.T
import kr.yeobaek.note.ui.txt

/** 설정: 한 줄씩, 선택지는 글자로 (설명 문구 없음) */
fun openSettings() {
    Overlay.modal(page = true) { close -> SettingsPage(close) }
}

@Composable
private fun SettingsPage(close: () -> Unit) {
    Y.setRev; Y.penRev
    val S = Y.settings
    val sys = WindowInsets.systemBars.asPaddingValues()
    fun save() { Y.saveSettings() }
    fun redraw() { QuizLayout.clearCache(); save() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(top = sys.calculateTopPadding() + 32.dp, bottom = sys.calculateBottomPadding() + 24.dp, start = 20.dp, end = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn("back", "닫기") { close() }
                Spacer(Modifier.width(8.dp))
                BasicText("설정", style = txt(28.sp, 600, T.snow, -.02f))
            }
            H("필기")
            R("손가락으로 쓰기") { Switch(S.finger) { S.finger = it; save() } }
            R("S Pen 버튼") { Seg(S.penButton, listOf("eraser" to "지우개", "lasso" to "올가미")) { S.penButton = it; save() } }
            R("모양 보정") { Switch(S.snap) { S.snap = it; save() } }
            R("손바닥 무시") { Seg(S.palm, listOf("weak" to "약", "mid" to "중", "strong" to "강")) { S.palm = it; save() } }
            H("화면")
            R("다크 모드") { Seg(S.dark, listOf("off" to "끔", "on" to "켬", "auto" to "기기")) { S.dark = it; redraw() } }
            R("도구 막대 투명도") { Seg(S.glass, listOf("clear" to "투명", "mid" to "반투명", "solid" to "불투명")) { S.glass = it; save() } }
            R("종이 색") { Seg(S.paper, listOf("white" to "흰색", "gray" to "회색")) { S.paper = it; redraw() } }
            R("펜 칸 개수") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn("minus", "줄이기", size = 32.dp, iconSize = 18.dp) { S.trayCount = (S.trayCount - 1).coerceIn(3, 8); save() }
                    BasicText("${S.trayCount}", Modifier.padding(horizontal = 6.dp), style = txt(15.sp, 500, T.snow))
                    IconBtn("plus", "늘리기", size = 32.dp, iconSize = 18.dp) { S.trayCount = (S.trayCount + 1).coerceIn(3, 8); save() }
                }
            }
            R("진동") { Seg(S.haptic, listOf("off" to "끔", "mid" to "약", "strong" to "강")) { S.haptic = it; save(); Y.haptic(10) } }
            H("저장")
            R("자동 저장") { Seg(S.autosave, listOf(0 to "즉시", 1000 to "1초", 5000 to "5초")) { S.autosave = it; save() } }
            R("내보내기") { Seg(S.exportDir, listOf("files" to "파일", "share" to "공유")) { S.exportDir = it; save() } }
            R("휴지통 보관") { Seg(S.trashDays, listOf(7 to "7일", 30 to "30일", 90 to "90일")) { S.trashDays = it; save() } }
            BasicText(BuildConfigInfo.version, Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp), style = txt(12.sp, 400, T.ink2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        }
    }
}

@Composable private fun H(t: String) = BasicText(t, Modifier.padding(top = 36.dp, bottom = 4.dp), style = txt(12.sp, 500, T.ink2))

@Composable
private fun R(label: String, ctrl: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = txt(15.sp, 400, T.snow))
            ctrl()
        }
        Box(Modifier.fillMaxWidth().height(.5.dp).background(T.hair))
    }
}
