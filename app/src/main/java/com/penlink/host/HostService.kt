package com.penlink.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.penlink.Protocol
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

class HostService : Service() {
    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var server: PenServer? = null
    private var thread: Thread? = null
    private var wl: PowerManager.WakeLock? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_NEWCODE -> newCode()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun start(intent: Intent) {
        if (running) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "PenLink Host", NotificationManager.IMPORTANCE_LOW))
        val notif: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("PenLink Host aktif")
            .setContentText("Berbagi layar & menerima input pena")
            .setOngoing(true).build()
        ServiceCompat.startForeground(
            this, 1, notif,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )

        val rc = intent.getIntExtra(EXTRA_RC, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return }

        val dm = DisplayMetrics()
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        val rw = dm.widthPixels; val rh = dm.heightPixels
        val scale = min(1f, MAX_SIDE / max(rw, rh).toFloat())
        val cw = ((rw * scale).toInt()) and 1.inv(); val ch = ((rh * scale).toInt()) and 1.inv()

        val mgr = getSystemService(MediaProjectionManager::class.java)
        projection = mgr.getMediaProjection(rc, data).also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(Looper.getMainLooper()))
        }
        reader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2)
        vd = projection!!.createVirtualDisplay(
            "PenLink", cw, ch, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null
        )

        wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "penlink:host").apply { acquire(8 * 60 * 60 * 1000L) }

        PenInjector.prepare()
        val code = randomCode()
        server = PenServer(Protocol.DEFAULT_PORT).apply {
            this.code = code; screenW = rw; screenH = rh
            onClientChange = { c ->
                HostState.update { it.copy(clientConnected = c, quality = quality, fps = fps) }
            }
            onFatal = { e -> HostState.update { it.copy(error = "Server gagal: ${e.message}") } }
            start()
        }
        running = true
        HostState.update { HostState.Snap(running = true, code = code, screen = "${rw}x$rh → ${cw}x$ch") }
        thread = Thread(::loop, "pen-stream").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    /**
     * Latest-frame-only: reader selalu dikuras (acquireLatestImage), frame disalin ke satu bitmap,
     * dan hanya frame terbaru yang dikodekan. Bila socket masih punya antrean (hasBufferedData),
     * kirim dilewati dan frame berikutnya yang lebih baru yang dikirim.
     */
    private fun loop() {
        val out = ByteArrayOutputStream(1 shl 19)
        var bmp: Bitmap? = null
        var fw = 0; var fh = 0; var dirty = false
        while (running) {
            val t0 = SystemClock.elapsedRealtime()
            val srv = server ?: break
            try {
                val img = reader?.acquireLatestImage()
                if (img != null) {
                    val p = img.planes[0]
                    val rowPx = p.rowStride / p.pixelStride
                    fw = img.width; fh = img.height
                    var b = bmp
                    if (b == null || b.width != rowPx || b.height != fh) {
                        b = Bitmap.createBitmap(rowPx, fh, Bitmap.Config.ARGB_8888); bmp = b
                    }
                    try { b.copyPixelsFromBuffer(p.buffer) } finally { img.close() }
                    dirty = true
                }
                val c = srv.client
                val b = bmp
                if (b != null && c != null && c.isOpen && (dirty || srv.resend) && !c.hasBufferedData()) {
                    srv.resend = false; dirty = false
                    val src = if (b.width != fw) Bitmap.createBitmap(b, 0, 0, fw, fh) else b
                    out.reset(); out.write(Protocol.MSG_FRAME.toInt())
                    src.compress(Bitmap.CompressFormat.JPEG, srv.quality, out)
                    if (src !== b) src.recycle()
                    c.send(ByteBuffer.wrap(out.toByteArray()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "loop", e)
            }
            val fps = if (srv.client != null) srv.fps else 4
            val sleep = 1000L / fps - (SystemClock.elapsedRealtime() - t0)
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    private fun randomCode() = String.format("%04d", SecureRandom().nextInt(10000))

    private fun newCode() {
        val c = randomCode(); server?.code = c
        HostState.update { it.copy(code = c) }
    }

    override fun onDestroy() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        try { server?.stop(300) } catch (_: Exception) {}
        PenInjector.releaseStuck()
        vd?.release(); reader?.close()
        try { projection?.stop() } catch (_: Exception) {}
        wl?.takeIf { it.isHeld }?.release()
        HostState.update { HostState.Snap() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HostService"
        private const val CH = "penlink_host"
        private const val MAX_SIDE = 1920f
        const val ACTION_START = "penlink.START"
        const val ACTION_STOP = "penlink.STOP"
        const val ACTION_NEWCODE = "penlink.NEWCODE"
        const val EXTRA_RC = "rc"
        const val EXTRA_DATA = "data"
    }
}
