# ScrollLock

Distraction blocker, Deep Focus shield, Reminders & Alarm tasks, CameraX Study Page-Turn counter, and on-device ML Kit AI Workout coach for Android (Kotlin + Jetpack Compose + Material 3).

## Required Project Files for Building

- `.github/workflows/build-apk.yml` — Automated GitHub Actions APK build workflow
- `app/` — Android module (`build.gradle.kts`, `proguard-rules.pro`, `src/`)
- `gradle/` — Version catalog (`libs.versions.toml`) and Gradle wrapper config (`9.3.1`)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` — Root Gradle configuration
- `debug.keystore.base64` — Encoded debug signing keystore
- `.env.example`, `.gitignore` — Environment template and Git ignore rules

## Build APK Automatically (GitHub Actions)

1. Push all files above to your GitHub repository.
2. Open the **Actions** tab on GitHub and select **Build ScrollLock APK**.
3. Once complete, download the **`ScrollLock-debug-apk`** artifact (`app-debug.apk`).

## Build Locally (Android Studio or CLI)

```bash
base64 -d debug.keystore.base64 > debug.keystore
gradle :app:assembleDebug
```

Output APK path: `app/build/outputs/apk/debug/app-debug.apk`.
