package com.penlink.host

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.penlink.*
import rikka.shizuku.Shizuku
import java.net.Inet4Address
import java.net.NetworkInterface
import android.content.pm.PackageManager

class HostActivity : AppCompatActivity() {
    private lateinit var pill: Pill
    private lateinit var codeTv: TextView
    private lateinit var ipsTv: TextView
    private lateinit var infoTv: TextView
    private lateinit var rowShizuku: StatusRow
    private lateinit var rowCapture: StatusRow
    private lateinit var rowClient: StatusRow
    private lateinit var btnShizuku: MaterialButton
    private lateinit var btnStart: MaterialButton

    private val capLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == RESULT_OK && d != null) {
            startForegroundService(
                Intent(this, HostService::class.java).setAction(HostService.ACTION_START)
                    .putExtra(HostService.EXTRA_RC, r.resultCode).putExtra(HostService.EXTRA_DATA, d)
            )
        } else toast("Izin perekaman layar ditolak")
    }
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { render() } }
    private val binderL = Shizuku.OnBinderReceivedListener { runOnUiThread { render() } }
    private val deadL = Shizuku.OnBinderDeadListener { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pill = Pill(this)
        codeTv = label("- - - -", 44f, true).apply { gravity = android.view.Gravity.CENTER; letterSpacing = .15f }
        ipsTv = label("", 13f, alpha = .8f)
        infoTv = label("", 12f, alpha = .7f)
        rowShizuku = StatusRow(this, "1. Shizuku (input pena)")
        rowCapture = StatusRow(this, "2. Perekaman layar (MediaProjection)")
        rowClient = StatusRow(this, "3. Client")
        btnShizuku = button("Buka Shizuku", outlined = true) { onShizukuClick() }
        btnStart = button("Mulai Host") { onStartClick() }

        setContentView(
            page(
                "Host", "HP utama — menerima pena & membagikan layar", pill,
                card(vbox().apply {
                    addView(label("KODE PAIRING", 12f, true, .6f))
                    addView(codeTv)
                    addView(label("Alamat host (port ${Protocol.DEFAULT_PORT})", 12f, true, .6f).apply { setPadding(0, dp(12), 0, 0) })
                    addView(ipsTv); addView(infoTv)
                }),
                card(vbox().apply {
                    addView(rowShizuku); addView(rowCapture); addView(rowClient)
                    addView(btnShizuku)
                }),
                btnStart,
                button("Ganti kode pairing", outlined = true) {
                    startService(Intent(this@HostActivity, HostService::class.java).setAction(HostService.ACTION_NEWCODE))
                }
            )
        )
        Shizuku.addBinderReceivedListenerSticky(binderL)
        Shizuku.addBinderDeadListener(deadL)
        Shizuku.addRequestPermissionResultListener(permL)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        HostState.listener = { runOnUiThread { render() } }
        render()
    }

    override fun onPause() { HostState.listener = null; super.onPause() }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderL)
        Shizuku.removeBinderDeadListener(deadL)
        Shizuku.removeRequestPermissionResultListener(permL)
        super.onDestroy()
    }

    /** 0 = tidak berjalan, 1 = belum diberi izin, 2 = siap */
    private fun shizukuState(): Int = try {
        when {
            !Shizuku.pingBinder() -> 0
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> 2
            else -> 1
        }
    } catch (e: Throwable) { 0 }

    private fun render() {
        val s = HostState.snap
        val sz = shizukuState()
        rowShizuku.set(
            when (sz) { 2 -> C.GREEN; 1 -> C.AMBER; else -> C.RED },
            when (sz) {
                2 -> "Aktif & izin diberikan"
                1 -> "Berjalan, tetapi izin belum diberikan"
                else -> "Tidak berjalan. Buka Shizuku → Start (Wireless debugging)."
            }
        )
        btnShizuku.visibility = if (sz == 2) android.view.View.GONE else android.view.View.VISIBLE
        btnShizuku.text = if (sz == 1) "Beri izin Shizuku" else "Buka aplikasi Shizuku"

        rowCapture.set(
            if (s.running) C.GREEN else C.GREY,
            if (s.running) "Aktif (${s.screen})" else "Izin diminta saat menekan Mulai Host"
        )
        rowClient.set(
            if (s.clientConnected) C.GREEN else C.AMBER,
            if (s.clientConnected) "Terhubung • JPEG ${s.quality}% • ${s.fps} FPS" else "Menunggu client memasukkan kode"
        )
        when {
            s.error != null -> pill.set(C.RED, "Error")
            s.clientConnected -> pill.set(C.GREEN, "Connected")
            s.running -> pill.set(C.AMBER, "Menunggu client")
            else -> pill.set(C.GREY, "Disconnected")
        }
        infoTv.text = s.error ?: ""
        codeTv.text = s.code.toCharArray().joinToString(" ")
        ipsTv.text = ipList().ifEmpty { "Tidak ada jaringan aktif. Untuk USB/ADB gunakan 127.0.0.1." }
        btnStart.text = if (s.running) "Hentikan Host" else "Mulai Host"
        btnStart.isEnabled = s.running || sz == 2
    }

    private fun onShizukuClick() {
        if (shizukuState() == 1) {
            try { Shizuku.requestPermission(100) } catch (e: Throwable) { toast("Gagal meminta izin: ${e.message}") }
        } else {
            val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (i != null) startActivity(i) else toast("Aplikasi Shizuku belum terpasang")
        }
    }

    private fun onStartClick() {
        if (HostState.snap.running) {
            startService(Intent(this, HostService::class.java).setAction(HostService.ACTION_STOP))
        } else {
            capLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
    }

    private fun ipList(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${ni.name}: ${it.hostAddress}" } }
            .joinToString("\n")
    } catch (e: Exception) { "" }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
