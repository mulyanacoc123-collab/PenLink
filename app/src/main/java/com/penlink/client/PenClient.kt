package com.penlink.client

import com.penlink.Protocol
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/** Koneksi tunggal client -> host, dipakai bersama oleh ClientActivity & DrawActivity. */
object PenClient {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    interface Listener {
        fun onState(s: State, msg: String) {}
        fun onFrame(jpeg: ByteArray) {}
        fun onRtt(ms: Float) {}
    }

    @Volatile var listener: Listener? = null
    @Volatile var state = State.DISCONNECTED; private set
    @Volatile var lastFrame: ByteArray? = null; private set
    @Volatile private var ws: WebSocketClient? = null

    private fun setState(s: State, msg: String) { state = s; listener?.onState(s, msg) }

    fun connect(host: String, port: Int, code: String, quality: Int, fps: Int) {
        disconnect(); lastFrame = null
        setState(State.CONNECTING, "Menghubungkan ke $host:$port…")
        val c = try {
            object : WebSocketClient(URI("ws://$host:$port")) {
                override fun onOpen(h: ServerHandshake?) {
                    send(JSONObject().put("t", "auth").put("code", code).put("q", quality).put("fps", fps).toString())
                }
                override fun onMessage(message: String) {
                    if (ws !== this) return
                    val j = try { JSONObject(message) } catch (e: Exception) { return }
                    when (j.optString("t")) {
                        "ok" -> { setState(State.CONNECTED, "Terhubung"); startPing(this) }
                        "err" -> { setState(State.DISCONNECTED, j.optString("m", "Ditolak host")); close() }
                        "pong" -> listener?.onRtt((System.nanoTime() - j.optLong("ts")) / 1e6f)
                    }
                }
                override fun onMessage(bytes: ByteBuffer) {
                    if (ws !== this || bytes.remaining() < 2 || bytes.get() != Protocol.MSG_FRAME) return
                    val a = ByteArray(bytes.remaining()); bytes.get(a)
                    lastFrame = a; listener?.onFrame(a)
                }
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    if (ws === this && state != State.DISCONNECTED) setState(State.DISCONNECTED, "Koneksi terputus")
                }
                override fun onError(ex: Exception?) {
                    if (ws === this && state == State.CONNECTING) setState(State.DISCONNECTED, "Gagal: ${ex?.message}")
                }
            }
        } catch (e: Exception) {
            setState(State.DISCONNECTED, "Alamat tidak valid"); return
        }
        c.isTcpNoDelay = true
        c.connectionLostTimeout = 5
        ws = c
        Thread {
            val ok = try { c.connectBlocking(5, TimeUnit.SECONDS) } catch (e: Exception) { false }
            if (!ok && ws === c && state == State.CONNECTING) {
                setState(State.DISCONNECTED, "Tidak bisa menjangkau host")
                try { c.close() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun startPing(c: WebSocketClient) {
        Thread {
            while (ws === c && c.isOpen) {
                try { c.send(JSONObject().put("t", "ping").put("ts", System.nanoTime()).toString()) } catch (e: Exception) {}
                try { Thread.sleep(1000) } catch (e: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    fun sendInput(bb: ByteBuffer) {
        val c = ws
        if (c != null && state == State.CONNECTED && c.isOpen) c.send(bb)
    }

    fun sendConfig(quality: Int, fps: Int) {
        ws?.takeIf { it.isOpen }?.send(JSONObject().put("t", "cfg").put("q", quality).put("fps", fps).toString())
    }

    fun disconnect() {
        val c = ws; ws = null; state = State.DISCONNECTED
        try { c?.close() } catch (_: Exception) {}
    }
}
