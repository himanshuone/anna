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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.service.AppInterceptorService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

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
                    LauncherDashboard(
                        modifier = Modifier
                            .padding(innerPadding)
                            .background(Color.Black)
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        // Prevent default back behavior on home screen
    }
}

data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val className: String
)

@Composable
fun LauncherDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.appDao()

    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    // Ticking Clock State
    var timeString by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf("") }

    // App List State
    var allAppsList by remember { mutableStateOf<List<LauncherAppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // Quick verification state for setup requirement
    var isAllPermissionsActive by remember { mutableStateOf(true) }

    fun refreshPermissions() {
        val acc = isAccessibilityServiceEnabled(context, AppInterceptorService::class.java)
        val overlay = Settings.canDrawOverlays(context)
        val usage = isUsageStatsPermissionGranted(context)
        val battery = isBatteryOptimizationIgnored(context)
        val defaultL = isDefaultLauncher(context)
        isAllPermissionsActive = acc && overlay && usage && battery && defaultL
    }

    // Refresh permissions and clock (optimized delay of 15 seconds to save battery)
    LaunchedEffect(Unit) {
        refreshPermissions()
        while (true) {
            val cal = Calendar.getInstance()
            timeString = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.US)
            dateString = sdf.format(cal.time)
            delay(15000)
        }
    }

    // Fetch launchable apps drawer on background thread immediately on start
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allAppsList = getInstalledLaunchableApps(context)
        }
    }

    // Force refresh when screen gets focused
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

    val filteredAppsList = remember(searchQuery, allAppsList) {
        if (searchQuery.isBlank()) {
            allAppsList
        } else {
            allAppsList.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Launcher root layout with native smooth swiping HorizontalPager
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    // PAGE 0: ULTRA MINIMALIST HOME SCREEN
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Giant Clock
                        Text(
                            text = timeString.ifBlank { "00:00" },
                            color = Color.White,
                            fontSize = 64.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Minimalist date
                        Text(
                            text = dateString.ifBlank { "Monday, Void" }.uppercase(),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )

                        if (!isAllPermissionsActive) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "⚠️ SETUP REQUIRED (CLICK TO ACTIVATE)",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        val intent = Intent(context, SettingsActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                    .border(1.dp, Color.Red, RectangleShape)
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(60.dp))

                        // Launcher quick actions: PHONE & CAMERA
                        Column(
                            modifier = Modifier.width(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PHONE",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { launchPhone(context) }
                                    .border(1.dp, Color(0xFF333333), RectangleShape)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "CAMERA",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { launchCamera(context) }
                                    .border(1.dp, Color(0xFF333333), RectangleShape)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(80.dp))

                        Text(
                            text = "← Swipe left to All Apps",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                1 -> {
                    // PAGE 1: TEXT-ONLY ALL APPS DRAWER WITH INSTANT SEARCH
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "ALL APPLICATIONS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Modern text-only minimalist search field
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color.White, RectangleShape)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "SEARCH...",
                                                    color = Color.DarkGray,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            innerTextField()
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            Text(
                                                text = "CLEAR",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .clickable { searchQuery = "" }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Scrolling apps list
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (filteredAppsList.isEmpty()) {
                                item {
                                    Text(
                                        text = "No apps found.",
                                        color = Color.DarkGray,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            } else {
                                items(filteredAppsList, key = { app -> "${app.packageName}/${app.className}" }) { app ->
                                    Text(
                                        text = app.label.uppercase(),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onAppClick(context, app, dao, coroutineScope)
                                            }
                                            .padding(vertical = 14.dp, horizontal = 4.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFF151515))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // MINIMAL BOTTOM PAGE SLIDING INDICATORS & TABS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .border(BorderStroke(1.dp, Color(0xFF1F1F1F)), RectangleShape)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HOME",
                color = if (pagerState.currentPage == 0) Color.White else Color.DarkGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    }
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            )
            Text(
                text = "DRAWER",
                color = if (pagerState.currentPage == 1) Color.White else Color.DarkGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    }
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            )
        }
    }
}

// --- HELPER UTILITIES ---

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
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

fun isUsageStatsPermissionGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.noteOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

fun getInstalledLaunchableApps(context: Context): List<LauncherAppInfo> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
    return resolveInfos.mapNotNull { info ->
        val packageName = info.activityInfo.packageName
        val appLabel = info.loadLabel(pm).toString()
        LauncherAppInfo(
            packageName = packageName,
            label = appLabel,
            className = info.activityInfo.name
        )
    }.sortedBy { it.label.lowercase() }
}

fun launchPhone(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot launch Phone app", Toast.LENGTH_SHORT).show()
    }
}

fun launchCamera(context: Context) {
    try {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent2 = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent2)
        } catch (e2: Exception) {
            Toast.makeText(context, "Cannot launch Camera app", Toast.LENGTH_SHORT).show()
        }
    }
}

fun launchApp(context: Context, app: LauncherAppInfo) {
    if (app.packageName == context.packageName) {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return
    }
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
    if (launchIntent != null) {
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "Unable to launch ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

fun onAppClick(context: Context, app: LauncherAppInfo, dao: com.example.data.AppDao, coroutineScope: kotlinx.coroutines.CoroutineScope) {
    if (app.packageName == context.packageName) {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return
    }
    if (AppInterceptorService.isWhitelisted(app.packageName)) {
        launchApp(context, app)
        return
    }
    coroutineScope.launch {
        val state = dao.getAppState(app.packageName)
        val currentTime = System.currentTimeMillis()
        val needBlock = if (state == null) {
            true
        } else {
            state.isLockedOut || state.lockOutUntil > currentTime || state.currentSessionEndTime < currentTime
        }

        if (needBlock) {
            val intent = Intent(context, BlockerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("TARGET_PACKAGE", app.packageName)
            }
            context.startActivity(intent)
        } else {
            launchApp(context, app)
        }
    }
}
