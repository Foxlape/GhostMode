<div align="center">

# 👻 Ghost Mode

**Seamlessly turn your Android device "unavailable" for incoming cellular calls while keeping high-speed LTE / mobile data fully active.**

[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Shizuku](https://img.shields.io/badge/Shizuku-v10%2B-2196F3?style=for-the-badge)](https://shizuku.rikka.app)
[![Root](https://img.shields.io/badge/Root-KernelSU%20%7C%20Magisk%20%7C%20APatch-E91E63?style=for-the-badge)](https://github.com/tiann/KernelSU)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

[**🇷🇺 Читать документацию на русском языке**](README.ru.md)

</div>

---

## 🌟 Overview

**Ghost Mode** is an open-source Android utility that makes your phone appear as **"Subscriber unavailable" / "Out of service"** to all incoming cellular callers (just as if the phone were switched off), while **LTE / 5G mobile data continues to work at full speed**.

No more airplane mode, missed internet notifications, or clumsy call reject rules. You remain fully connected to the web, instant messengers, and cloud services without being disturbed by unsolicited calls.

### 💡 How It Works
1. **IMS / VoLTE Deregistration**: Disables telephony IMS registration (`cmd phone ims disable`), preventing VoLTE and VoWiFi calls from reaching the device.
2. **Strict LTE-Only Lock**: Locks the modem to LTE packet transmission (`cmd phone set-allowed-network-types-for-users -s 0 01000001000000000000`), disabling Circuit Switched Fallback (CSFB) to 2G/3G.
3. **Restoration**: When toggled off, your exact original network mask and IMS stack are cleanly restored.

All commands run via **Shizuku** (rootless shell via ADB UID 2000) or direct **Root** (`su` via KernelSU / Magisk / APatch).

---

## ✨ Features

- ⚡ **No Root Required**: Executes privileged telephony commands seamlessly via [Shizuku](https://shizuku.rikka.app) (with built-in 1-click download & setup guidance).
- 🔥 **Root Superuser Support**: Direct `su` backend with automatic privilege detection (KernelSU, Magisk, APatch) — works instantly after device reboot without relaunching Shizuku.
- 📱 **Multi-Vendor Presets**: Pre-configured profiles for Google Pixel (AOSP), Xiaomi (MIUI / HyperOS), Samsung (One UI), OnePlus (OxygenOS), vivo/iQOO (OriginOS / Funtouch), and legacy Android versions.
- 🗂️ **Sidebar Navigation Drawer**: Clean, fast drawer menu separating Dashboard, Diagnostics, Command Log, and Settings into dedicated screens.
- 🛠️ **Custom Preset Builder**: Build, duplicate, test, and save custom command sequences with dynamic network mask placeholders (`{{SAVED_MASK}}`).
- 📤 **Preset Export & Import**: Easily backup or share custom profiles in JSON format.
- 🎛️ **Quick Settings Tile**: Toggle Ghost Mode directly from your Android notification shade with one tap.
- 🧩 **Home Screen Widget**: Sleek interactive desktop widget for instant state switching.
- ⏰ **Automated Schedules**: Daily quiet hours (e.g. `23:00` → `08:00`) with persistent boot-aware exact alarms.
- 🔔 **Persistent Status Notification**: Optional active mode notification featuring an elapsed timer and direct one-click "Turn Off" action.
- 📊 **Usage Statistics**: Track total time in Ghost Mode, session history (up to 500 records), and usage trends (today, 7 days, all-time).
- 🌍 **Bilingual Interface**: Full English and Russian localization with instant runtime language switching.
- 🎨 **Modern Material 3 UI**: Clean adaptive design respecting system bars, landscape mode, and large tablet screens.

---

## 📋 Compatibility & Requirements

| Requirement | Minimum | Recommended |
|---|---|---|
| **Android OS** | Android 8.0 (API 26) | Android 12+ (API 31+) |
| **Privilege Provider** | [Shizuku v10+](https://shizuku.rikka.app) | KernelSU / Magisk / Shizuku |
| **Carrier** | VoLTE enabled | LTE / 5G coverage |

### 🏷️ Vendor Presets Matrix

| Preset | Target Systems | Core Strategy |
|---|---|---|
| **Universal (Auto-detect)** | Android 12+ (Default) | Automatically discovers device & carrier IMS package names and toggles them |
| **Stock / Pixel** | Google Pixel, Moto, Clean AOSP | `cmd phone ims disable` + LTE-only network mask lock |
| **Xiaomi MIUI / HyperOS** | Xiaomi, Redmi, POCO | AOSP telephony shell commands bypass hidden MIUI lockouts |
| **Samsung One UI** | Galaxy S / A / Z series | LTE-only lock + disables `com.sec.imsservice` user package |
| **OnePlus (OxygenOS)** | OnePlus 8-13 (Snapdragon / MTK) | AOSP commands + toggles Qualcomm/MediaTek IMS packages (`org.codeaurora.ims`) |
| **vivo / iQOO (OriginOS / Funtouch)** | vivo X/V series, iQOO | AOSP commands + handles SoC IMS services (`com.mediatek.ims`) |
| **Legacy Android (9–11)** | Android 9.0 – 11.0 | Global preferred network mode `11` + fast airplane toggle |

---

## 🚀 Quick Start Guide

### 1. Choose Your Execution Backend

#### Option A: Rootless via Shizuku (Recommended for non-rooted phones)
1. Install [Shizuku](https://shizuku.rikka.app) on your device.
2. Start the Shizuku service via **Wireless Debugging** (Android 11+) or via ADB from a PC:
   ```bash
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. Open Ghost Mode, tap **Grant Permission** on the backend status card, and authorize.

#### Option B: Direct Root (KernelSU / Magisk / APatch)
1. Grant root permissions to Ghost Mode in your superuser manager (KernelSU / Magisk).
2. Open Ghost Mode — root will be detected automatically (`su -c id`).
3. Root mode is completely persistent across device reboots.

### 2. Enable Ghost Mode
1. Select the preset matching your device (or keep **Universal**).
2. Toggle the main switch to **ON**.
3. Verify:
   - Call your number from another phone: should hear "Subscriber unavailable".
   - Open a browser on LTE (Wi-Fi off): web pages load fast without interruptions.

---

## 🔧 Building from Source

### Prerequisites
- JDK 17+
- Android SDK 35 (Platform tools & build-tools 35.0.0)

### Build Debug APK
```bash
# Clone the repository
git clone https://github.com/foxlape/GhostMode.git
cd GhostMode

# Build debug APK
./gradlew assembleDebug

# Output APK path: app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK
```bash
./gradlew assembleRelease
# Output APK path: app/build/outputs/apk/release/app-release.apk
```

---

## 📂 Project Architecture

```
GhostMode/
├── app/
│   ├── src/main/
│   │   ├── aidl/               # Shizuku UserService AIDL interface (IUserService.aidl)
│   │   ├── java/com/ghostmode/app/
│   │   │   ├── data/           # Preset models, JSON serializer, State Repository
│   │   │   ├── domain/         # GhostModeController state machine & mask parser
│   │   │   ├── scheduling/     # ScheduleManager & Boot/Time receiver
│   │   │   ├── service/        # Ongoing Status Notification Foreground Service
│   │   │   ├── shell/          # AutoShellExecutor, RootShell, ShizukuUserService
│   │   │   ├── tile/           # Quick Settings QS Tile Provider
│   │   │   ├── widget/         # Home Screen AppWidgetProvider
│   │   │   └── ui/             # Jetpack Compose UI (Cards, Dialogs, Material3 Theme)
│   │   └── res/                # Vector drawables, localization strings (EN, RU)
│   └── build.gradle.kts        # App build config (Kotlin 2.0, Compose BOM, SDK 35)
├── .github/
│   ├── workflows/              # CI build & automated GitHub Release workflows
│   └── ISSUE_TEMPLATE/         # Bug report & preset request templates
├── LICENSE                     # Apache-2.0 License
├── README.md                   # English Documentation
└── README.ru.md                # Russian Documentation
```

---

## ⚠️ Limitations & Disclaimers

> [!WARNING]
> **Emergency Calls**: Never rely on this mode in critical situations where emergency availability is essential. Always turn Ghost Mode off when urgent call reception is needed.

- **SMS Delivery**: On some cellular carriers, SMS text messages are routed through IMS. Disabling IMS might delay SMS until Ghost Mode is turned off.
- **Conditional Call Forwarding**: If you have active voicemail or carrier call forwarding set up for "When unreachable", callers may be redirected to your voicemail.
- **OnePlus Devices**: After disabling Ghost Mode on certain OxygenOS versions, a quick network toggle or restart might be required if VoLTE does not immediately re-register.
- **Responsibility**: Use this software strictly on your own hardware in accordance with local telecommunications regulations.

---

## 🤝 Contributing

Contributions are welcome!
- Found a command combination that works for a new phone vendor? Submit a **[Preset Request](https://github.com/foxlape/GhostMode/issues/new?template=preset_request.yml)**.
- Encountered a bug? File a **[Bug Report](https://github.com/foxlape/GhostMode/issues/new?template=bug_report.yml)**.
- Want to improve code or translations? Check out [CONTRIBUTING.md](CONTRIBUTING.md) and open a Pull Request!

---

## 📄 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.
