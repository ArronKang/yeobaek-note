package kr.yeobaek.note

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/** 기기 없이 앱 화면 전체를 그림으로: app/build/shots/ui-*.png (./gradlew testDebugUnitTest --tests '*UiShotTest*') */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-xhdpi")
class UiShotTest {
    private val out = File("build/shots").apply { mkdirs() }

    private fun idle(ms: Long = 600) { repeat((ms / 50).toInt()) { ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS) } }
    private fun cap(a: Activity, name: String) {
        idle()
        val v = a.window.decorView
        val bm = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bm))
        File(out, "ui-$name.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun screens() {
        val ac = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ac.get()
        cap(a, "library")
        val m = Nav.newNote("생명과학 3단원", "sage", "lined", null)
        Nav.open(m)
        idle(1500)
        cap(a, "editor")
    }
}
