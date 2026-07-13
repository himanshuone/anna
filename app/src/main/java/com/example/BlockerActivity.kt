package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: "Unknown App"

        setContent {
            BlockerScreen(
                targetPackage = targetPackage,
                onDismiss = {
                    finish()
                }
            )
        }
    }

    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

enum class BlockerState {
    LOADING,
    SESSION_SETUP,
    LOCKOUT_EXTENSION
}

@Composable
fun BlockerScreen(
    targetPackage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.appDao()

    var screenState by remember { mutableStateOf(BlockerState.LOADING) }
    var penaltySecondsLeft by remember { mutableStateOf(20) }

    LaunchedEffect(targetPackage) {
        val state = dao.getAppState(targetPackage)
        val currentTime = System.currentTimeMillis()

        if (state == null) {
            screenState = BlockerState.SESSION_SETUP
        } else {
            val isExpiredOrLocked = state.isLockedOut || 
                                    state.lockOutUntil > currentTime || 
                                    state.currentSessionEndTime < currentTime

            if (isExpiredOrLocked) {
                screenState = BlockerState.LOCKOUT_EXTENSION
            } else {
                onDismiss()
            }
        }
    }

    LaunchedEffect(screenState) {
        if (screenState == BlockerState.LOCKOUT_EXTENSION) {
            penaltySecondsLeft = 20
            while (penaltySecondsLeft > 0) {
                delay(1000)
                penaltySecondsLeft--
            }
        }
    }

    fun classifyPackage(packageName: String): String {
        val lower = packageName.lowercase()
        return if (lower.contains("social") || 
                   lower.contains("facebook") || 
                   lower.contains("twitter") || 
                   lower.contains("instagram") || 
                   lower.contains("tiktok") || 
                   lower.contains("youtube") || 
                   lower.contains("netflix") || 
                   lower.contains("game") || 
                   lower.contains("reddit") || 
                   lower.contains("snapchat")) {
            "Distraction"
        } else if (lower.contains("gmail") || 
                   lower.contains("calendar") || 
                   lower.contains("keep") || 
                   lower.contains("notes") || 
                   lower.contains("drive") || 
                   lower.contains("docs")) {
            "Productivity"
        } else {
            "Distraction"
        }
    }

    fun startSessionForMinutes(minutes: Int) {
        coroutineScope.launch {
            val currentEndTime = if (minutes == -1) {
                Long.MAX_VALUE
            } else {
                System.currentTimeMillis() + (minutes * 60 * 1000)
            }
            val newRecord = AppState(
                packageName = targetPackage,
                isLockedOut = false,
                lockOutUntil = 0L,
                currentSessionEndTime = currentEndTime
            )
            dao.insertAppState(newRecord)
            onDismiss()
        }
    }

    // Centered pop-up dialog in middle center of screen with translucent black overlay background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (screenState) {
            BlockerState.LOADING -> {
                Text(
                    text = "VOID",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            BlockerState.SESSION_SETUP -> {
                // Centered popup dialog card
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .border(1.dp, Color.White, RectangleShape)
                        .background(Color.Black)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "INTENT GATED",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = targetPackage.substringAfterLast("."),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = targetPackage,
                        color = Color.DarkGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF222222))
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "SELECT SESSION DURATION",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Clean pre-defined minutes option buttons (no manual entry)
                    val durations = listOf(1, 2, 5, 10, -1)
                    durations.forEach { minutes ->
                        Button(
                            onClick = { startSessionForMinutes(minutes) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag(if (minutes == -1) "duration_unlimited" else "duration_${minutes}m"),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = if (minutes == -1) "UNLIMITED ACCESS" else "$minutes MINUTE" + (if (minutes > 1) "S" else ""),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(homeIntent)
                            (context as? Activity)?.finish()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "EXIT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            BlockerState.LOCKOUT_EXTENSION -> {
                // Centered lockout card
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .border(1.dp, Color.White, RectangleShape)
                        .background(Color.Black)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LIMIT EXCEEDED",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Time Completed",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = targetPackage.substringAfterLast("."),
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF222222))
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    val isPenaltyOver = penaltySecondsLeft == 0

                    if (!isPenaltyOver) {
                        // Penalty countdown in progress: show ONLY the ticking countdown, hide extend options
                        Text(
                            text = "PENALTY UNTIL EXTEND AVAILABLE",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "WAIT ${penaltySecondsLeft}s",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))
                    } else {
                        // Penalty finished: display pre-configured minutes selection options to extend session
                        Text(
                            text = "SELECT EXTENSION TIME",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val durations = listOf(1, 2, 5, 10, -1)
                        durations.forEach { minutes ->
                            Button(
                                onClick = { startSessionForMinutes(minutes) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .testTag(if (minutes == -1) "extend_unlimited" else "extend_${minutes}m"),
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Text(
                                    text = if (minutes == -1) "UNLIMITED ACCESS" else "$minutes MINUTE" + (if (minutes > 1) "S" else ""),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Exit button is always accessible to return to home
                    OutlinedButton(
                        onClick = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(homeIntent)
                            (context as? Activity)?.finish()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "EXIT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
