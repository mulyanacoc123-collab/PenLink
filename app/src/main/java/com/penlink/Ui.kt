package com.penlink

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object C {
    val GREEN = Color.parseColor("#2E9E5B")
    val RED = Color.parseColor("#D64545")
    val AMBER = Color.parseColor("#D9961A")
    val GREY = Color.parseColor("#8A8F98")
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.label(s: CharSequence, sp: Float = 14f, bold: Boolean = false, alpha: Float = 1f) =
    TextView(this).apply {
        text = s; textSize = sp; this.alpha = alpha
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.vbox(pad: Int = 16) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

fun Context.card(inner: View) = MaterialCardView(this).apply {
    radius = dp(16).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
    addView(inner)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(12) }
}

fun Context.button(text: String, outlined: Boolean = false, onClick: () -> Unit) =
    MaterialButton(
        this, null,
        if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle
        else com.google.android.material.R.attr.materialButtonStyle
    ).apply { this.text = text; setOnClickListener { onClick() } }

fun Context.page(title: String, subtitle: String, vararg children: View) = ScrollView(this).apply {
    fitsSystemWindows = true
    addView(vbox(20).also { col ->
        col.addView(label(title, 28f, true))
        col.addView(label(subtitle, 14f, alpha = .7f).apply { setPadding(0, 0, 0, dp(16)) })
        children.forEach { col.addView(it) }
    })
}

/** Indikator status berbentuk pil: "Connected" / "Disconnected" dst. */
class Pill(ctx: Context) : TextView(ctx) {
    init {
        setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setPadding(ctx.dp(14), ctx.dp(6), ctx.dp(14), ctx.dp(6))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = ctx.dp(12) }
    }
    fun set(color: Int, text: String) {
        this.text = text
        background = GradientDrawable().apply { cornerRadius = 100f; setColor(color) }
    }
}

/** Baris checklist dengan titik warna + deskripsi. */
class StatusRow(ctx: Context, title: String) : LinearLayout(ctx) {
    private val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(C.GREY) }
    private val desc = ctx.label("", 12f, alpha = .7f)
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, ctx.dp(6), 0, ctx.dp(6))
        addView(View(ctx).apply {
            background = bg
            layoutParams = LayoutParams(ctx.dp(12), ctx.dp(12)).apply { rightMargin = ctx.dp(12) }
        })
        addView(LinearLayout(ctx).apply {
            orientation = VERTICAL
            addView(ctx.label(title, 15f, true)); addView(desc)
        })
    }
    fun set(color: Int, text: String) { bg.setColor(color); desc.text = text }
}
