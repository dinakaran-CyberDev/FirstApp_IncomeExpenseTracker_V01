FirstApp_Tracker_v01
--------------------

This is a minimal Android (Jetpack Compose) project for the Income/Expense tracker.
It is configured to build a Debug APK using Codemagic or a system Gradle installation.

How to use:
1. Upload this folder as the repository root (replace existing files).
2. If using Codemagic, the included codemagic.yaml will build the debug APK automatically.
3. After build, download from Artifacts: app/build/outputs/apk/debug/app-debug.apk

Notes:
- This project intentionally does not include gradle wrapper files (gradlew). Codemagic will use system gradle.
- If you prefer local Android Studio builds on your computer, you can generate Gradle wrapper by running `gradle wrapper` locally.
