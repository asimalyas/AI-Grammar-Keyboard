# AI Grammar Keyboard

AI Grammar Keyboard is a custom Android keyboard built with Kotlin and `InputMethodService`. It lets you type directly inside WhatsApp or another app, improve the current draft with Groq or Gemini, and replace the draft text in-place.

## Add API keys

Copy `local.properties.example` to `local.properties` in the project root:

```properties
GROQ_API_KEY=your_groq_key_here
GEMINI_API_KEY=your_gemini_key_here
AI_PROVIDER=groq
```

`AI_PROVIDER` can be `groq` or `gemini`. Groq is the default. Real API keys should only go in `local.properties` or GitHub Actions secrets, never in source code. `local.properties` is ignored by git.

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
3. Add these repository secrets:
   - `GROQ_API_KEY`
   - `GEMINI_API_KEY`
   - `AI_PROVIDER`
4. Set `AI_PROVIDER` to `groq` unless you want Gemini.
5. Go to the Actions tab.
6. Run the "Build Android APK" workflow.
7. Download the uploaded `ai-grammar-keyboard-debug-apk` artifact.

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

Android will show a warning for any third-party keyboard. This project only sends text to the selected AI provider when you tap one of the AI buttons.

## Use in WhatsApp

1. Open WhatsApp and tap a message field.
2. Switch to "AI Grammar Keyboard" from the keyboard picker.
3. Type normally using the dark keyboard.
4. Tap `Fix`, `Pro`, or `Simple` to rewrite the current draft.
5. English stays English, Urdu script stays Urdu script, and Roman Urdu stays Roman Urdu.
6. Review the updated draft in WhatsApp.
7. Tap `Send` to send, or tap `Switch` to return to Samsung Keyboard.