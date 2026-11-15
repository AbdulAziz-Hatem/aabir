package com.abdulaziz.aabir.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.abdulaziz.aabir.ui.screens.AboutScreen
import com.abdulaziz.aabir.ui.screens.HistoryTab
import com.abdulaziz.aabir.ui.screens.ScanTab
import com.abdulaziz.aabir.ui.screens.SettingsTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    feedback: FeedbackManager,
    onOpenOnboarding: () -> Unit = {},
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
    var currentZoom by remember { mutableStateOf(2.2f) }
    var instantCaptureCounter by remember { mutableStateOf(0) }
    var activeWifiGateway by remember { mutableStateOf("") }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isScanning = true
            torchState = true
            onToggleTorch(true)
            feedback.speak("الكاميرا تعمل وجّه الهاتف نحو الكرت", settings.voiceAnnounce, settings.selectedVoiceName)
        } else {
            feedback.speak("تم رفض إذن الكاميرا يمكنك استخدام الإدخال اليدوي", settings.voiceAnnounce, settings.selectedVoiceName)
            showPermissionRationaleDialog = true
        }
    }

    // دالة بث موحدة تمنع النطق مرتين بشكل قاطع
    val announceFeedback: (String) -> Unit = { message ->
        if (settings.voiceAnnounce) {
            feedback.speak(message, true, settings.selectedVoiceName)
        } else {
            feedback.showToast(message)
        }
    }

    LaunchedEffect(settings.selectedTtsEngine) {
        feedback.setEngine(settings.selectedTtsEngine)
    }

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

    // إدارة إيماءات التراجع التنبؤي
    BackHandler(enabled = showAboutScreen) {
        showAboutScreen = false
    }

    BackHandler(enabled = isScanning) {
        isScanning = false
        torchState = false
        onToggleTorch(false)
    }

    BackHandler(enabled = !isScanning && !showAboutScreen && currentTab != AppTab.SCAN) {
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
        if (!isLoggingIn) {
            val clean = pinToLogin.filter { it.isDigit() }
            if (clean.length < settings.minLen) {
                val alertMsg = if (clean.isEmpty()) "يرجى كتابة أو مسح رمز الكرت أولا" else "رمز الكرت غير مكتمل مطلوب ${settings.minLen} أرقام على الأقل"
                statusText = alertMsg
                feedback.playErrorTone()
                feedback.vibrateError()
                announceFeedback(alertMsg)
            } else {
                isLoggingIn = true
                statusText = "جار تسجيل الدخول بالكود $clean"
                announceFeedback("جار تسجيل الدخول في الشبكة")

                scope.launch {
                    val (success, msg) = MikrotikClient.login(context, clean, settings.gatewayIp)
                    isLoggingIn = false
                    statusText = msg

                    if (success) {
                        feedback.playSuccessChord()
                        feedback.vibrateSuccess()
                        announceFeedback(msg)
                        val updated = listOf(VoucherRecord(clean)) + historyList.filter { it.pin != clean }
                        historyList = updated
                        storage.saveHistory(updated)
                    } else {
                        feedback.playErrorTone()
                        feedback.vibrateError()
                        announceFeedback(msg)
                    }
                }
            }
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        if (showAboutScreen) {
            AboutScreen(onBack = { showAboutScreen = false })
        } else {
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
                                zoomRatio = currentZoom,
                                instantCaptureTrigger = instantCaptureCounter,
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
                                        statusText = "تم التقاط الكود $detectedPin"
                                        announceFeedback("تم التقاط الكود $detectedPin اضغط زر الدخول")
                                    }
                                },
                                onGuidanceNeeded = { guideMsg ->
                                    announceFeedback(guideMsg)
                                },
                                onSweetSpotLocked = {
                                    feedback.playSuccessChord()
                                    feedback.vibrateFound()
                                    announceFeedback("ثبت يدك تم الالتقاط")
                                },
                                onRadarPulse = {
                                    feedback.playRadarTick()
                                }
                            )

                            // إطار التصويب البصري عالي التباين
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.90f)
                                        .height(115.dp)
                                        .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ضع خانة الكرت الصغير داخل هذا الإطار",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clearAndSetSemantics {}
                                    )
                                }
                            }

                            // شريط أدوات الكاميرا العلوية
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

                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clickable {
                                            val newZoom = if (currentZoom >= 2.0f) 1.0f else 2.2f
                                            currentZoom = newZoom
                                            val zoomMsg = if (newZoom >= 2.0f) "تكبير للكروت الصغيرة" else "تكبير عادي"
                                            announceFeedback(zoomMsg)
                                        }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = if (currentZoom >= 2.0f) "مفتاح التكبير مفعل للكروت الصغيرة انقر للتحويل إلى عادي" else "مفتاح التكبير مفعل للعادي انقر للتحويل إلى الكروت الصغيرة"
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (currentZoom >= 2.0f) "2.2x مصغر" else "1.0x عادي",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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

                            // زر الالتقاط الفوري المباشر
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding()
                                    .padding(bottom = 32.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Button(
                                    onClick = {
                                        instantCaptureCounter++
                                        feedback.vibrateFound()
                                        announceFeedback("جار الالتقاط الفوري")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth(0.78f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "التقاط فوري الآن",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
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
                                        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                        if (isGranted) {
                                            isScanning = true
                                            torchState = true
                                            onToggleTorch(true)
                                            announceFeedback("الكاميرا تعمل وجّه الهاتف نحو الكرت")
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    onExecuteLogin = executeLogin,
                                    onCopySuccess = {
                                        statusText = "تم نسخ الرمز إلى الحافظة"
                                        announceFeedback("تم النسخ")
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
                                    },
                                    onCopySuccess = {
                                        statusText = "تم نسخ الرمز إلى الحافظة"
                                        announceFeedback("تم النسخ")
                                    }
                                )
                            }

                            AppTab.SETTINGS -> {
                                SettingsTab(
                                    settings = settings,
                                    onSettingsChange = updateSettings,
                                    activeWifiGateway = activeWifiGateway,
                                    feedback = feedback,
                                    onOpenAbout = {
                                        showAboutScreen = true
                                    },
                                    onReplayOnboarding = onOpenOnboarding,
                                    onResetDefaults = {
                                        updateSettings(AppSettings())
                                    }
                                )
                            }
                        }
                    }
                }

                if (showPermissionRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationaleDialog = false },
                        title = { Text("إذن الكاميرا غير مفعل", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                text = "يتطلب المسح الضوئي المباشر إذن الكاميرا. يمكنك تفعيل الإذن من إعدادات الهاتف، أو الاستمرار بكتابة رمز الكرت يدوياً في الحقل المخصص دون أي تعطل.",
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPermissionRationaleDialog = false
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                }
                            ) {
                                Text("فتح إعدادات الهاتف")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionRationaleDialog = false }) {
                                Text("الإدخال اليدوي")
                            }
                        }
                    )
                }
            }
        }
    }
}
