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
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    SettingsScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        onPermissionClick = { label -> handlePermissionClick(label) }
                    )
                }
            }
        }
    }

    private fun handlePermissionClick(label: String) {
        try {
            when (label) {
                "Default Home Launcher" -> {
                    try {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
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
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onPermissionClick: (String) -> Unit
) {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayEnabled by remember { mutableStateOf(false) }
    var isUsageStatsEnabled by remember { mutableStateOf(false) }
    var isBatteryOptimizationsIgnored by remember { mutableStateOf(false) }
    var isDefaultLauncherEnabled by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        try {
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, AppInterceptorService::class.java)
            isOverlayEnabled = Settings.canDrawOverlays(context)
            isUsageStatsEnabled = isUsageStatsPermissionGranted(context)
            isBatteryOptimizationsIgnored = isBatteryOptimizationIgnored(context)
            isDefaultLauncherEnabled = isDefaultLauncher(context)
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Error refreshing permissions: ${e.message}", e)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allGranted = isAccessibilityEnabled && 
                     isOverlayEnabled && 
                     isUsageStatsEnabled && 
                     isBatteryOptimizationsIgnored && 
                     isDefaultLauncherEnabled

    LazyColumn(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PHONE DETOX SETUP",
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            PermissionOnboardingRow(
                label = "Default Home Launcher",
                description = "Sets Phone Detox as your default Android launcher.",
                isEnabled = isDefaultLauncherEnabled,
                onClick = { onPermissionClick("Default Home Launcher") }
            )
        }

        item {
            PermissionOnboardingRow(
                label = "Accessibility Interceptor",
                description = "Intercepts launch intents to keep distractors gated.",
                isEnabled = isAccessibilityEnabled,
                onClick = { onPermissionClick("Accessibility Interceptor") }
            )
        }

        item {
            PermissionOnboardingRow(
                label = "System Overlay Blocker",
                description = "Draws the focus challenge window over blocked apps.",
                isEnabled = isOverlayEnabled,
                onClick = { onPermissionClick("System Overlay Blocker") }
            )
        }

        item {
            PermissionOnboardingRow(
                label = "Usage Auditing Stats",
                description = "Monitors current foreground package names to enforce blocks.",
                isEnabled = isUsageStatsEnabled,
                onClick = { onPermissionClick("Usage Auditing Stats") }
            )
        }

        item {
            PermissionOnboardingRow(
                label = "Battery Limit Exemption",
                description = "Prevents background service from being killed by OS battery savers.",
                isEnabled = isBatteryOptimizationsIgnored,
                onClick = { onPermissionClick("Battery Limit Exemption") }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
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
                        text = "Phone Detox is actively guarding your attention. Press your home button to experience the minimalist interface.",
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


