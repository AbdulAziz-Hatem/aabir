package com.abdulaziz.aabir.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulaziz.aabir.camera.CameraPreview
import com.abdulaziz.aabir.feedback.FeedbackManager
import com.abdulaziz.aabir.model.AppSettings
import com.abdulaziz.aabir.model.AppTab
import com.abdulaziz.aabir.model.VoucherRecord
import com.abdulaziz.aabir.network.MikrotikClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    feedback: FeedbackManager,
    onToggleTorch: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(AppSettings()) }
    var currentTab by remember { mutableStateOf(AppTab.SCAN) }
    var pinText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var torchState by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf(listOf<VoucherRecord>()) }
    var activeWifiGateway by remember { mutableStateOf("") }

    // تحديث بوابة الواي فاي الحالية
    LaunchedEffect(Unit) {
        activeWifiGateway = MikrotikClient.getActiveGatewayIp(context)
    }

    // دالة تنفيذ تسجيل الدخول في ميكروتك
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
                    historyList = listOf(VoucherRecord(clean)) + historyList.filter { it.pin != clean }
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
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("عَابِر", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("v1.0.0", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("اعبُر إلى شبكتك بلمحة بصر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { settings = settings.copy(isDarkTheme = !settings.isDarkTheme) },
                            modifier = Modifier.semantics { contentDescription = if (settings.isDarkTheme) "التبديل إلى الوضع الفاتح" else "التبديل إلى الوضع الداكن" }
                        ) {
                            Icon(if (settings.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null)
                        }
                    }
                )
            },
            bottomBar = {
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
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // شاشة المسح عبر CameraX
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
                                    feedback.speak("تم التقاط الكود $detectedPin، اضغط زر الدخول", settings.voiceAnnounce, settings.selectedVoiceName)
                                }
                            },
                            onRadarPulse = {
                                feedback.playRadarTick()
                            }
                        )

                        // شريط أدوات الكاميرا العلوية
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                    .background(if (torchState) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .semantics { contentDescription = if (torchState) "إطفاء فلاش الكاميرا" else "تشغيل فلاش الكاميرا" }
                            ) {
                                Icon(if (torchState) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                } else {
                    when (currentTab) {
                        AppTab.SCAN -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .clickable {
                                                isScanning = true
                                                torchState = true
                                                onToggleTorch(true)
                                                feedback.speak("تم فتح الكاميرا، وجه الهاتف نحو الكرت", settings.voiceAnnounce, settings.selectedVoiceName)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                                            }
                                            Text("امسح كرت الواي فاي", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            Text("تعرف فوري محلي بدون إنترنت · معالجة ذكية للخدش", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("رمز كرت الواي فاي (PIN)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            OutlinedTextField(
                                                value = pinText,
                                                onValueChange = { pinText = it.filter { char -> char.isDigit() } },
                                                placeholder = { Text("امسح بالكاميرا أو اكتب الرمز", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                                            )

                                            Button(
                                                onClick = { executeLogin(pinText) },
                                                enabled = pinText.length >= settings.minLen && !isLoggingIn,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(54.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                if (isLoggingIn) {
                                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("جارٍ تسجيل الدخول...", fontWeight = FontWeight.Bold)
                                                } else {
                                                    Icon(Icons.Default.Wifi, contentDescription = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("تسجيل الدخول في الشبكة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cb.setPrimaryClip(ClipData.newPlainText("Voucher", pinText))
                                                    statusText = "تم نسخ الرمز إلى الحافظة"
                                                    feedback.speak("تم النسخ", settings.voiceAnnounce, settings.selectedVoiceName)
                                                },
                                                enabled = pinText.isNotEmpty(),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("نسخ الكود")
                                            }
                                        }
                                    }
                                }

                                if (statusText.isNotEmpty()) {
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .semantics { liveRegion = LiveRegionMode.Assertive },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = statusText,
                                                modifier = Modifier.padding(16.dp),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AppTab.HISTORY -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("سجل الكروت المشحونة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (historyList.isNotEmpty()) {
                                            TextButton(onClick = { historyList = emptyList() }) {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("مسح السجل", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }

                                if (historyList.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text("لا توجد كروت مشحونة في السجل حتى الآن", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                        }
                                    }
                                }

                                items(historyList) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(item.pin, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                Text(
                                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp)),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                            IconButton(onClick = {
                                                pinText = item.pin
                                                currentTab = AppTab.SCAN
                                                executeLogin(item.pin)
                                            }) {
                                                Icon(Icons.Default.Refresh, contentDescription = "إعادة تسجيل الدخول بهذا الكرت")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AppTab.SETTINGS -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text("الإعدادات وإمكانية الوصول", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }

                                // قسم الصوت
                                item {
                                    Card(shape = RoundedCornerShape(18.dp)) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("التنبيهات الصوتية وقارئ الشاشة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("الإشعارات الناطقة (TTS)")
                                                Switch(
                                                    checked = settings.voiceAnnounce,
                                                    onCheckedChange = { settings = settings.copy(voiceAnnounce = it) }
                                                )
                                            }

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("صوت رادار الكاميرا")
                                                    Text("تكتكة ناعمة كل 2.8 ثانية لعدم الإزعاج", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                }
                                                Switch(
                                                    checked = settings.radarSound,
                                                    onCheckedChange = { settings = settings.copy(radarSound = it) }
                                                )
                                            }

                                            Button(
                                                onClick = { feedback.speak("أهلاً بك في تطبيق عابر، محرك الصوت يعمل بشكل متكامل.", true, settings.selectedVoiceName) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("تجربة الصوت")
                                            }
                                        }
                                    }
                                }

                                // قسم شبكة ميكروتك
                                item {
                                    Card(shape = RoundedCornerShape(18.dp)) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("إعدادات شبكة ميكروتك (Hotspot)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                            if (activeWifiGateway.isNotEmpty()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("بوابة الواي فاي المتصلة:")
                                                        Text(activeWifiGateway, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                            }

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("الدخول التلقائي فور المسح")
                                                Switch(
                                                    checked = settings.autoLoginOnScan,
                                                    onCheckedChange = { settings = settings.copy(autoLoginOnScan = it) }
                                                )
                                            }

                                            OutlinedTextField(
                                                value = settings.gatewayIp,
                                                onValueChange = { settings = settings.copy(gatewayIp = it) },
                                                label = { Text("عنوان بوابة الراوتر المخصصة") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }

                                // قسم عن التطبيق والمطور الحصري (Single Definitive About Section)
                                item {
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Text("عن تطبيق عَابِر والمطور", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }

                                            Text(
                                                "تطبيق «عَابِر | Aabir» هو تطبيق أندرويد حر ومفتوح المصدر (FOSS) تم بناؤه بالكامل بلغة Kotlin وإطار Jetpack Compose الحديث، ومخصص لتمكين المكفوفين ومستخدمي قارئات الشاشة (TalkBack / Jieshuo) من مسح كروت الواي فاي ضوئياً وتسجيل الدخول التلقائي في ميكروتك دون الحاجة لفتح المتصفح.",
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )

                                            Surface(
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text("الحزم المتاحة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                    Text("• النسخة الكاملة (Full): مدمج بها محرك الذكاء الاصطناعي لتعمل بدون إنترنت.", fontSize = 12.sp)
                                                    Text("• النسخة الخفيفة (Lite): حجم ضئيل جداً (~3MB) لتوفير رصيد البيانات.", fontSize = 12.sp)
                                                }
                                            }

                                            Text("منصة الفحص الحسي المباشر:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        feedback.playSuccessChord()
                                                        feedback.vibrateSuccess()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("فحص النجاح", fontSize = 11.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        feedback.playErrorTone()
                                                        feedback.vibrateError()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("فحص الفشل", fontSize = 11.sp)
                                                }
                                            }

                                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("المطور: عبد العزيز حاتم (AbdulAziz Hatem)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("الترخيص: رخصة جنو العمومية (GNU General Public License v3.0 (GPLv3)) - حر ومفتوح المصدر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                            }

                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdulAziz-Hatem/aabir"))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("زيارة مستودع المشروع على GitHub", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
