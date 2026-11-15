package com.abdulaziz.aabir.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulaziz.aabir.feedback.FeedbackManager
import com.abdulaziz.aabir.model.AppSettings
import com.abdulaziz.aabir.ui.components.AboutCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    activeWifiGateway: String,
    feedback: FeedbackManager,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedEngines = remember { feedback.getInstalledEngines() }
    var engineDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "الإعدادات وإمكانية الوصول",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }

        // قسم الصوت وقارئ الشاشة ومحركات TTS
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "التنبيهات الصوتية ومحرك القراءة",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الإشعارات الناطقة (TTS)")
                        Switch(
                            checked = settings.voiceAnnounce,
                            onCheckedChange = { onSettingsChange(settings.copy(voiceAnnounce = it)) }
                        )
                    }

                    // خيار اختيار محرك تحويل النص إلى كلام (Google vs Samsung vs System Default)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "اختيار محرك تحويل النص إلى كلام:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        ExposedDropdownMenuBox(
                            expanded = engineDropdownExpanded,
                            onExpandedChange = { engineDropdownExpanded = it }
                        ) {
                            val currentEngineLabel = installedEngines.find { it.packageName == settings.selectedTtsEngine }?.label
                                ?: if (settings.selectedTtsEngine.isEmpty()) "افتراضي النظام (تلقائي)" else settings.selectedTtsEngine

                            OutlinedTextField(
                                value = currentEngineLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = engineDropdownExpanded,
                                onDismissRequest = { engineDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("افتراضي النظام (تلقائي)", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onSettingsChange(settings.copy(selectedTtsEngine = ""))
                                        feedback.setEngine("")
                                        engineDropdownExpanded = false
                                        feedback.speak("تم التبديل إلى محرك النظام الافتراضي", true)
                                    }
                                )
                                installedEngines.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(engine.label) },
                                        onClick = {
                                            onSettingsChange(settings.copy(selectedTtsEngine = engine.packageName))
                                            feedback.setEngine(engine.packageName)
                                            engineDropdownExpanded = false
                                            feedback.speak("تم التبديل إلى محرك ${engine.label}", true)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // زر فتح إعدادات TTS بالنظام
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent("com.android.settings.TTS_SETTINGS")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                } catch (ignored: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SettingsVoice, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح إعدادات الصوت في نظام أندرويد", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("صوت رادار الكاميرا")
                            Text(
                                "تكتكة ناعمة كل 2.8 ثانية لعدم الإزعاج",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = settings.radarSound,
                            onCheckedChange = { onSettingsChange(settings.copy(radarSound = it)) }
                        )
                    }

                    Button(
                        onClick = {
                            feedback.speak(
                                "أهلاً بك في تطبيق عابر، محرك الصوت يعمل بشكل متكامل.",
                                true,
                                settings.selectedVoiceName
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "إعدادات شبكة ميكروتك (Hotspot)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    if (activeWifiGateway.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("بوابة الواي فاي المتصلة:")
                                Text(
                                    activeWifiGateway,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الدخول التلقائي فور المسح")
                        Switch(
                            checked = settings.autoLoginOnScan,
                            onCheckedChange = { onSettingsChange(settings.copy(autoLoginOnScan = it)) }
                        )
                    }

                    OutlinedTextField(
                        value = settings.gatewayIp,
                        onValueChange = { onSettingsChange(settings.copy(gatewayIp = it)) },
                        label = { Text("عنوان بوابة الراوتر المخصصة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // بطاقة حول التطبيق والمطور القابلة للطي والتوسيع
        item {
            AboutCard(
                feedback = feedback,
                selectedVoiceName = settings.selectedVoiceName,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // زر استعادة الافتراضيات
        item {
            OutlinedButton(
                onClick = onResetDefaults,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("استعادة كافة الإعدادات الافتراضية")
            }
        }
    }
}
