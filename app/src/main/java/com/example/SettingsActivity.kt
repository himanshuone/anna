package com.example

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AppInterceptorService
import com.example.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {

    // Refresh permission states on resume
    private val isAccessibilityEnabled = mutableStateOf(false)
    private val isOverlayEnabled = mutableStateOf(false)
    private val isUsageStatsEnabled = mutableStateOf(false)
    private val isBatteryOptimizationsIgnored = mutableStateOf(false)
    private val isDefaultLauncherEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    SettingsScreen(
                        isAccessibilityEnabled = isAccessibilityEnabled.value,
                        isOverlayEnabled = isOverlayEnabled.value,
                        isUsageStatsEnabled = isUsageStatsEnabled.value,
                        isBatteryOptimizationsIgnored = isBatteryOptimizationsIgnored.value,
                        isDefaultLauncherEnabled = isDefaultLauncherEnabled.value,
                        onPermissionClick = { label -> handlePermissionClick(label) },
                        modifier = Modifier
                            .padding(innerPadding)
                            .background(Color.Black)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        isAccessibilityEnabled.value = isAccessibilityServiceEnabled(this, AppInterceptorService::class.java)
        isOverlayEnabled.value = android.provider.Settings.canDrawOverlays(this)
        isUsageStatsEnabled.value = isUsageStatsPermissionGranted(this)
        isBatteryOptimizationsIgnored.value = isBatteryOptimizationIgnored(this)
        isDefaultLauncherEnabled.value = isDefaultLauncher(this)
    }

    private fun handlePermissionClick(label: String) {
        try {
            when (label) {
                "Default Home Launcher" -> {
                    try {
                        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        startActivity(intent)
                    }
                }
                "Accessibility Interceptor" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                "System Overlay Blocker" -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                "Usage Auditing Stats" -> {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                "Battery Limit Exemption" -> {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings for $label", Toast.LENGTH_SHORT).show()
        }
    }

    // --- PERMISSION VERIFICATION HELPERS ---

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedId = ComponentName(context, serviceClass).flattenToShortString()
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(settingValue)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedId, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
}

@Composable
fun SettingsScreen(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isUsageStatsEnabled: Boolean,
    isBatteryOptimizationsIgnored: Boolean,
    isDefaultLauncherEnabled: Boolean,
    onPermissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allGranted = isAccessibilityEnabled && 
                     isOverlayEnabled && 
                     isUsageStatsEnabled && 
                     isBatteryOptimizationsIgnored && 
                     isDefaultLauncherEnabled

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        item {
            Text(
                text = "INTENT GATE SETUP",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Strict minimalist gatekeeper. Ensure all parameters below are active to shield your digital focus.",
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Column {
                PermissionOnboardingRow(
                    label = "Default Home Launcher",
                    description = "Sets Intent Gate as your default Android launcher.",
                    isEnabled = isDefaultLauncherEnabled,
                    onClick = { onPermissionClick("Default Home Launcher") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PermissionOnboardingRow(
                    label = "Accessibility Interceptor",
                    description = "Intercepts launch intents to keep distractors gated.",
                    isEnabled = isAccessibilityEnabled,
                    onClick = { onPermissionClick("Accessibility Interceptor") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PermissionOnboardingRow(
                    label = "System Overlay Blocker",
                    description = "Draws the focus challenge window over blocked apps.",
                    isEnabled = isOverlayEnabled,
                    onClick = { onPermissionClick("System Overlay Blocker") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PermissionOnboardingRow(
                    label = "Usage Auditing Stats",
                    description = "Monitors current foreground package names to enforce blocks.",
                    isEnabled = isUsageStatsEnabled,
                    onClick = { onPermissionClick("Usage Auditing Stats") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                PermissionOnboardingRow(
                    label = "Battery Limit Exemption",
                    description = "Prevents background service from being killed by OS battery savers.",
                    isEnabled = isBatteryOptimizationsIgnored,
                    onClick = { onPermissionClick("Battery Limit Exemption") }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        item {
            if (allGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White, RectangleShape)
                        .background(Color.Black)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STATUS: SECURE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Intent Gate is actively guarding your attention. Press your home button to experience the minimalist interface.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF333333), RectangleShape)
                        .background(Color.Black)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STATUS: INCOMPLETE",
                        color = Color.DarkGray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Click outstanding parameters above to authorize and complete the setup process.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingRow(
    label: String,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isEnabled) Color.White else Color(0xFF222222), RectangleShape)
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = if (isEnabled) Color.LightGray else Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (isEnabled) "[ ACTIVE ]" else "[ GRANT ]",
            color = if (isEnabled) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
