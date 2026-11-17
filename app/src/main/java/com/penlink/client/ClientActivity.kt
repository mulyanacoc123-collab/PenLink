package com.penlink.client

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.penlink.*

class ClientActivity : AppCompatActivity(), PenClient.Listener {
    private lateinit var pill: Pill
    private lateinit var tip: TextView
    private lateinit var status: TextView
    private lateinit var hostEt: TextInputEditText
    private lateinit var portEt: TextInputEditText
    private lateinit var codeEt: TextInputEditText
    private lateinit var qSlider: Slider
    private lateinit var fpsSlider: Slider
    private lateinit var qLabel: TextView
    private lateinit var fpsLabel: TextView
    private lateinit var penOnly: MaterialSwitch
    private lateinit var btn: MaterialButton
    private val prefs by lazy { getSharedPreferences("penlink", MODE_PRIVATE) }
    private var mode = 0

    private val tips = listOf(
        "Host & tablet harus satu jaringan Wi-Fi. Masukkan IP yang tampil di layar Host.",
        "Via PC: adb -s <HOST> forward tcp:8080 tcp:8080  dan  adb -s <TABLET> reverse tcp:8080 tcp:8080. Tablet lalu terhubung ke 127.0.0.1.",
        "Aktifkan USB Tethering di HP Host lalu sambungkan kabel (tablet harus mendukung). Isi IP gateway, biasanya 192.168.42.129."
    )

    private fun field(hint: String): Pair<TextInputLayout, TextInputEditText> {
        val il = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { this.hint = hint }
        val et = TextInputEditText(il.context)
        il.addView(et)
        il.layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        return il to et
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pill = Pill(this).apply { set(C.GREY, "Disconnected") }
        status = label("", 13f, alpha = .8f)
        tip = label("", 12f, alpha = .7f).apply { setPadding(0, dp(8), 0, dp(8)) }

        val modes = listOf("Wi-Fi", "USB / ADB", "USB Tethering")
        val ids = modes.map { View.generateViewId() }
        val toggle = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        modes.forEachIndexed { i, m ->
            toggle.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = ids[i]; text = m; textSize = 12f
            })
        }

        val (hostIl, h) = field("IP Host"); hostEt = h
        val (portIl, p) = field("Port"); portEt = p
        portEt.inputType = InputType.TYPE_CLASS_NUMBER
        portEt.setText(prefs.getInt("port", Protocol.DEFAULT_PORT).toString())
        val (codeIl, c) = field("Kode pairing (4 digit)"); codeEt = c
        codeEt.apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4)); textSize = 28f; gravity = Gravity.CENTER; letterSpacing = .4f
        }
        codeEt.doAfterTextChanged { if (it?.length == 4) connect() }   // otomatis terhubung di digit ke-4

        qLabel = label("", 13f, true); fpsLabel = label("", 13f, true)
        qSlider = Slider(this).apply { valueFrom = 30f; valueTo = 95f; stepSize = 5f; value = prefs.getFloat("q", 70f) }
        fpsSlider = Slider(this).apply { valueFrom = 10f; valueTo = 60f; stepSize = 5f; value = prefs.getFloat("fps", 30f) }
        qSlider.addOnChangeListener { _, v, _ -> qLabel.text = "Kualitas JPEG: ${v.toInt()}%" }
        fpsSlider.addOnChangeListener { _, v, _ -> fpsLabel.text = "Target FPS: ${v.toInt()}" }
        qLabel.text = "Kualitas JPEG: ${qSlider.value.toInt()}%"; fpsLabel.text = "Target FPS: ${fpsSlider.value.toInt()}"
        penOnly = MaterialSwitch(this).apply { text = "Hanya pena (tolak sentuhan jari)"; isChecked = prefs.getBoolean("penOnly", true) }
        btn = button("Hubungkan") { connect() }

        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked) { mode = ids.indexOf(id); applyMode(hostIl) }
        }
        toggle.check(ids[prefs.getInt("mode", 0).coerceIn(0, 2)])

        setContentView(
            page(
                "Client", "Tablet — tampilkan layar host & kirim pena", pill,
                card(vbox().apply { addView(toggle); addView(tip); addView(hostIl); addView(portIl) }),
                card(vbox().apply {
                    addView(label("KODE PAIRING", 12f, true, .6f)); addView(codeIl)
                    addView(qLabel); addView(qSlider); addView(fpsLabel); addView(fpsSlider); addView(penOnly)
                }),
                btn, status
            )
        )
    }

    private fun applyMode(hostIl: TextInputLayout) {
        tip.text = tips[mode]
        when (mode) {
            1 -> { hostEt.setText("127.0.0.1"); hostEt.isEnabled = false }
            2 -> { hostEt.isEnabled = true; hostEt.setText(prefs.getString("ip_tether", "192.168.42.129")) }
            else -> { hostEt.isEnabled = true; hostEt.setText(prefs.getString("ip_wifi", "")) }
        }
    }

    private fun connect() {
        if (PenClient.state == PenClient.State.CONNECTING) return
        val host = hostEt.text.toString().trim()
        val port = portEt.text.toString().toIntOrNull() ?: Protocol.DEFAULT_PORT
        val code = codeEt.text.toString()
        if (host.isEmpty()) { status.text = "Isi alamat host"; return }
        if (code.length != 4) { status.text = "Kode pairing harus 4 digit"; return }
        prefs.edit().putInt("mode", mode).putInt("port", port).putFloat("q", qSlider.value)
            .putFloat("fps", fpsSlider.value).putBoolean("penOnly", penOnly.isChecked)
            .apply { when (mode) { 0 -> putString("ip_wifi", host); 2 -> putString("ip_tether", host) } }.apply()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(codeEt.windowToken, 0)
        PenClient.listener = this
        PenClient.connect(host, port, code, qSlider.value.toInt(), fpsSlider.value.toInt())
    }

    override fun onResume() {
        super.onResume()
        PenClient.listener = this
        if (PenClient.state != PenClient.State.CONNECTING) { codeEt.setText(""); pill.set(C.GREY, "Disconnected"); btn.isEnabled = true }
    }

    override fun onPause() { if (PenClient.listener === this) PenClient.listener = null; super.onPause() }

    override fun onState(s: PenClient.State, msg: String) = runOnUiThread {
        status.text = msg
        btn.isEnabled = s != PenClient.State.CONNECTING
        when (s) {
            PenClient.State.CONNECTING -> pill.set(C.AMBER, "Connecting…")
            PenClient.State.DISCONNECTED -> { pill.set(C.RED, "Disconnected"); codeEt.setText("") }
            PenClient.State.CONNECTED -> {
                pill.set(C.GREEN, "Connected")
                startActivity(Intent(this, DrawActivity::class.java).putExtra("penOnly", penOnly.isChecked))
            }
        }
    }
}
