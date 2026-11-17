package com.penlink.host

import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import com.penlink.Protocol as P
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/** Menyuntikkan MotionEvent stylus asli (pressure/tilt/hover) lewat Shizuku (UID shell). */
object PenInjector {
    private const val TAG = "PenInjector"
    private var service: Any? = null
    private var method: Method? = null
    private val props = arrayOf(MotionEvent.PointerProperties())
    private val coords = arrayOf(MotionEvent.PointerCoords())
    private var downTime = 0L
    private var down = false
    private var lx = 0f; private var ly = 0f; private var lw = 1; private var lh = 1
    private var ltool = MotionEvent.TOOL_TYPE_STYLUS

    val ready: Boolean get() = method != null

    @Synchronized
    fun prepare(): Boolean {
        if (method != null) return true
        return try {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val iface = Class.forName("android.hardware.input.IInputManager")
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            method = iface.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "prepare gagal", t); false
        }
    }

    @Synchronized
    fun inject(action: Int, tool: Int, buttons: Int, nx: Float, ny: Float, pressure: Float, tilt: Float, w: Int, h: Int) {
        val a = when (action) {
            P.A_DOWN -> MotionEvent.ACTION_DOWN
            P.A_UP -> MotionEvent.ACTION_UP
            P.A_MOVE -> MotionEvent.ACTION_MOVE
            P.A_CANCEL -> MotionEvent.ACTION_CANCEL
            P.A_HOVER_ENTER -> MotionEvent.ACTION_HOVER_ENTER
            P.A_HOVER_MOVE -> MotionEvent.ACTION_HOVER_MOVE
            P.A_HOVER_EXIT -> MotionEvent.ACTION_HOVER_EXIT
            else -> return
        }
        val tt = when (tool) {
            P.TOOL_STYLUS -> MotionEvent.TOOL_TYPE_STYLUS
            P.TOOL_ERASER -> MotionEvent.TOOL_TYPE_ERASER
            else -> MotionEvent.TOOL_TYPE_FINGER
        }
        var bs = 0
        if (buttons and P.BTN_PRIMARY != 0) bs = bs or MotionEvent.BUTTON_STYLUS_PRIMARY
        if (buttons and P.BTN_SECONDARY != 0) bs = bs or MotionEvent.BUTTON_STYLUS_SECONDARY
        lx = nx; ly = ny; lw = w; lh = h; ltool = tt
        val now = SystemClock.uptimeMillis()
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_HOVER_ENTER) downTime = now
        if (a == MotionEvent.ACTION_DOWN) down = true
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) down = false
        val p = if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP)
            pressure.coerceIn(0f, 1f) else 0f
        send(a, tt, bs, nx, ny, p, tilt, w, h, now)
    }

    /** Dipanggil saat client putus supaya goresan tidak "menggantung". */
    @Synchronized
    fun releaseStuck() {
        if (!down) return
        down = false
        send(MotionEvent.ACTION_CANCEL, ltool, 0, lx, ly, 0f, 0f, lw, lh, SystemClock.uptimeMillis())
    }

    private fun send(a: Int, tt: Int, bs: Int, nx: Float, ny: Float, p: Float, tilt: Float, w: Int, h: Int, now: Long) {
        val m = method ?: return
        props[0].clear(); props[0].id = 0; props[0].toolType = tt
        coords[0].clear()
        coords[0].x = (nx.coerceIn(0f, 1f) * (w - 1)); coords[0].y = (ny.coerceIn(0f, 1f) * (h - 1))
        coords[0].pressure = p; coords[0].size = 0.1f
        coords[0].setAxisValue(MotionEvent.AXIS_TILT, tilt)
        val source = if (tt == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS
        val ev = MotionEvent.obtain(downTime, now, a, 1, props, coords, 0, bs, 1f, 1f, 0, 0, source, 0)
        try {
            m.invoke(service, ev, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        } catch (t: Throwable) {
            Log.e(TAG, "inject gagal", t)
        } finally {
            ev.recycle()
        }
    }
}
