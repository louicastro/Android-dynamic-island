# Build & Compilation Guide

This guide explains how to compile the **Dynamic Island** open-source Android application from source into an APK.

---

## 1. Prerequisites

Before building the project, ensure you have the following installed:
- **Java Development Kit (JDK)**: Version 17 or Version 21 (OpenJDK recommended).
- **Android SDK**:
  - `compileSdk`: **35** (Android 15)
  - `minSdk`: **26** (Android 8.0 Oreo)
  - `targetSdk`: **35**
- **Gradle**: Version 8.7 (managed automatically via the included Gradle wrapper)
- **Android Studio**: Koala, Ladybug, or Iguana (optional, for IDE development)

---

## 2. Building via Android Studio (Recommended for GUI)

1. Launch Android Studio.
2. Select **File -> Open...** and choose the `apple dynamic island` directory.
3. Android Studio will automatically recognize the project structure and invoke Gradle sync.
4. Once indexing finishes, select **Build -> Make Project** (or press `Ctrl+F9` / `Cmd+F9`).
5. To generate an APK:
   - Click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
   - The compiled debug APK will be placed at:
     ```
     app/build/outputs/apk/debug/app-debug.apk
     ```
6. Connect an Android device (via USB with USB debugging enabled or Wi-Fi debugging) and click the green **Run** button (or `Shift+F10`).

---

## 3. Building via Command Line (Terminal / CI)

### Step 1: Ensure JDK 17+ is active
Verify your Java version:
```bash
java -version
```
If `JAVA_HOME` is not set:
```bash
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH
```

### Step 2: Ensure Android SDK is configured
If building in a standalone terminal without Android Studio, set `ANDROID_HOME`:
```bash
export ANDROID_HOME=/home/your-user/Android/Sdk
```
Or create a `local.properties` file in the root directory:
```properties
sdk.dir=/home/your-user/Android/Sdk
```

### Step 3: Run the Unit Tests
```bash
./gradlew test
```

### Step 4: Compile Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Install APK onto connected device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Building a Release APK

To create an optimized, release-ready APK:
```bash
./gradlew assembleRelease
```
The APK will be located at:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

To sign the APK with a custom keystore using `apksigner`:
```bash
apksigner sign --ks release.keystore --out app-release-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 5. Device Permissions Setup

When running the app on a physical Android device:
1. **Display over other apps**: The app will prompt to open system settings for `SYSTEM_ALERT_WINDOW`. Toggle on for Dynamic Island.
2. **Notification Listener Service**: Grants access to listen for music metadata and incoming chat notifications.
3. **Phone State Permission**: Enables call duration tracking and incoming phone alerts.
4. **Battery Optimization Exclusion**: Recommended to keep background services responsive during deep sleep (Doze mode).
