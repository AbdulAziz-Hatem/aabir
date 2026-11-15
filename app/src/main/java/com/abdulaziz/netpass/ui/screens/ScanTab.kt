package com.abdulaziz.netpass.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulaziz.netpass.model.AppSettings

@Composable
fun ScanTab(
    pinText: String,
    onPinChange: (String) -> Unit,
    statusText: String,
    isLoggingIn: Boolean,
    settings: AppSettings,
    onStartScan: () -> Unit,
    onExecuteLogin: (String) -> Unit,
    onCopySuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // بطاقة فتح الكاميرا (مجموعة تنقل متماسكة)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onStartScan() }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = "امسح كرت Wi-Fi، انقر لفتح الكاميرا والمسح مع التوجيه الصوتي"
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
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
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "امسح كرت Wi-Fi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "تعرف فوري محلي مع إرشاد حركي صوتي ذكي",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // بطاقة إدخال الرمز والإجراءات
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { isTraversalGroup = true },
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "رمز كرت Wi-Fi (PIN)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = pinText,
                        onValueChange = onPinChange,
                        placeholder = {
                            Text(
                                "امسح بالكاميرا أو اكتب الرمز",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onExecuteLogin(pinText) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                stateDescription = if (pinText.isEmpty()) "الحقل فارغ" else "${pinText.length} أرقام مكتوبة"
                            },
                        shape = RoundedCornerShape(16.dp),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    )

                    Button(
                        onClick = { onExecuteLogin(pinText) },
                        enabled = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isLoggingIn) "جارٍ تسجيل الدخول في الشبكة" else "تسجيل الدخول في الشبكة"
                            },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ تسجيل الدخول...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "تسجيل الدخول في الشبكة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Voucher", pinText))
                            onCopySuccess()
                        },
                        enabled = pinText.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("نسخ الكود")
                    }
                }
            }
        }

        // بطاقة إعلان الحالة لقارئات الشاشة
        if (statusText.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
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
