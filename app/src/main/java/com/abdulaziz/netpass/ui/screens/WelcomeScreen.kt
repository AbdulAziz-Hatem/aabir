package com.abdulaziz.netpass.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // مؤشر الصفحات (Dots Indicator) مع إمكانية الوصول لقارئ الشاشة
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics {
                            contentDescription = "الخطوة ${pagerState.currentPage + 1} من 3"
                        }
                    ) {
                        repeat(3) { index ->
                            val isSelected = pagerState.currentPage == index
                            val width by animateDpAsState(targetValue = if (isSelected) 28.dp else 10.dp, label = "dotWidth")
                            Box(
                                modifier = Modifier
                                    .height(10.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                            )
                        }
                    }

                    // أزرار التنقل السفلية
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pagerState.currentPage < 2) {
                            TextButton(
                                onClick = onGetStarted,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("تخطي", fontSize = 14.sp)
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }

                        Button(
                            onClick = {
                                if (pagerState.currentPage < 2) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                } else {
                                    onGetStarted()
                                }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (pagerState.currentPage == 2) "ابدأ الاستخدام" else "التالي",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowBack, contentDescription = null) // السهم لليمين في بيئة RTL
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> OnboardingStepPage(
                    icon = Icons.Default.Wifi,
                    badgeText = "نسخة تجريبية (Alpha Preview)",
                    title = "مرحباً بك في نِت بَاس",
                    subtitle = "اعبُر إلى شبكتك بلمحة بصر",
                    description = "تطبيق Android حر ومفتوح المصدر (FOSS) مبني بالكامل بلغة Kotlin وإطار Jetpack Compose الحديث، ومخصص لتمكين المكفوفين ومستخدمي قارئات الشاشة (TalkBack / Jieshuo) من مسح كروت Wi-Fi والتسجيل التلقائي في MikroTik بنقرة واحدة."
                )
                1 -> OnboardingStepPage(
                    icon = Icons.Default.Visibility,
                    badgeText = "الرؤية الموجهة التكيفية",
                    title = "التوجيه المكتبي الذكي",
                    subtitle = "إرشاد حركي فوري بدون تشويش أو تأخير",
                    description = "عند وضع كرت Wi-Fi الصغير على الطاولة، يرشدك الهاتف صوتياً بكلمات سريعة ومقتضبة تتوافق مع حركة يدك (يمين، يسار، اقترب...)، مع حماية المعالج والبطارية. وبمجرد استقرار الكرت في البؤرة الذهبية، يتم التقاط الكود ذاتياً فوراً دون لمس الشاشة."
                )
                2 -> PermissionsAndFallbackPage(
                    hasPermission = hasCameraPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
}

@Composable
fun OnboardingStepPage(
    icon: ImageVector,
    badgeText: String,
    title: String,
    subtitle: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = badgeText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = description,
                modifier = Modifier.padding(20.dp),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionsAndFallbackPage(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "إدارة الأذونات والبدائل",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = "أنت دائماً في تحكم كامل",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasPermission) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (hasPermission) "إذن الكاميرا مفعّل بنجاح" else "إذن الكاميرا (اختياري للمسح)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = "تُستخدم الكاميرا فقط لمسح رمز الكرت ضوئياً محلياً على هاتفك دون إرسال أي صورة للخارج.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                if (!hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("منح إذن الكاميرا الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // بطاقة ضمان استمرار العمل في حال رفض الإذن
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "حتى لو تم رفض الإذن الآن أو لاحقاً، فإن التطبيق يعمل بكامل وظائفه عبر كتابة أو لصق الرمز يدوياً دون أي تعطل.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}
