package kr.yeobaek.note

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kr.yeobaek.note.data.Doc
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.editor.DocView
import kr.yeobaek.note.editor.EditorBack
import kr.yeobaek.note.editor.EditorScreen
import kr.yeobaek.note.export.Export
import kr.yeobaek.note.library.L
import kr.yeobaek.note.library.LibraryScreen
import kr.yeobaek.note.ui.Dark
import kr.yeobaek.note.ui.Light
import kr.yeobaek.note.ui.LocalTok
import kr.yeobaek.note.ui.Out
import kr.yeobaek.note.ui.Overlay
import kr.yeobaek.note.ui.OverlayHost

object BuildConfigInfo {
    val version: String get() = runCatching { Y.app.packageManager.getPackageInfo(Y.app.packageName, 0).versionName }.getOrNull() ?: ""
}

class MainActivity : ComponentActivity() {
    private var pendingExport: Pair<Doc, DocView>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Y.init(this)
        enableEdgeToEdge()
        if (savedInstanceState != null) Nav.restoreState(savedInstanceState) else handleIntent(intent)
        setContent { App() }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); Nav.saveState(outState) }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    /** 다른 앱에서 PDF / .ybq 열기 */
    private fun handleIntent(i: Intent?) {
        val uri: Uri = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        Nav.import(uri, null) { m -> if (m != null) { L.go(if (m.kind == "quiz") "quiz" else if (m.kind == "pdf") "pdf" else "all"); Nav.open(m) } }
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        Y.sysDark = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onStop() {
        super.onStop()
        Nav.tabs.forEach { runCatching { Docs.save(it.doc) } }
    }

    /** 화면을 떠났거나 메모리가 부족하면 페이지 그림을 내려놓음 (돌아오면 다시 그림) → 시스템이 앱을 덜 정리함 */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            kr.yeobaek.note.editor.DocView.trimAll()
            kr.yeobaek.note.quiz.QuizLayout.clearCache()
        }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val dark = Y.isDark()
        val tok = if (dark) Dark else Light
        LaunchedEffect(dark) {
            WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            window.decorView.setBackgroundColor(tok.bg.toArgb())
        }
        val scope = rememberCoroutineScope()

        // 가져오기 (PDF · .ybq)
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) Nav.import(uri, L.folderNow()) { m ->
                if (m == null) return@import
                if (!L.view.startsWith("f:")) L.go(if (m.kind == "quiz") "quiz" else if (m.kind == "pdf") "pdf" else "all")
                Nav.open(m)
            }
        }
        L.importPick = { pick.launch(arrayOf("application/pdf", "application/json", "application/octet-stream", "*/*")) }

        // 내보내기: 파일로 저장
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val (doc, v) = pendingExport ?: return@rememberLauncherForActivityResult
            pendingExport = null
            if (uri == null) return@rememberLauncherForActivityResult
            Nav.busy = true
            scope.launch {
                val ok = runCatching { contentResolver.openOutputStream(uri)?.use { Export.build(doc, v, it) } }.isSuccess
                Nav.busy = false
                Overlay.toast(if (ok) "저장했어요" else "내보내지 못했어요")
            }
        }
        val onExport: (Doc, DocView) -> Unit = { doc, v ->
            if (Y.settings.exportDir == "share") {
                Nav.busy = true
                scope.launch {
                    val r = runCatching { Export.share(doc, v) { startActivity(it) } }
                    Nav.busy = false
                    if (r.isFailure) Overlay.toast("내보내지 못했어요")
                }
            } else { pendingExport = doc to v; save.launch(Export.fileName(doc)) }
        }

        // 편집 중 화면 꺼짐 방지
        LaunchedEffect(Nav.inEditor) {
            if (Nav.inEditor) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 안드로이드 뒤로가기: 메뉴 → 창 → 정답 카드·글상자·사이드바 → 편집 화면 → 서재 서랍·선택
        BackHandler {
            when {
                Overlay.closePop() -> Unit
                Overlay.closeTop() -> Unit
                EditorBack.handler?.invoke() == true -> Unit
                Nav.inEditor -> Nav.closeEditor()
                L.back() -> Unit
                else -> finish()
            }
        }

        CompositionLocalProvider(LocalTok provides tok) {
            // 종이를 들어 올리듯: 편집 화면은 살짝 올라오며, 서재는 살짝 물러남
            val p = remember { Animatable(if (Nav.inEditor) 1f else 0f) }
            var shown by remember { mutableStateOf(Nav.cur) }
            LaunchedEffect(Nav.inEditor) {
                if (Nav.inEditor) { shown = Nav.cur; p.animateTo(1f, tween(460, easing = Out)) }
                else { p.animateTo(0f, tween(380, easing = Out)) }
            }
            LaunchedEffect(Nav.cur, Nav.editorRev) { if (Nav.inEditor) shown = Nav.cur }
            Box(Modifier.fillMaxSize()) {
                if (p.value < 1f) Box(Modifier.fillMaxSize().graphicsLayer { val s = 1f - .03f * p.value; scaleX = s; scaleY = s; alpha = 1f - .6f * p.value }) { LibraryScreen() }
                val cur = if (Nav.inEditor) Nav.cur else shown
                if (p.value > 0f && cur != null) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        alpha = p.value; translationY = (1 - p.value) * 18.dp.toPx(); val s = .985f + .015f * p.value; scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(.5f, .3f)
                    }) { androidx.compose.runtime.key(cur.key) { EditorScreen(cur, onExport) } }
                }
                // 상태 표시줄 자리: 앱 화면과 겹치지 않게 단색 배경
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(if (Nav.inEditor) androidx.compose.ui.graphics.Color(kr.yeobaek.note.ink.Paper.c().desk) else tok.bg))
                OverlayHost()
            }
        }
    }
}
