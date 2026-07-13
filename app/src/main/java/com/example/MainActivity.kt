package com.example

import android.util.Log
import android.app.Activity
import android.app.KeyguardManager
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
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
import com.example.data.AppState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.service.AppInterceptorService
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    companion object {
        val homePressTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    LauncherDashboard(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressTrigger.tryEmit(Unit)
    }

    override fun onBackPressed() {
        // Return to home screen page if back button is pressed on app drawer
        homePressTrigger.tryEmit(Unit)
    }
}

data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val className: String
)

sealed class DrawerItem {
    data class AppItem(val app: LauncherAppInfo) : DrawerItem()
    data class FolderItem(val name: String, val appCount: Int) : DrawerItem()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.appDao()

    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    // Live Flow for global user stats and daily streaks
    val userStatsState by db.userStatsDao().getUserStatsFlow().collectAsState(initial = null)
    var appStatesList by remember { mutableStateOf<List<AppState>>(emptyList()) }

    // Ticking Clock State
    var timeString by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf("") }

    // App List State
    var allAppsList by remember { mutableStateOf<List<LauncherAppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // App Long Click Options State
    var appOptionsSelected by remember { mutableStateOf<LauncherAppInfo?>(null) }

    // Quick verification state for setup requirement
    var isAllPermissionsActive by remember { mutableStateOf(true) }

    // Virtual lock state
    var isScreenLocked by remember { mutableStateOf(false) }

    // New States for Folder/Hiding System
    var showHiddenAppsDialog by remember { mutableStateOf(false) }
    var folderToOpen by remember { mutableStateOf<String?>(null) }
    var showFolderSelectionForApp by remember { mutableStateOf<LauncherAppInfo?>(null) }
    var showCreateFolderDialogForApp by remember { mutableStateOf<LauncherAppInfo?>(null) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Keyboard focus dismissal and auto-show on page change
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0) {
            focusManager.clearFocus()
        } else if (pagerState.currentPage == 1) {
            delay(150)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error requesting focus: ${e.message}", e)
            }
        }
    }

    fun reloadAppStates() {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appStatesList = dao.getAllAppStates()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error reloading app states: ${e.message}", e)
            }
        }
    }

    fun refreshPermissions() {
        try {
            val acc = isAccessibilityServiceEnabled(context, AppInterceptorService::class.java)
            val overlay = Settings.canDrawOverlays(context)
            val usage = isUsageStatsPermissionGranted(context)
            val battery = isBatteryOptimizationIgnored(context)
            val defaultL = isDefaultLauncher(context)
            isAllPermissionsActive = acc && overlay && usage && battery && defaultL
        } catch (e: Exception) {
            Log.e("MainActivity", "Error refreshing permissions: ${e.message}", e)
            isAllPermissionsActive = false
        }
    }

    // Refresh permissions and clock (responsive 1 second updates)
    LaunchedEffect(Unit) {
        try {
            refreshPermissions()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in initial permission refresh: ${e.message}", e)
        }
        while (true) {
            val cal = Calendar.getInstance()
            timeString = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.US)
            dateString = sdf.format(cal.time)
            delay(1000)
        }
    }

    // Fetch launchable apps drawer on background thread immediately on start, along with app usage stats
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                allAppsList = getInstalledLaunchableApps(context)
                appStatesList = dao.getAllAppStates()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading installed apps/stats: ${e.message}", e)
            allAppsList = emptyList()
        }
    }

    // Reset to Page 0 (HOME) when Home key is clicked/slid or back button pressed
    LaunchedEffect(Unit) {
        MainActivity.homePressTrigger.collect {
            try {
                pagerState.animateScrollToPage(0)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error animating scroll to page 0: ${e.message}", e)
            }
        }
    }

    // Force refresh when screen gets focused
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                try {
                    refreshPermissions()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in ON_RESUME refreshPermissions: ${e.message}", e)
                }
                searchQuery = ""
                coroutineScope.launch {
                    try {
                        pagerState.scrollToPage(0)
                        withContext(Dispatchers.IO) {
                            appStatesList = dao.getAllAppStates()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error in ON_RESUME data load: ${e.message}", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val appStateMap = remember(appStatesList) {
        appStatesList.associateBy { it.packageName }
    }

    val filteredAppsList = remember(searchQuery, allAppsList, appStateMap) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            allAppsList.filter { app ->
                val state = appStateMap[app.packageName]
                state?.isHidden != true && state?.folderName == null
            }
        } else {
            allAppsList.filter { app ->
                val state = appStateMap[app.packageName]
                state?.isHidden != true && app.label.contains(query, ignoreCase = true)
            }
        }
    }

    val foldersList = remember(appStatesList) {
        appStatesList.mapNotNull { it.folderName }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val folderAppsMap = remember(foldersList, allAppsList, appStateMap) {
        foldersList.associateWith { folder ->
            allAppsList.filter { app ->
                val state = appStateMap[app.packageName]
                state?.folderName == folder && state?.isHidden != true
            }
        }.filterValues { it.isNotEmpty() }
    }

    val drawerItems = remember(filteredAppsList, folderAppsMap) {
        val items = mutableListOf<DrawerItem>()
        filteredAppsList.forEach { app ->
            items.add(DrawerItem.AppItem(app))
        }
        folderAppsMap.forEach { (name, apps) ->
            items.add(DrawerItem.FolderItem(name, apps.size))
        }
        items.sortedBy { item ->
            when (item) {
                is DrawerItem.AppItem -> item.app.label.lowercase()
                is DrawerItem.FolderItem -> item.name.lowercase()
            }
        }
    }

    // Select top 4 most used launchable apps
    val mostUsedApps = remember(allAppsList, appStatesList, appStateMap) {
        val usageMap = appStatesList.associate { it.packageName to it.totalAccessCount }
        allAppsList
            .filter { app ->
                val count = usageMap[app.packageName] ?: 0
                count > 0 && appStateMap[app.packageName]?.isHidden != true
            }
            .sortedByDescending { usageMap[it.packageName] ?: 0 }
            .take(4)
    }

    // Launcher root layout with virtual lock overlay support
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isScreenLocked) {
            // OLED SLEEP / VIRTUAL LOCK OVERLAY
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                isScreenLocked = false
                                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                if (km != null) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.findActivity()?.let { activity ->
                                            km.requestDismissKeyguard(activity, null)
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Giant Clock
                Text(
                    text = timeString.ifBlank { "00:00" },
                    color = Color.White,
                    fontSize = 68.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "[ DOUBLE TAP TO UNLOCK ]",
                    color = Color(0xFF222222),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // NORMAL ACTIVE LAUNCHER INTERFACE
            Column(
                modifier = Modifier.fillMaxSize()
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
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                isScreenLocked = true
                                                // Lock the physical device if Accessibility Service is enabled
                                                val service = AppInterceptorService.instance
                                                if (service != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                                                }
                                            }
                                        )
                                    }
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

                                // VISUAL STATS CARD
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                val totalGatedOpens = appStatesList.sumOf { it.openCount }
                                val totalDeferrals = appStatesList.sumOf { it.deferralCount }
                                val deferralPercent = if (totalGatedOpens + totalDeferrals > 0) {
                                    (totalDeferrals * 100) / (totalGatedOpens + totalDeferrals)
                                } else {
                                    100
                                }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                        .background(Color(0xFF070707))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "MINDFUL METRICS",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Gated Opens
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "OPENS",
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "$totalGatedOpens",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        // Deferral %
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "DEFERRAL",
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "$deferralPercent%",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        // Streak
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "STREAK",
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${userStatsState?.dailyStreak ?: 0} D",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Custom visual progress bar representing deferral rate
                                    val progressFraction = if (totalGatedOpens + totalDeferrals > 0) {
                                        totalDeferrals.toFloat() / (totalGatedOpens + totalDeferrals).toFloat()
                                    } else {
                                        1.0f
                                    }
                                    LinearProgressIndicator(
                                        progress = { progressFraction.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(2.dp),
                                        color = Color.White,
                                        trackColor = Color(0xFF1B1B1B)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

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
                                // Modern minimalist search bar with rounded corners and search icon
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    placeholder = {
                                        Text(
                                            text = "SEARCH APPS...",
                                            color = Color.Gray,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = Color.Gray
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear search",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF111111),
                                        unfocusedContainerColor = Color(0xFF0A0A0A),
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color(0xFF333333),
                                        cursorColor = Color.White
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Scrolling apps list
                                LazyColumn(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (searchQuery.isBlank() && mostUsedApps.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "MOST FREQUENTLY USED",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.5.sp,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                             )
                                        }
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                mostUsedApps.forEach { app ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                                            .clickable { onAppClick(context, app, dao, coroutineScope) }
                                                            .padding(8.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                text = app.label.take(1).uppercase(),
                                                                color = Color.White,
                                                                fontSize = 16.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = app.label.uppercase(),
                                                                color = Color.LightGray,
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                                repeat(4 - mostUsedApps.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp)
                                                    .height(1.dp)
                                                    .background(Color(0xFF222222))
                                            )
                                        }
                                    }

                                    if (searchQuery.isNotBlank()) {
                                        // Searching: flat filtered apps list
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
                                            items(filteredAppsList, key = { app -> "search_${app.packageName}/${app.className}" }) { app ->
                                                Text(
                                                    text = app.label.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .combinedClickable(
                                                            onClick = {
                                                                onAppClick(context, app, dao, coroutineScope)
                                                            },
                                                            onLongClick = {
                                                                appOptionsSelected = app
                                                            }
                                                        )
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
                                    } else {
                                        // Not searching: mixed folder and individual apps list
                                        if (drawerItems.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "No applications available.",
                                                    color = Color.DarkGray,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(vertical = 16.dp)
                                                )
                                            }
                                        } else {
                                            items(drawerItems, key = { item ->
                                                when (item) {
                                                    is DrawerItem.AppItem -> "app_${item.app.packageName}/${item.app.className}"
                                                    is DrawerItem.FolderItem -> "folder_${item.name}"
                                                }
                                            }) { item ->
                                                when (item) {
                                                    is DrawerItem.AppItem -> {
                                                        val app = item.app
                                                        Text(
                                                            text = app.label.uppercase(),
                                                            color = Color.White,
                                                            fontSize = 15.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .combinedClickable(
                                                                    onClick = {
                                                                        onAppClick(context, app, dao, coroutineScope)
                                                                    },
                                                                    onLongClick = {
                                                                        appOptionsSelected = app
                                                                    }
                                                                )
                                                                .padding(vertical = 14.dp, horizontal = 4.dp)
                                                        )
                                                    }
                                                    is DrawerItem.FolderItem -> {
                                                        val folderName = item.name
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    folderToOpen = folderName
                                                                }
                                                                .padding(vertical = 14.dp, horizontal = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "📁 ${folderName.uppercase()}",
                                                                color = Color.White,
                                                                fontSize = 15.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Text(
                                                                text = "(${item.appCount} APPS)",
                                                                color = Color.Gray,
                                                                fontSize = 12.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                    }
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(Color(0xFF151515))
                                                )
                                            }
                                        }

                                        // MANAGE HIDDEN APPS BUTTON AT THE BOTTOM OF LIST
                                        item {
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Text(
                                                text = "[ MANAGE HIDDEN APPLICATIONS ]",
                                                color = Color.DarkGray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showHiddenAppsDialog = true }
                                                    .padding(vertical = 16.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (appOptionsSelected != null) {
            val app = appOptionsSelected!!
            val state = appStateMap[app.packageName]
            val isSelectedAppHidden = state?.isHidden == true
            val currentFolderOfSelectedApp = state?.folderName

            AlertDialog(
                onDismissRequest = { appOptionsSelected = null },
                shape = RectangleShape,
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color.White, RectangleShape),
                title = {
                    Text(
                        text = app.label.uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "PACKAGE: ${app.packageName}",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Option 1: App Settings (App Info)
                        Text(
                            text = "APP INFO / SETTINGS",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                                    }
                                    appOptionsSelected = null
                                }
                                .border(1.dp, Color(0xFF333333), RectangleShape)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 2: Notification Settings
                        Text(
                            text = "NOTIFICATION SETTINGS",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${app.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, "Cannot open notification settings", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    appOptionsSelected = null
                                }
                                .border(1.dp, Color(0xFF333333), RectangleShape)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 3: Move to Folder / Change Folder
                        Text(
                            text = if (currentFolderOfSelectedApp != null) "CHANGE/REMOVE FOLDER (CURRENT: ${currentFolderOfSelectedApp.uppercase()})" else "MOVE TO FOLDER",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFolderSelectionForApp = app
                                    appOptionsSelected = null
                                }
                                .border(1.dp, Color(0xFF333333), RectangleShape)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 4: Hide/Unhide Application
                        Text(
                            text = if (isSelectedAppHidden) "UNHIDE APPLICATION" else "HIDE APPLICATION",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        try {
                                            val existing = dao.getAppState(app.packageName)
                                            val newState = (existing ?: AppState(packageName = app.packageName)).copy(
                                                isHidden = !isSelectedAppHidden
                                            )
                                            dao.insertAppState(newState)
                                            reloadAppStates()
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Error toggling hide: ${e.message}", e)
                                        }
                                    }
                                    appOptionsSelected = null
                                }
                                .border(1.dp, Color(0xFF333333), RectangleShape)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 5: Uninstall App
                        Text(
                            text = "UNINSTALL APPLICATION",
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_DELETE).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot request uninstallation", Toast.LENGTH_SHORT).show()
                                    }
                                    appOptionsSelected = null
                                }
                                .border(1.dp, Color.Red, RectangleShape)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Text(
                        text = "CLOSE",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { appOptionsSelected = null }
                            .padding(8.dp)
                    )
                }
            )
        }

        // SUPPLEMENTARY DIALOG 1: Folder Selection Dialog
        if (showFolderSelectionForApp != null) {
            val app = showFolderSelectionForApp!!
            val state = appStateMap[app.packageName]
            AlertDialog(
                onDismissRequest = { showFolderSelectionForApp = null },
                shape = RectangleShape,
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color.White, RectangleShape),
                title = {
                    Text(
                        text = "MOVE ${app.label.uppercase()} TO",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Create New Folder Button
                        Text(
                            text = "[ + CREATE NEW FOLDER ]",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCreateFolderDialogForApp = app
                                    showFolderSelectionForApp = null
                                }
                                .border(1.dp, Color(0xFF444444), RectangleShape)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )

                        // Remove from existing folder (if any)
                        if (state?.folderName != null) {
                            Text(
                                text = "[ REMOVE FROM FOLDER ]",
                                color = Color.Red,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            try {
                                                val existing = dao.getAppState(app.packageName)
                                                if (existing != null) {
                                                    dao.insertAppState(existing.copy(folderName = null))
                                                    reloadAppStates()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Error removing folder: ${e.message}", e)
                                            }
                                        }
                                        showFolderSelectionForApp = null
                                    }
                                    .border(1.dp, Color.Red, RectangleShape)
                                    .padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // List of existing folder names
                        val uniqueFolders = folderAppsMap.keys.sorted()
                        if (uniqueFolders.isNotEmpty()) {
                            Text(
                                text = "EXISTING FOLDERS:",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                items(uniqueFolders) { fName ->
                                    Text(
                                        text = fName.uppercase(),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    try {
                                                        val existing = dao.getAppState(app.packageName)
                                                        val newState = (existing ?: AppState(packageName = app.packageName)).copy(
                                                            folderName = fName
                                                        )
                                                        dao.insertAppState(newState)
                                                        reloadAppStates()
                                                    } catch (e: Exception) {
                                                        Log.e("MainActivity", "Error moving folder: ${e.message}", e)
                                                    }
                                                }
                                                showFolderSelectionForApp = null
                                            }
                                            .border(1.dp, Color(0xFF222222), RectangleShape)
                                            .padding(12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Text(
                        text = "CANCEL",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { showFolderSelectionForApp = null }
                            .padding(8.dp)
                    )
                }
            )
        }

        // SUPPLEMENTARY DIALOG 2: Create Folder Dialog
        if (showCreateFolderDialogForApp != null) {
            val app = showCreateFolderDialogForApp!!
            var newFolderNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateFolderDialogForApp = null },
                shape = RectangleShape,
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color.White, RectangleShape),
                title = {
                    Text(
                        text = "CREATE NEW FOLDER",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            placeholder = {
                                Text(
                                    text = "FOLDER NAME...",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF111111),
                                unfocusedContainerColor = Color(0xFF0A0A0A),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0xFF333333),
                                cursorColor = Color.White
                            ),
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Row {
                        Text(
                            text = "CANCEL",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { showCreateFolderDialogForApp = null }
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "CREATE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    val cleaned = newFolderNameInput.trim()
                                    if (cleaned.isNotEmpty()) {
                                        coroutineScope.launch {
                                            try {
                                                val existing = dao.getAppState(app.packageName)
                                                val newState = (existing ?: AppState(packageName = app.packageName)).copy(
                                                    folderName = cleaned
                                                )
                                                dao.insertAppState(newState)
                                                reloadAppStates()
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Error creating folder: ${e.message}", e)
                                            }
                                        }
                                        showCreateFolderDialogForApp = null
                                    } else {
                                        Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(8.dp)
                        )
                    }
                }
            )
        }

        // SUPPLEMENTARY DIALOG 3: Folder Viewer Dialog
        if (folderToOpen != null) {
            val fName = folderToOpen!!
            val appsInFolder = folderAppsMap[fName] ?: emptyList()
            AlertDialog(
                onDismissRequest = { folderToOpen = null },
                shape = RectangleShape,
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color.White, RectangleShape),
                title = {
                    Text(
                        text = "FOLDER: ${fName.uppercase()}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (appsInFolder.isEmpty()) {
                            Text(
                                text = "NO APPLICATIONS IN THIS FOLDER.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                items(appsInFolder, key = { app -> "folder_view_${app.packageName}/${app.className}" }) { app ->
                                    Text(
                                        text = app.label.uppercase(),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    folderToOpen = null
                                                    onAppClick(context, app, dao, coroutineScope)
                                                },
                                                onLongClick = {
                                                    appOptionsSelected = app
                                                    folderToOpen = null
                                                }
                                            )
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
                },
                confirmButton = {
                    Text(
                        text = "CLOSE",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { folderToOpen = null }
                            .padding(8.dp)
                    )
                }
            )
        }

        // SUPPLEMENTARY DIALOG 4: Hidden Apps Management Dialog
        if (showHiddenAppsDialog) {
            val hiddenApps = allAppsList.filter { app ->
                appStateMap[app.packageName]?.isHidden == true
            }
            AlertDialog(
                onDismissRequest = { showHiddenAppsDialog = false },
                shape = RectangleShape,
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color.White, RectangleShape),
                title = {
                    Text(
                        text = "HIDDEN APPLICATIONS",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (hiddenApps.isEmpty()) {
                            Text(
                                text = "NO HIDDEN APPLICATIONS.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(hiddenApps, key = { app -> "hidden_view_${app.packageName}/${app.className}" }) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFF222222), RectangleShape)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = app.label.uppercase(),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "[ UNHIDE ]",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    coroutineScope.launch {
                                                        try {
                                                            val existing = dao.getAppState(app.packageName)
                                                            if (existing != null) {
                                                                dao.insertAppState(existing.copy(isHidden = false))
                                                                reloadAppStates()
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("MainActivity", "Error unhiding: ${e.message}", e)
                                                        }
                                                    }
                                                }
                                                .padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Text(
                        text = "CLOSE",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { showHiddenAppsDialog = false }
                            .padding(8.dp)
                    )
                }
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

fun recordAppUsage(dao: com.example.data.AppDao, packageName: String, coroutineScope: kotlinx.coroutines.CoroutineScope) {
    coroutineScope.launch {
        try {
            val existing = dao.getAppState(packageName)
            val newState = (existing ?: AppState(packageName = packageName)).copy(
                totalAccessCount = (existing?.totalAccessCount ?: 0) + 1,
                lastInteractedTime = System.currentTimeMillis()
            )
            dao.insertAppState(newState)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error recording app usage: ${e.message}", e)
        }
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
        recordAppUsage(dao, app.packageName, coroutineScope)
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
            recordAppUsage(dao, app.packageName, coroutineScope)
            launchApp(context, app)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
