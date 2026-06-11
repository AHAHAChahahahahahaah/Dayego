package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuHelper {

    fun isAvailable(): Boolean {
        return try {
            val ping = Shizuku.pingBinder()
            Log.d("ShizukuHelper", "pingBinder returned: $ping")
            ping
        } catch (e: Throwable) {
            Log.e("ShizukuHelper", "pingBinder threw exception", e)
            false
        }
    }

    fun hasPermission(context: Context): Boolean {
        return try {
            if (isAvailable()) {
                val shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                Log.d("ShizukuHelper", "checkSelfPermission returned: $shizukuGranted")
                if (shizukuGranted) {
                    return true
                }

                // Fallback check
                val systemGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    "moe.shizuku.manager.permission.API_V23"
                ) == PackageManager.PERMISSION_GRANTED
                Log.d("ShizukuHelper", "system checkSelfPermission returned: $systemGranted")
                systemGranted
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.e("ShizukuHelper", "hasPermission threw exception", e)
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            if (isAvailable()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.e("ShizukuHelper", "requestPermission threw exception", e)
        }
    }
}
