package com.penlink.client

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.penlink.Protocol as P
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/** Menampilkan frame host (fit-center) dan mengirim event pena (termasuk history batching, tekanan, tilt, hover). */
class PenCanvasView(ctx: Context) : View(ctx) {
    var penOnly = true
    @Volatile private var shown: Bitmap? = null
    private var spare: Bitmap? = null
    private val dst = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pending = AtomicReference<ByteArray?>()
    private val decoder = Executors.newSingleThreadExecutor()
    private val opts = BitmapFactory.Options()

    init { setBackgroundColor(Color.BLACK) }

    /** Latest-only: frame lama yang belum didekode otomatis tertimpa. */
    fun submit(jpeg: ByteArray) {
        pending.set(jpeg)
        decoder.execute {
            val d = pending.getAndSet(null) ?: return@execute
            opts.inMutable = true; opts.inBitmap = spare
            val bmp = try { BitmapFactory.decodeByteArray(d, 0, d.size, opts) }
            catch (e: IllegalArgumentException) { opts.inBitmap = null; BitmapFactory.decodeByteArray(d, 0, d.size, opts) }
            if (bmp != null) { spare = shown; shown = bmp; postInvalidateOnAnimation() }
        }
    }

    override fun onDraw(c: Canvas) {
        val b = shown ?: return
        val s = min(width / b.width.toFloat(), height / b.height.toFloat())
        val dw = b.width * s; val dh = b.height * s
        val l = (width - dw) / 2; val t = (height - dh) / 2
        dst.set(l, t, l + dw, t + dh)
        c.drawBitmap(b, null, dst, paint)
    }

    private fun tool(e: MotionEvent) = when (e.getToolType(0)) {
        MotionEvent.TOOL_TYPE_STYLUS -> P.TOOL_STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> P.TOOL_ERASER
        else -> P.TOOL_FINGER
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val tool = tool(e)
        if (penOnly && tool == P.TOOL_FINGER) return true // palm rejection sederhana
        val act = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> P.A_DOWN
            MotionEvent.ACTION_UP -> P.A_UP
            MotionEvent.ACTION_MOVE -> P.A_MOVE
            MotionEvent.ACTION_CANCEL -> P.A_CANCEL
            else -> return true
        }
        if (act == P.A_DOWN) requestUnbufferedDispatch(e)
        send(e, act, tool)
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val tool = tool(e)
        if (tool == P.TOOL_FINGER) return false
        val act = when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> P.A_HOVER_ENTER
            MotionEvent.ACTION_HOVER_MOVE -> P.A_HOVER_MOVE
            MotionEvent.ACTION_HOVER_EXIT -> P.A_HOVER_EXIT
            else -> return false
        }
        send(e, act, tool)
        return true
    }

    private fun send(e: MotionEvent, act: Int, tool: Int) {
        if (dst.isEmpty) return
        val hist = if (act == P.A_MOVE) e.historySize else 0
        val n = hist + 1
        val bb = ByteBuffer.allocate(2 + n * P.SAMPLE_SIZE)
        bb.put(P.MSG_INPUT); bb.put(n.toByte())
        var btn = 0
        if (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) btn = btn or P.BTN_PRIMARY
        if (e.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) btn = btn or P.BTN_SECONDARY
        val hover = act >= P.A_HOVER_ENTER
        for (i in 0 until n) {
            val h = i < hist
            val x = if (h) e.getHistoricalX(0, i) else e.getX(0)
            val y = if (h) e.getHistoricalY(0, i) else e.getY(0)
            val p = if (hover) 0f else (if (h) e.getHistoricalPressure(0, i) else e.getPressure(0)).coerceIn(0f, 1f)
            val tilt = if (h) e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, i) else e.getAxisValue(MotionEvent.AXIS_TILT, 0)
            bb.put(act.toByte()); bb.put(tool.toByte()); bb.put(btn.toByte())
            bb.putFloat(((x - dst.left) / dst.width()).coerceIn(0f, 1f))
            bb.putFloat(((y - dst.top) / dst.height()).coerceIn(0f, 1f))
            bb.putFloat(p); bb.putFloat(tilt)
        }
        bb.flip()
        PenClient.sendInput(bb)
    }
}
