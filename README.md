# AI Grammar Keyboard

AI Grammar Keyboard is a custom Android keyboard built with Kotlin and `InputMethodService`. It lets you type directly inside WhatsApp or another app, improve the current draft with Gemini, and replace the draft text in-place.

## Add Gemini API key

Copy `local.properties.example` to `local.properties` in the project root:

```properties
GEMINI_API_KEY=your_gemini_key_here
```

Real API keys should only go in `local.properties` or GitHub Actions secrets, never in source code. `local.properties` is ignored by git.

## Build APK without Android Studio

Windows:

```powershell
.\gradlew.bat assembleDebug
```

Linux or Mac:

```bash
./gradlew assembleDebug
```

The debug APK is created here:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The first build downloads Gradle and Android dependencies. You need a JDK and Android SDK installed locally. If you do not have the Android SDK, use the included GitHub Actions workflow.

## Build online with GitHub Actions

1. Push this project to GitHub.
2. Open the repository settings.
3. Add this repository secret:
   - `GEMINI_API_KEY`
4. Go to the Actions tab.
5. Run the "Build Android APK" workflow.
6. Download the uploaded `ai-grammar-keyboard-debug-apk` artifact.

## Install APK on your phone

Using ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Without ADB, transfer the APK to your phone, open it, and allow installation from that source if Android asks.

## Enable the keyboard

On your Android phone:

```text
Settings -> System / General Management -> Keyboard -> On-screen keyboard -> Manage keyboards -> Enable AI Grammar Keyboard
```

Android will show a warning for any third-party keyboard. This project only sends text to Gemini when you tap one of the AI buttons.

## Use in WhatsApp

1. Open WhatsApp and tap a message field.
2. Switch to "AI Grammar Keyboard" from the keyboard picker.
3. Type normally using the dark keyboard.
4. Tap `Fix`, `Pro`, or `Simple` to rewrite the current draft.
5. Review the updated draft in WhatsApp.
6. Tap `Send` to send, or tap `Switch` to return to Samsung Keyboard.