# Contributing

Thanks for helping! The most valuable contributions are **working command sets for devices** and **bug reports with
a command log**.

## Reporting a problem

1. Reproduce it, then open *Settings → Diagnostics* and *Settings → Command log → Copy log*.
2. Open a [bug report](https://github.com/Foxlape/GhostMode/issues/new?template=bug_report.yml) with your device,
   firmware, carrier, preset and the log.

## Proposing a preset

Create a custom preset in the app, test it (call yourself, check mobile data, turn off and check that VoLTE comes
back), export it and attach the JSON to a
[preset request](https://github.com/Foxlape/GhostMode/issues/new?template=preset_request.yml).

## Code changes

```bash
git clone https://github.com/Foxlape/GhostMode.git
cd GhostMode
./gradlew testDebugUnitTest assembleDebug
```

- Kotlin official code style, Jetpack Compose + Material 3.
- Every mode change goes through `system/GhostActions` → `domain/GhostModeController`; do not run shell commands
  from UI code.
- Anything the ON path changes must be restorable. The controller restores `settings put` keys and packages disabled
  with `pm disable-user` automatically; other commands need an explicit OFF counterpart.
- Add a unit test for controller or repository changes (`app/src/test`). `FakeShell` in `GhostModeControllerTest`
  lets you script command output.
- User-facing text goes to `values/strings.xml` **and** `values-ru/strings.xml`; `StringsLocalizationTest` checks both.
- UI changes: regenerate screenshots with
  `./gradlew testDebugUnitTest -Pscreenshots --tests '*ScreenshotTest*'` and look at the PNGs.
- Add a line to `CHANGELOG.md` under *Unreleased*.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`fix:`, `feat:`, `docs:`, …) and
describe *what changed and why*.

## Releasing (maintainers)

1. Bump `versionCode` (`major × 10000 + minor × 100 + patch`) and `versionName` in `app/build.gradle.kts`.
2. Move *Unreleased* in `CHANGELOG.md` to the new version; add `fastlane/metadata/android/{en-US,ru-RU}/changelogs/<versionCode>.txt`.
3. Tag `vX.Y.Z` and push the tag — the release workflow builds, signs and publishes the APK with the changelog.

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
