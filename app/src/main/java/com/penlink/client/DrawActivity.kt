package com.penlink.client

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.penlink.dp
import java.util.concurrent.atomic.AtomicInteger

class DrawActivity : AppCompatActivity(), PenClient.Listener {
    private lateinit var canvasV: PenCanvasView
    private lateinit var stats: TextView
    private val frames = AtomicInteger()
    @Volatile private var rtt = 0f
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            stats.text = "${frames.getAndSet(0)} fps • RTT %.1f ms".format(rtt)
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        canvasV = PenCanvasView(this).apply { penOnly = intent.getBooleanExtra("penOnly", true) }
        stats = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 11f; setBackgroundColor(0x66000000)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val close = TextView(this).apply {
            text = "✕"; setTextColor(Color.WHITE); textSize = 18f; setBackgroundColor(0x66000000)
            setPadding(dp(14), dp(6), dp(14), dp(6)); setOnClickListener { finish() }
        }
        setContentView(FrameLayout(this).apply {
            addView(canvasV, FrameLayout.LayoutParams(-1, -1))
            addView(stats, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
            addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        })
        PenClient.listener = this
        PenClient.lastFrame?.let { canvasV.submit(it) }
        h.post(tick)
    }

    override fun onFrame(jpeg: ByteArray) { frames.incrementAndGet(); canvasV.submit(jpeg) }
    override fun onRtt(ms: Float) { rtt = ms }
    override fun onState(s: PenClient.State, msg: String) {
        if (s == PenClient.State.DISCONNECTED) runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish()
        }
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        if (PenClient.listener === this) PenClient.listener = null
        PenClient.disconnect()
        super.onDestroy()
    }
}
