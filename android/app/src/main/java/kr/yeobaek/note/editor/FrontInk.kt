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
                    // 확정된 필기는 아래 DocView가 그림: 이 층은 투명하게
                    canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
                }
            })
        }.getOrNull()
        ok = renderer != null
    }

    fun draw(seg: Seg) { val r = renderer ?: return; dirty = true; r.renderFrontBufferedLayer(seg) }

    fun clear() { if (!dirty) return; dirty = false; renderer?.clear() }

    fun release() { renderer?.release(true); renderer = null; ok = false }
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
