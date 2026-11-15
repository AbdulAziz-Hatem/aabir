# قواعد Proguard لمشروع عابر لحماية المحركات بعد الضغط
-keep class com.abdulaziz.aabir.model.** { *; }
-keep class com.abdulaziz.aabir.network.** { *; }

# محرك الذكاء الاصطناعي Google ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**

# مكتبة الكاميرا CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# مكتبة الشبكة OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes *Annotation*

# مكتبة Coroutines
-dontwarn kotlinx.coroutines.**
