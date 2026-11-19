# Keep ComponentRegistrar no-arg constructors for ML Kit reflection instantiation under R8 Full Mode (AGP 9+)
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
    *;
}

# Google ML Kit Text Recognition and Common components
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# CameraX Core & Lifecycle
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# NetPass Models
-keep class com.abdulaziz.netpass.model.** { *; }

# OkHttp & Coroutines
-keepattributes *Annotation*
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn kotlinx.coroutines.**
