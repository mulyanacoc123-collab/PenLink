package com.penlink.host

/** State bersama Service -> Activity (tanpa binding). */
object HostState {
    data class Snap(
        val running: Boolean = false,
        val code: String = "----",
        val clientConnected: Boolean = false,
        val quality: Int = 0,
        val fps: Int = 0,
        val screen: String = "",
        val error: String? = null
    )

    @Volatile var snap = Snap(); private set
    @Volatile var listener: ((Snap) -> Unit)? = null

    @Synchronized fun update(f: (Snap) -> Snap) { snap = f(snap); listener?.invoke(snap) }
}
