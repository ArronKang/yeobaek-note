package kr.yeobaek.note

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import kr.yeobaek.note.data.Docs
import kr.yeobaek.note.data.TextBox
import kr.yeobaek.note.editor.DocView
import kr.yeobaek.note.quiz.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 기기 없이: 앱 시작 → 예제 문제지 → 페이지를 그림으로 (app/build/shots 폴더) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-xhdpi")
class RenderTest {
    private val out = File("build/shots").apply { mkdirs() }

    private fun shot(v: DocView, name: String) {
        val bm = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bm))
        File(out, "$name.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun layout(v: View, w: Int = 1600, h: Int = 2560) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        ShadowLooper.idleMainLooper()
    }
    private fun ev(action: Int, x: Float, y: Float, tool: Int, t: Long): MotionEvent {
        val pp = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val pc = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .6f; touchMajor = 10f; touchMinor = 10f })
        return MotionEvent.obtain(t, t, action, 1, pp, pc, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test fun seedAndRender() {
        Y.init(ApplicationProvider.getApplicationContext())
        val quizzes = Y.lib.notes.filter { it.kind == "quiz" }
        assertEquals(5, quizzes.size)
        quizzes.forEach { n ->
            Quiz.prep(n)
            val doc = Quiz.quizDoc(n)!!
            assertTrue(doc.pages.isNotEmpty())
            val v = DocView(ApplicationProvider.getApplicationContext(), doc, object : DocView.Host {})
            layout(v)
            shot(v, "quiz-" + n.title.take(8).replace(Regex("[^A-Za-z0-9가-힣]"), "_"))
            println("${n.title}: ${doc.pages.size}쪽, 문항 ${doc.quiz!!.problems.size}")
        }
    }

    @Test fun noteDrawing() {
        Y.init(ApplicationProvider.getApplicationContext())
        val m = Nav.newNote("테스트", "sage", "lined", null)
        val doc = Docs.noteDoc(m)
        val v = DocView(ApplicationProvider.getApplicationContext(), doc, object : DocView.Host {})
        layout(v)
        // 펜으로 물결 하나
        var t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 200f, 500f, MotionEvent.TOOL_TYPE_STYLUS, t))
        for (k in 1..60) { t += 8; v.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, 200f + k * 18f, 500f + (Math.sin(k / 6.0) * 80).toFloat(), MotionEvent.TOOL_TYPE_STYLUS, t)) }
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 1280f, 500f, MotionEvent.TOOL_TYPE_STYLUS, t + 8))
        ShadowLooper.idleMainLooper()
        assertEquals(1, doc.pages[0].strokes.size)
        doc.pages[0].texts.add(TextBox("t", .1f, .5f, .5f, .03f, "#2B4FD8", "여백 노트 글상자"))
        v.dirtyAll()
        shot(v, "note")
        v.undo(); assertEquals(0, doc.pages[0].strokes.size)
        v.redo(); assertEquals(1, doc.pages[0].strokes.size)
        // 손가락으로 위로 밀면 스크롤 (펜을 뗀 직후는 손바닥으로 보고 무시하므로 1초 뒤)
        v.addPage(); v.addPage()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2))
        t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 800f, 2000f, MotionEvent.TOOL_TYPE_FINGER, t))
        for (k in 1..10) { t += 16; v.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, 800f, 2000f - k * 120f, MotionEvent.TOOL_TYPE_FINGER, t)) }
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 800f, 800f, MotionEvent.TOOL_TYPE_FINGER, t + 16))
        assertTrue("스크롤됨: ${v.sy}", v.sy > 500f)
        assertEquals(1, doc.pages[0].strokes.size)
        v.saveNow()
        Y.settings.dark = "on"; v.themeChanged(); shot(v, "note-dark"); Y.settings.dark = "off"
    }
}
