package com.abdulaziz.aabir.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulaziz.aabir.feedback.FeedbackManager
import com.abdulaziz.aabir.model.AppSettings
import com.abdulaziz.aabir.ui.components.AboutCard

@Composable
fun SettingsTab(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    activeWifiGateway: String,
    feedback: FeedbackManager,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        // قسم الصوت وقارئ الشاشة
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "التنبيهات الصوتية وقارئ الشاشة",
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
