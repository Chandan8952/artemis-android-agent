# ⚡ Artemis Android Agent (Advanced Mode)

An autonomous on-device Android Agent inspired by **Google ARTEMIS**, packaged as a native standalone Android App (APK).

It operates real Android smartphones autonomously using **Multimodal Vision-Language Models (Google Gemini 2.0 Flash)**, Android **Accessibility Services**, and on-device **Shizuku ADB** capabilities.

---

## 🌟 Key Features

1. **Floating Autonomous HUD**:
   * A draggable floating bubble / terminal overlay that stays on top of any app.
   * Send natural language commands anytime (e.g. *"Open YouTube, search Coldplay and play the first video"* or *"Open Settings, find Battery and check current level"*).
   * Live streaming of the AI's step-by-step reasoning and action trajectory.

2. **Multimodal VLM Perception**:
   * Captures screen frames on the fly using `MediaProjection` and Android 11+ `takeScreenshot`.
   * Parses visible accessibility nodes (`UiNode`) into a token-efficient format to ground visual targets accurately.
   * Connects directly to **Google Gemini 2.0 Flash** via secure HTTPS.

3. **Advanced Mode (Shizuku On-Device ADB)**:
   * **No PC or USB cable required!**
   * Uses [Shizuku](https://shizuku.rikka.app) to execute `adb shell` commands, silent app installation (`pm install`), force stopping apps (`am force-stop`), and real-time crash log inspections (`logcat`).
   * Falls back gracefully to **Standard Mode** (pure Accessibility gestures) if Shizuku is not running.

---

## 🛠️ Project Architecture

```
artemis-android-agent/
├── app/
│   ├── build.gradle.kts                   # Dependencies (Compose, Shizuku, OkHttp, Coroutines)
│   └── src/main/
│       ├── AndroidManifest.xml            # Service declarations and system permissions
│       ├── res/
│       │   └── xml/accessibility_service_config.xml # Gestures & hierarchy retrieval flags
│       └── java/com/artemis/agent/
│           ├── accessibility/
│           │   └── ArtemisAccessibilityService.kt # Taps, Swipes, Text input, Global nav
│           ├── ai/
│           │   └── GeminiVlmClient.kt             # Gemini 2.0 Flash prompt & JSON action parser
│           ├── engine/
│           │   └── ArtemisAgentEngine.kt          # Autonomous loop & action dispatcher
│           ├── model/
│           │   ├── AgentAction.kt                 # Action definitions (UI + ADB)
│           │   ├── AgentState.kt                  # Reactive execution states
│           │   └── UiNode.kt                      # Compressed UI element representation
│           ├── screen/
│           │   └── ScreenCaptureManager.kt        # Live screen frame capture
│           ├── service/
│           │   └── FloatingOverlayService.kt      # Floating draggable HUD over other apps
│           ├── shizuku/
│           │   └── ShizukuManager.kt              # On-device ADB shell controller
│           └── ui/
│               ├── MainActivity.kt                # Permission setup wizard & toggle
│               ├── MainScreen.kt                  # Material3 Compose dashboard
│               └── theme/Theme.kt                 # Dark futuristic HUD styling
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 How to Build the APK

### Method 1: Android Studio (Recommended)
1. Copy or transfer this folder to your PC/Mac or open it in **Android Studio**:
   ```
   artemis-android-agent
   ```
2. Wait for Gradle Sync to complete.
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The generated APK will be in:
   `app/build/outputs/apk/debug/app-debug.apk`

### Method 2: Command Line (Gradle)
Run in the project directory:
```bash
./gradlew assembleDebug
```

---

## 📱 How to Use on Your Phone

1. **Install the APK** on your Android device.
2. **Open Artemis Agent**:
   * Enter your free **Gemini API Key** (from [Google AI Studio](https://aistudio.google.com/)).
   * Tap **Grant** on **Floating Overlay** (allows drawing over other apps).
   * Tap **Enable** on **Accessibility Service** (enables taps and swipes).
   * Tap **Initialize** on **Screen Capture** (allows the agent to see your screen).
   * *(Optional for Advanced Mode)*: Open the [Shizuku](https://shizuku.rikka.app) app, start it via Wireless Debugging, and tap **Grant** in Artemis Agent.
3. Tap **Start Floating Agent**.
4. The floating Artemis bubble will appear. Tap it, type any instruction, and watch your phone execute it autonomously!
