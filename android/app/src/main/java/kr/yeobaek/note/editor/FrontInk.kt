package kr.yeobaek.note.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import kr.yeobaek.note.data.Stroke
import kr.yeobaek.note.ink.Ink

/**
 * 저지연 필기 층: 쓰는 중인 획을 화면 합성(HWUI)을 거치지 않고 바로 그려서 펜 끝을 바짝 따라오게 함.
 * - 쓰는 동안: 새로 확정된 마디만 앞 버퍼(front buffer)에 덧그림
 * - 펜을 떼면: 아래 DocView가 같은 획을 그린 뒤 지움 (두 프레임 뒤)
 * 형광펜(겹치면 진해짐)·지우개·올가미는 예전 방식 그대로.
 */
class FrontInk(ctx: Context) {
    /** 한 번 그릴 몫: 획 모양(복사본), 그릴 마디 범위, 페이지 위치 */
    class Seg(val st: Stroke, val from: Int, val to: Int, val left: Float, val top: Float, val K: Float, val dark: Boolean, val t: Long = 0L)

    val surface = SurfaceView(ctx).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }
    private var renderer: CanvasFrontBufferedRenderer<Seg>? = null
    var ok = false; private set
    @Volatile private var dirty = false
    /** 마지막으로 지운 뒤 아무것도 안 그렸음 (화면이 움직일 때마다 지우기 반복 방지) */
    private var cleared = false
    /** 펜을 뗐을 때 여러 버퍼 층에 그릴 최종 획 */
    @Volatile private var final: Seg? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val clearRun = Runnable { clearNow() }

    init {
        // 표면이 다시 만들어지면(탭 전환·홈 다녀오기) 예전 획이 남지 않게 비움
        surface.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(h: android.view.SurfaceHolder) { main.post { dirty = false; final = null; cleared = true; renderer?.clear() } }
            override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: android.view.SurfaceHolder) { main.removeCallbacks(clearRun); dirty = false; final = null }
        })
    }

    fun attach() {
        if (renderer != null) return
        renderer = runCatching {
            CanvasFrontBufferedRenderer(surface, object : CanvasFrontBufferedRenderer.Callback<Seg> {
                override fun onDrawFrontBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: Seg) {
                    canvas.save(); canvas.translate(param.left, param.top)
                    Ink.draw(canvas, param.st, param.K, param.dark, from = param.from, to = param.to)
                    canvas.restore()
                }
                override fun onDrawMultiBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, params: Collection<Seg>) {
                    canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
                    // 펜을 뗀 순간: 앞 버퍼의 획을 최종 모양으로 바꿔 같은 프레임에 보여 줌 (아래 화면이 그릴 때까지)
                    final?.let { f -> canvas.save(); canvas.translate(f.left, f.top); Ink.draw(canvas, f.st, f.K, f.dark); canvas.restore() }
                }
            })
        }.getOrNull()
        ok = renderer != null
    }

    fun draw(seg: Seg) {
        val r = renderer ?: return
        // 앞 획을 아직 겹쳐 두고 있으면 먼저 지움 (아래 화면은 이미 그렸음)
        if (final != null) clearNow()
        dirty = true; cleared = false; r.renderFrontBufferedLayer(seg)
    }

    /** 펜을 뗌: 앞 버퍼 내용을 여러 버퍼 층으로 넘기면서 최종 모양으로 (한 번에 바뀜) */
    fun finish(seg: Seg) {
        val r = renderer ?: return
        final = seg; cleared = false; r.commit()
        main.removeCallbacks(clearRun); main.postDelayed(clearRun, 250)
    }

    /** 몇 프레임 뒤 지움 (모양 보정 등) */
    fun clearSoon() { main.removeCallbacks(clearRun); main.postDelayed(clearRun, 120) }

    fun clearNow(force: Boolean = false) {
        main.removeCallbacks(clearRun)
        if (!force && !dirty && final == null) return
        if (force && !dirty && final == null && cleared) return
        cleared = true
        dirty = false; final = null; renderer?.clear()
    }
    fun clear() = clearNow()

    fun release() { main.removeCallbacks(clearRun); renderer?.release(true); renderer = null; ok = false }
}

/** 필기 화면 + 그 위 저지연 층 (터치는 아래 DocView가 받음) */
@SuppressLint("ViewConstructor")
class DocFrame(ctx: Context, val view: DocView) : FrameLayout(ctx) {
    val front = FrontInk(ctx)
    init {
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(front.surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        front.surface.isClickable = false; front.surface.isFocusable = false
        view.front = front
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); front.attach() }
    fun release() { view.front = null; front.release(); view.release() }
}
