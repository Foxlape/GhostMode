# Contributing to Ghost Mode

First off, thank you for considering contributing to Ghost Mode! 🎉

Whether you're reporting bugs, suggesting new vendor presets, improving documentation, or submitting code changes, your help is appreciated.

---

## 🛠️ How Can You Contribute?

### 1. Contributing New Device Presets
Different Android manufacturers and carrier configurations handle IMS services and network masks differently. If you discovered working shell commands for your device model:
1. Open a **[New Preset Request](https://github.com/foxlape/GhostMode/issues/new?template=preset_request.yml)**.
2. Provide your phone model, Android / ROM version, and the enable/disable commands.

### 2. Reporting Bugs
- Search existing issues before submitting a new one.
- Use the **[Bug Report Form](https://github.com/foxlape/GhostMode/issues/new?template=bug_report.yml)**.
- Include the exact output from the **Command Log** card inside the app.

### 3. Submitting Code / Pull Requests
1. Fork the repository and create your feature branch:
   ```bash
   git checkout -b feature/awesome-feature
   ```
2. Follow standard Kotlin / Compose coding conventions.
3. Verify that the project builds cleanly:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest
   ```
4. Commit your changes with clear, semantic commit messages.
5. Push to your branch and open a Pull Request.

---

## 🏛️ Codebase Structure

- `app/src/main/java/com/ghostmode/app/`
  - `shell/` — Shizuku & Root execution engines
  - `data/` — Presets, JSON parser, and state repository
  - `domain/` — Mode state machine and network mask handling
  - `scheduling/` — Exact alarm scheduling across reboots
  - `service/` — Ongoing notification foreground service
  - `ui/` — Jetpack Compose UI (Material 3)
  - `tile/` & `widget/` — Quick Settings tile and AppWidget provider

---

## 📜 Code of Conduct
Please follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all project interactions.
