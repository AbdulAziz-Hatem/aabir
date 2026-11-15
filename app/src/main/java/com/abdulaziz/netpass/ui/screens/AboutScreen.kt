package com.abdulaziz.netpass.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // دعم إيماءة وزر الرجوع التنبؤي في Android
    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("عن تطبيق نِت بَاس والمطور", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = "الرجوع إلى الإعدادات" }
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null) // السهم لليمين في بيئة RTL
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("الرجوع إلى شاشة الإعدادات", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ترويسة التطبيق والشعار
            item {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "نِت بَاس | NetPass",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "إصدار تجريبي (Alpha Preview v1.1.0)",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "«اعبُر إلى شبكتك بلمحة بصر»",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // بطاقة 1: قصة نشأة التطبيق الحقيقية
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Text("قصة نشأة تطبيق نِت بَاس", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.semantics { heading() })
                        }

                        Text(
                            text = "نشأ هذا التطبيق لحاجة شخصية حقيقية عندما بحثنا مطولاً في المتاجر والمستودعات عن تطبيق مفتوح المصدر لمسح كروت شبكات Wi-Fi ضوئياً بدقة واحترافية تلائم المكفوفين ومستخدمي قارئات الشاشة، فلم نجد ما يلبي طموحنا ومعايير الأمان الشاملة وخلو التطبيقات من الإعلانات المزعجة.\n\nلذا اتخذنا القرار ببنائه وتطويره من الصفر بأحدث تقنيات Android الأصيلة (Kotlin & Jetpack Compose & CameraX) ليكون برمجية حرة ومتاحة للجميع بأعلى معايير الإتقان للأبد.",
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // بطاقة 2: تعهد الأمان والخصوصية الصارم للمستخدم
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تعهد الأمان والخصوصية الصارم", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Text("• لا خوادم خارجية: الاتصال محصور حصرياً بين هاتفك وبوابة راوتر MikroTik المحلي الخاص بالشبكة المتصل بها فقط.", fontSize = 12.sp, lineHeight = 18.sp)
                        Text("• صفر تتبع وصفر إعلانات: التطبيق لا يحتوي على أي أدوات تحليلية أو أكواد تتبع أو إعلانات مخفية نهائياً.", fontSize = 12.sp, lineHeight = 18.sp)
                        Text("• معالجة محلية 100%: الكاميرا تفحص الأرقام داخل معالج الهاتف ولا تحفظ الصور في الذاكرة إطلاقاً.", fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // بطاقة 3: الحزم المتاحة
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("حزم التطبيق المتوفرة:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.semantics { heading() })
                        Text("• النسخة الكاملة (Full - 21MB): مدمج بها محرك الذكاء الاصطناعي لتعمل في أي مكان بدون اتصال إنترنت وبدون Google Play Services.", fontSize = 12.sp, lineHeight = 18.sp)
                        Text("• النسخة الخفيفة (Lite - 2.7MB): حجم ضئيل جداً لتوفير باقة البيانات، وتعتمد على تنزيل النموذج ديناميكياً عبر Google Play Services.", fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // بطاقة 4: المطور والترخيص وزيارة المستودع
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("المطور: عبد العزيز حاتم (AbdulAziz Hatem)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("الترخيص: رخصة جنو العمومية (GNU General Public License v3.0 - GPLv3) - حر ومفتوح المصدر 100% للأبد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdulAziz-Hatem/netpass"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("زيارة مستودع المشروع على GitHub", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
