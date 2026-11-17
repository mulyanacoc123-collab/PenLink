package com.penlink

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class PenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Diperlukan agar refleksi ke IInputManager (hidden API) diizinkan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("L")
    }
}
