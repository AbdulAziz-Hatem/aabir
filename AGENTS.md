# NetPass Developer & AI Agent Guidelines

NetPass is a sovereign, zero-telemetry, offline-first Android application designed to scan Wi-Fi vouchers and log in to MikroTik Hotspot captive portals, with strict WCAG 2.2 AAA accessibility for screen reader users.

## 1. Workspace & Directory Structure
- The Android project root is `~/workspace/netpass`.
- All Gradle commands must be executed inside `netpass/` (`cd netpass && ./gradlew ...`).
- App module is at `netpass/app/`.
- Root workspace (`~/workspace`) contains build artifacts, APK extractions, and decompiled inspection directories.

## 2. Toolchain & Prerequisites
- **JDK:** Java 21 (Temurin / Zulu / OpenJDK 21 LTS).
- **Android SDK:** `compileSdk 37`, `targetSdk 35`, `minSdk 24`.
- **Build System:** Gradle 9.6.0, Android Gradle Plugin (AGP) 9.5.1, Kotlin Compose 2.4.21.
- **UI Framework:** Jetpack Compose BOM 2026.09.00 with Material 3 (M3).

## 3. Product Flavors
Two flavors exist under the `edition` dimension:
- `full`: Bundled offline ML Kit (`com.google.mlkit:text-recognition:16.0.1`), zero dependencies on Google Play Services, larger APK (~21MB).
- `lite`: Unbundled ML Kit (`play-services-mlkit-text-recognition:19.0.2`), requires Google Play Services to download model, minimal APK (~2.7MB).
- Target flavor-specific dependencies using `fullImplementation` and `liteImplementation`.

## 4. Build & Test Commands
Run all commands inside `netpass/`:

```bash
# Clean project
./gradlew clean

# Run all unit tests
./gradlew testFullDebugUnitTest testLiteDebugUnitTest --stacktrace

# Run a single test class
./gradlew testFullDebugUnitTest --tests "com.abdulaziz.netpass.VoucherValidationTest"

# Run a single unit test method
./gradlew testFullDebugUnitTest --tests "com.abdulaziz.netpass.VoucherValidationTest.normalizeToAsciiDigits_convertsArabicAndPersianDigitsCorrectly"

# Assemble Debug APKs
./gradlew assembleFullDebug
./gradlew assembleLiteDebug

# Assemble Release APKs (Requires signing env vars: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEYSTORE_KEY_ALIAS, KEYSTORE_KEY_PASSWORD)
./gradlew assembleFullRelease assembleLiteRelease
```

## 5. Architectural Non-Negotiables
- **Accessibility by Design (WCAG 2.2 AAA):**
  - Minimum touch target for any interactive element: 48x48dp.
  - Screen reader first: support TalkBack and Jieshuo.
  - Toggleable switch rows must use `Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { ... })` on the container and `onCheckedChange = null` on the child `Switch`. Do NOT attach manual `clickable`, manual `stateDescription`, or duplicate `contentDescription` on the row.
  - Compose semantics must merge descendants where appropriate (`Modifier.semantics(mergeDescendants = true)`) and provide unambiguous content descriptions.
  - About and Settings cards must be structured, technical, and concise; avoid conversational or poetic prose in string resources.
- **Synthetic Audio & Haptics (Zero Asset Audio):**
  - Audio cues (radar ticks, success chords, error tones) are mathematically synthesized via pure sine wave PCM buffers directly into static `AudioTrack` objects.
  - Do NOT bundle static `.mp3` or `.wav` media files into resources.
  - Serialize all audio operations onto the dedicated single-thread dispatcher to avoid audio track race conditions.
- **Hotspot Networking:**
  - Route discovery reads the actual gateway from `ConnectivityManager.getLinkProperties()`, with legacy DHCP fallback.
  - HTTP traffic to local gateways uses cleartext (enabled in `network_security_config.xml`).
  - Network operations must bind to the Wi-Fi network interface to bypass captive portal system intercept traps.
- **Camera & OCR Processing:**
  - Limit inference rate to 10 FPS to protect battery and thermals.
  - Candidate scoring algorithm penalizes calendar years and phone numbers; prioritizes isolated, centered digit strings matching the configured length window (specifically favoring 10, 12, and 14 digits).
  - Serial numbers starting with `0000` or preceded by `SN:` / `Barcode:` are strictly disqualified.
- **ProGuard / R8 Rules:**
  - Under AGP 9+ R8 Full Mode, keep `ComponentRegistrar` zero-arg constructors for ML Kit reflection (`app/proguard-rules.pro`). Never remove this keep rule.
- **Privacy Policy:**
  - Strict zero-telemetry: no Firebase Analytics, Crashlytics, third-party ads, or external cloud telemetry. All image and network processing is strictly on-device.
