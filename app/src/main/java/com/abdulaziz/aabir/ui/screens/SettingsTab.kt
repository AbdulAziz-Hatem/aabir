package com.abdulaziz.aabir.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

        // قسم الصوت وقارئ الشاشة ومحركات TTS (مدمج دلالياً 100%)
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

                    // سطر الإشعارات الناطقة مدمج دلالياً بالكامل
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSettingsChange(settings.copy(voiceAnnounce = !settings.voiceAnnounce))
                            }
                            .minimumInteractiveComponentSize()
                            .semantics(mergeDescendants = true) {
                                role = Role.Switch
                                stateDescription = if (settings.voiceAnnounce) "مفعل" else "معطل"
                                contentDescription = "الإشعارات الناطقة"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الإشعارات الناطقة (TTS)", fontWeight = FontWeight.SemiBold)
                            Text("نطق حالة الكاميرا والكرت ونتيجة الدخول", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.voiceAnnounce,
                            onCheckedChange = null
                        )
                    }

                    // خيار اختيار محرك تحويل النص إلى كلام
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
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SettingsVoice, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح إعدادات الصوت في نظام أندرويد", fontSize = 12.sp)
                    }

                    // سطر صوت رادار الكاميرا مدمج دلالياً
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSettingsChange(settings.copy(radarSound = !settings.radarSound))
                            }
                            .minimumInteractiveComponentSize()
                            .semantics(mergeDescendants = true) {
                                role = Role.Switch
                                stateDescription = if (settings.radarSound) "مفعل" else "معطل"
                                contentDescription = "صوت رادار الكاميرا"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("صوت رادار الكاميرا", fontWeight = FontWeight.SemiBold)
                            Text(
                                "تكتكة ناعمة كل 2.8 ثانية لعدم الإزعاج",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = settings.radarSound,
                            onCheckedChange = null
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
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
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

                    // سطر الدخول التلقائي مدمج دلالياً
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSettingsChange(settings.copy(autoLoginOnScan = !settings.autoLoginOnScan))
                            }
                            .minimumInteractiveComponentSize()
                            .semantics(mergeDescendants = true) {
                                role = Role.Switch
                                stateDescription = if (settings.autoLoginOnScan) "مفعل" else "معطل"
                                contentDescription = "الدخول التلقائي فور المسح"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الدخول التلقائي فور المسح", fontWeight = FontWeight.SemiBold)
                            Text("إرسال الكود للراوتر بمجرد التقاطه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = settings.autoLoginOnScan,
                            onCheckedChange = null
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

        // قسم معايير كرت الواي فاي (أطوال الكود)
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "معايير طول رمز الكرت",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = settings.minLen.toString(),
                            onValueChange = {
                                val v = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1
                                onSettingsChange(settings.copy(minLen = v))
                            },
                            label = { Text("الحد الأدنى") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = settings.maxLen.toString(),
                            onValueChange = {
                                val v = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 20
                                onSettingsChange(settings.copy(maxLen = v))
                            },
                            label = { Text("الحد الأقصى") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
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
                    .height(48.dp)
                    .minimumInteractiveComponentSize(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("استعادة كافة الإعدادات الافتراضية")
            }
        }
    }
}
