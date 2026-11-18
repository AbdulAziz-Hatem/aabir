# Minimal R8 rules: CameraX, ML Kit, DataStore, and OkHttp ship their own consumer rules.
-keepattributes *Annotation*

-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn kotlinx.coroutines.**
