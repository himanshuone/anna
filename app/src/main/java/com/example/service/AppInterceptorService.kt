package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.BlockerActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppInterceptorService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("AppInterceptorService", "Service created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName.isBlank() || packageName == this.packageName || isWhitelisted(packageName)) {
                return
            }

            val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm != null && !pm.isInteractive) {
                Log.d("AppInterceptorService", "Screen is off. Skipping intercept.")
                return
            }

            val km = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (km != null && km.isKeyguardLocked) {
                Log.d("AppInterceptorService", "Device is locked. Skipping intercept.")
                return
            }

            Log.d("AppInterceptorService", "Foreground app changed to: $packageName")

            serviceScope.launch {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val state = db.appDao().getAppState(packageName)
                    val currentTime = System.currentTimeMillis()

                    val needBlock = if (state == null) {
                        true
                    } else {
                        state.isLockedOut || state.lockOutUntil > currentTime || state.currentSessionEndTime < currentTime
                    }

                    if (needBlock) {
                        Log.d("AppInterceptorService", "Blocking app: $packageName")
                        val intent = Intent(applicationContext, BlockerActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("TARGET_PACKAGE", packageName)
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e("AppInterceptorService", "Error in background intercept check: ${e.message}", e)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AppInterceptorService", "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AppInterceptorService", "Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d("AppInterceptorService", "Service destroyed")
    }

    companion object {
        @Volatile
        var instance: AppInterceptorService? = null

        fun isWhitelisted(packageName: String): Boolean {
            val whitelist = setOf(
                "com.android.settings",
                "com.android.dialer",
                "com.android.calculator2",
                "com.android.deskclock",
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.huawei.deskclock",
                "com.example", // MainActivity package
                "com.aistudio.phonedetox.uqbyxv", // app package
                "com.google.android.apps.bard" // Gemini
            )

            if (packageName in whitelist) return true

            // General keyword matching for critical utilities
            val lower = packageName.lowercase()
            return lower.contains("settings") ||
                   lower.contains("dialer") ||
                   lower.contains("calculator") ||
                   lower.contains("clock") ||
                   lower.contains("phone") ||
                   lower.contains("aistudio") ||
                   lower.contains("gemini") ||
                   lower.contains("bard") ||
                   lower.contains("systemui") ||
                   lower.contains("permissioncontroller") ||
                   lower.contains("packageinstaller") ||
                   lower.contains("setupwizard") ||
                   lower.contains("biometric") ||
                   lower.contains("fingerprint") ||
                   lower.contains("faceid") ||
                   lower.contains("face") ||
                   lower.contains("facelock") ||
                   lower.contains("keyguard") ||
                   lower.contains("trustagent") ||
                   lower.contains("security") ||
                   lower.contains("lockscreen") ||
                   lower.contains("pattern") ||
                   lower.contains("pin") ||
                   lower.contains("password") ||
                   lower.contains("smartlock") ||
                   lower.contains("credentials") ||
                   lower.contains("authenticator") ||
                   lower.contains("gms") ||
                   packageName == "android" ||
                   lower.contains("launcher") // Don't block the system launcher/home screen
        }
    }
}
