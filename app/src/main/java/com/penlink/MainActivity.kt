package com.penlink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.penlink.client.ClientActivity
import com.penlink.host.HostActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            page(
                "PenLink", "Pen display nirkabel / USB. Pilih peran perangkat ini.",
                role("Host — HP utama", "Menerima input pena (Shizuku) dan mengirim layar.", HostActivity::class.java),
                role("Client — Tablet", "Menampilkan layar host dan mengirim goresan pena + tekanan.", ClientActivity::class.java)
            )
        )
    }

    private fun role(title: String, desc: String, target: Class<*>) = card(vbox().apply {
        addView(label(title, 18f, true))
        addView(label(desc, 13f, alpha = .7f).apply { setPadding(0, dp(4), 0, dp(12)) })
        addView(button("Pilih") { startActivity(Intent(this@MainActivity as Context, target)) })
    })
}
