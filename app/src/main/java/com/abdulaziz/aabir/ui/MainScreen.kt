package com.abdulaziz.aabir.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abdulaziz.aabir.camera.CameraPreview
import com.abdulaziz.aabir.feedback.FeedbackManager
import com.abdulaziz.aabir.model.AppSettings
import com.abdulaziz.aabir.model.AppTab
import com.abdulaziz.aabir.model.StorageManager
import com.abdulaziz.aabir.model.VoucherRecord
import com.abdulaziz.aabir.network.MikrotikClient
import com.abdulaziz.aabir.ui.screens.HistoryTab
import com.abdulaziz.aabir.ui.screens.ScanTab
import com.abdulaziz.aabir.ui.screens.SettingsTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    feedback: FeedbackManager,
    onToggleTorch: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val storage = remember { StorageManager(context) }

    var settings by remember { mutableStateOf(storage.loadSettings()) }
    var historyList by remember { mutableStateOf(storage.loadHistory()) }
    var currentTab by remember { mutableStateOf(AppTab.SCAN) }
    var pinText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var torchState by remember { mutableStateOf(false) }
    var activeWifiGateway by remember { mutableStateOf("") }

    // مراقبة دورة حياة النظام: إطفاء الكاميرا والفلاش فوراً عند مغادرة التطبيق
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (isScanning) {
                    isScanning = false
                    torchState = false
                    onToggleTorch(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // إدارة إيماءة وزر الرجوع التنبؤي
    BackHandler(enabled = isScanning) {
        isScanning = false
        torchState = false
        onToggleTorch(false)
    }

    BackHandler(enabled = !isScanning && currentTab != AppTab.SCAN) {
        currentTab = AppTab.SCAN
    }

    val updateSettings: (AppSettings) -> Unit = { newSettings ->
        settings = newSettings
        storage.saveSettings(newSettings)
    }

    LaunchedEffect(Unit) {
        activeWifiGateway = MikrotikClient.getActiveGatewayIp(context)
    }

    val executeLogin: (String) -> Unit = { pinToLogin ->
        val clean = pinToLogin.filter { it.isDigit() }
        if (clean.length >= settings.minLen) {
            isLoggingIn = true
            statusText = "جارٍ تسجيل الدخول بالكود: $clean"
            feedback.speak("جارٍ تسجيل الدخول في الشبكة", settings.voiceAnnounce, settings.selectedVoiceName)

            scope.launch {
                val (success, msg) = MikrotikClient.login(context, clean, settings.gatewayIp)
                isLoggingIn = false
                statusText = msg

                if (success) {
                    feedback.playSuccessChord()
                    feedback.vibrateSuccess()
                    feedback.speak(msg, settings.voiceAnnounce, settings.selectedVoiceName)
                    val updated = listOf(VoucherRecord(clean)) + historyList.filter { it.pin != clean }
                    historyList = updated
                    storage.saveHistory(updated)
                } else {
                    feedback.playErrorTone()
                    feedback.vibrateError()
                    feedback.speak(msg, settings.voiceAnnounce, settings.selectedVoiceName)
                }
            }
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                if (!isScanning) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("عَابِر", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text("اعبُر إلى شبكتك بلمحة بصر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { updateSettings(settings.copy(isDarkTheme = !settings.isDarkTheme)) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = if (settings.isDarkTheme) "التبديل إلى الوضع الفاتح" else "التبديل إلى الوضع الداكن"
                                    }
                            ) {
                                Icon(
                                    if (settings.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isScanning) {
                    NavigationBar {
                        AppTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    when (tab) {
                                        AppTab.SCAN -> Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        AppTab.HISTORY -> Icon(Icons.Default.History, contentDescription = null)
                                        AppTab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                },
                                label = { Text(tab.title, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isScanning) PaddingValues(0.dp) else paddingValues)
            ) {
                if (isScanning) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraPreview(
                            torchEnabled = torchState,
                            radarEnabled = settings.radarSound,
                            minLen = settings.minLen,
                            maxLen = settings.maxLen,
                            onPinDetected = { detectedPin ->
                                pinText = detectedPin
                                isScanning = false
                                torchState = false
                                onToggleTorch(false)
                                feedback.vibrateFound()

                                if (settings.autoLoginOnScan) {
                                    executeLogin(detectedPin)
                                } else {
                                    feedback.playSuccessChord()
                                    statusText = "تم التقاط الكود: $detectedPin"
                                    feedback.speak(
                                        "تم التقاط الكود $detectedPin، اضغط زر الدخول",
                                        settings.voiceAnnounce,
                                        settings.selectedVoiceName
                                    )
                                }
                            },
                            onGuidanceNeeded = { guideMsg ->
                                feedback.speak(guideMsg, settings.voiceAnnounce, settings.selectedVoiceName)
                            },
                            onRadarPulse = {
                                feedback.playRadarTick()
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isScanning = false
                                    torchState = false
                                    onToggleTorch(false)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .semantics { contentDescription = "إغلاق الكاميرا والعودة" }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    torchState = !torchState
                                    onToggleTorch(torchState)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (torchState) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .semantics {
                                        contentDescription = if (torchState) "إطفاء فلاش الكاميرا" else "تشغيل فلاش الكاميرا"
                                    }
                            ) {
                                Icon(
                                    if (torchState) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }
                } else {
                    when (currentTab) {
                        AppTab.SCAN -> {
                            ScanTab(
                                pinText = pinText,
                                onPinChange = { pinText = it.filter { char -> char.isDigit() } },
                                statusText = statusText,
                                isLoggingIn = isLoggingIn,
                                settings = settings,
                                onStartScan = {
                                    isScanning = true
                                    torchState = true
                                    onToggleTorch(true)
                                    feedback.speak(
                                        "الكاميرا تعمل، وجّه الهاتف نحو الكرت",
                                        settings.voiceAnnounce,
                                        settings.selectedVoiceName
                                    )
                                },
                                onExecuteLogin = executeLogin,
                                onCopySuccess = {
                                    statusText = "تم نسخ الرمز إلى الحافظة"
                                    feedback.speak("تم النسخ", settings.voiceAnnounce, settings.selectedVoiceName)
                                }
                            )
                        }

                        AppTab.HISTORY -> {
                            HistoryTab(
                                historyList = historyList,
                                onClearHistory = {
                                    historyList = emptyList()
                                    storage.saveHistory(emptyList())
                                },
                                onReLogin = { rePin ->
                                    pinText = rePin
                                    currentTab = AppTab.SCAN
                                    executeLogin(rePin)
                                }
                            )
                        }

                        AppTab.SETTINGS -> {
                            SettingsTab(
                                settings = settings,
                                onSettingsChange = updateSettings,
                                activeWifiGateway = activeWifiGateway,
                                feedback = feedback,
                                onResetDefaults = {
                                    updateSettings(AppSettings())
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
