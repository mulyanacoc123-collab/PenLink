package com.penlink.host

import android.util.Log
import com.penlink.Protocol as P
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PenServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
    class Session { @Volatile var authed = false }

    @Volatile var code = "0000"
    @Volatile var quality = 70
    @Volatile var fps = 30
    @Volatile var screenW = 1080
    @Volatile var screenH = 2400
    @Volatile var client: WebSocket? = null
    @Volatile var resend = false          // minta kirim ulang frame terakhir (client baru)
    var onClientChange: ((Boolean) -> Unit)? = null
    var onFatal: ((Exception) -> Unit)? = null

    private var fails = 0
    private var lockedUntil = 0L
    private val sched = Executors.newSingleThreadScheduledExecutor()

    init {
        isReuseAddr = true
        isTcpNoDelay = true      // matikan Nagle -> latensi minimal
        connectionLostTimeout = 5
    }

    override fun onStart() { Log.i(TAG, "Server siap di port ${address.port}") }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val s = Session(); conn.setAttachment(s)
        sched.schedule({ if (!s.authed) conn.close(1008, "auth timeout") }, 5, TimeUnit.SECONDS)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val s = conn.getAttachment<Session>() ?: return
        val j = try { JSONObject(message) } catch (e: Exception) { return }
        when (j.optString("t")) {
            "auth" -> handleAuth(conn, s, j)
            "cfg" -> if (s.authed) applyCfg(j)
            "ping" -> if (s.authed) conn.send(JSONObject().put("t", "pong").put("ts", j.optLong("ts")).toString())
        }
    }

    @Synchronized
    private fun handleAuth(conn: WebSocket, s: Session, j: JSONObject) {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return reject(conn, "Terlalu banyak percobaan. Tunggu 30 detik.")
        val ok = MessageDigest.isEqual(j.optString("code").toByteArray(), code.toByteArray())
        if (!ok) {
            if (++fails >= 5) { lockedUntil = now + 30_000; fails = 0 }
            return reject(conn, "Kode pairing salah")
        }
        val cur = client
        if (cur != null && cur.isOpen) return reject(conn, "Host sedang dipakai perangkat lain")
        fails = 0; s.authed = true; applyCfg(j)
        client = conn; resend = true
        conn.send(
            JSONObject().put("t", "ok").put("w", screenW).put("h", screenH)
                .put("q", quality).put("fps", fps).toString()
        )
        onClientChange?.invoke(true)
    }

    private fun applyCfg(j: JSONObject) {
        quality = j.optInt("q", quality).coerceIn(20, 95)
        fps = j.optInt("fps", fps).coerceIn(5, 60)
    }

    private fun reject(conn: WebSocket, msg: String) {
        try { conn.send(JSONObject().put("t", "err").put("m", msg).toString()) } catch (_: Exception) {}
        conn.close(1008, msg)
    }

    override fun onMessage(conn: WebSocket, msg: ByteBuffer) {
        if (conn.getAttachment<Session>()?.authed != true) return
        if (msg.remaining() < 2 || msg.get() != P.MSG_INPUT) return
        val n = msg.get().toInt() and 0xFF
        repeat(n) {
            if (msg.remaining() < P.SAMPLE_SIZE) return
            val a = msg.get().toInt(); val tool = msg.get().toInt(); val btn = msg.get().toInt()
            val x = msg.float; val y = msg.float; val p = msg.float; val tilt = msg.float
            PenInjector.inject(a, tool, btn, x, y, p, tilt, screenW, screenH)
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        if (conn === client) {
            client = null
            PenInjector.releaseStuck()
            onClientChange?.invoke(false)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "error", ex)
        if (conn == null) onFatal?.invoke(ex)
    }

    companion object { private const val TAG = "PenServer" }
}
