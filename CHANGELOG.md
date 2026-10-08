# Changelog

All notable changes to Ghost Mode are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow [Semantic Versioning](https://semver.org/).
Russian release notes for every version are published on the [Releases](https://github.com/Foxlape/GhostMode/releases) page.

## [Unreleased]

### Fixed

- **"On" while calls still came through ([#6](https://github.com/Foxlape/GhostMode/issues/6)).** IMS packages are
  disabled with `|| true`, so when the system refused, the app still reported success. Ghost Mode now checks the
  result and warns that the IMS service is still active and VoLTE / Wi-Fi Calling calls may get through.
- **Samsung One UI preset** also disables the IMS service package the system actually uses, in case newer One UI
  releases ship it under a different name.

## [0.2.0] — 2026-09-27

A rework of the core and a new interface. The main goal of this release is that turning Ghost Mode **off** always
returns the phone to exactly the state it was in before — whatever happens in between.

### Fixed

- **Restore after switching preset or SIM.** Turning off used the preset and SIM mode selected *at that moment*.
  Switching them while the mode was on ran the wrong restore commands — for example, Samsung IMS packages stayed
  disabled. The app now records what it applied and reverts exactly that.
- **Universal preset left VoLTE broken.** It disabled the detected IMS service (e.g. `com.shannon.imsservice` on
  Pixel) but never re-enabled it. Disabled packages are now tracked and re-enabled on turn-off; packages you had
  disabled yourself stay untouched. Diagnostics offer a one-tap repair for packages left disabled by older versions.
- **`preferred_network_mode` restored to `0`.** Samsung and Android 9–11 presets wrote `0` (GSM/WCDMA only, no LTE)
  on turn-off. Every `settings put` key is now restored to its original value (or deleted if it did not exist); the
  same applies to the Samsung VoLTE switches, which were always forced back on.
- **Stale network mask.** The original mask was captured once and reused forever. It is now captured on every
  activation; an LTE-only reading is never taken as the original. Android builds that print network type names
  instead of a bitmask are parsed correctly instead of falling back to "all networks".
- **Reboot handling.** After a reboot the app reset its state to "off", although most changes (mask, settings,
  disabled packages) survive a reboot — the phone could stay LTE-only or unreachable without the app knowing. Now the
  mode is re-applied automatically when root or Sui is available; otherwise a notification offers *Re-apply* or
  *Turn off*.
- **Schedule, timer, widget, notification action and shortcuts** failed silently on Shizuku when the app process was
  not running, because the Shizuku binder arrives asynchronously. They now wait for it.
- **One shell connection for the whole app.** Every component created its own Shizuku connection; when one of them
  unbound, it killed the service used by the others. Toggles from the tile and the app could also run at the same
  time. Everything now shares one connection and one lock.
- **Shizuku restarts.** After Shizuku was restarted, all commands timed out until the app was restarted. A race while
  binding the service could also cause 10-second timeouts.
- **Root command timeout** was never enforced: output was read before waiting, so a hanging command blocked forever.
- **Auto turn-off timer** was not cancelled by a manual turn-off and could switch off a later session.
- **Launcher shortcuts** ran again on every screen rotation, theme or language change.
- **Leaving the app during a toggle** could cancel the command sequence halfway.
- **Update check never worked** — the `INTERNET` permission was missing.
- **English UI showed Russian texts** for built-in presets, log notes and import messages.
- *About* showed a stale version number; duration units were not translated.
- Diagnostics always reported mobile data as active — it is now actually checked.
- Statistics ignored sessions crossing midnight or the 7-day boundary.
- The chosen language was lost after a restart on Android 12 and older.
- White flash on start in the dark theme; missing edge-to-edge layout before Android 15.
- An unexpected error in the schedule receiver could crash the app.
- Removed the empty "Buy me a coffee" dialog.
- "Both SIMs" on a single-SIM phone ran every slot-specific command against the empty slot and reported errors;
  slots without a ready SIM card are now skipped.
- "Add Quick Settings tile" did nothing when the tile was already added or the firmware does not support the
  request; the result is now shown, with manual instructions when needed.

### Added

- **New interface.** Three tabs (Home, Presets, Settings); a large animated switch; live status (active time,
  preset, SIM); auto turn-off timer on the home screen; quick tiles for SIM, schedule, statistics and diagnostics;
  alert cards only when something needs attention (access, reboot, exact alarms, battery, update).
- Presets: every command is visible; placeholders `{{SAVED_MASK}}`, `{{IMS_PACKAGES}}` and `-s 0` are documented in
  the editor.
- Diagnostics per SIM: network types, bound IMS service, mobile data, disabled IMS packages with a repair action.
- Statistics: 7-day chart and recent sessions.
- Command log: full-screen, copy the whole log for bug reports.
- Dynamic colors (Android 12+), redesigned widget, launcher icon and Quick Settings tile states.
- Fastlane metadata for F-Droid / IzzyOnDroid ([#3](https://github.com/Foxlape/GhostMode/issues/3)).

### Changed

- The update check is **opt-in** and off by default; without it the app never accesses the network.
- *Download Shizuku* opens the latest release on GitHub instead of Google Play, which is missing or disabled on many
  phones.
- The preset and SIM selection are locked while the mode is on.
- Importing presets shows every command and requires confirmation — imported commands run with shell/root rights.
- The Quick Settings tile asks to unlock the device first on a secure lock screen.
- A partial failure is reported ("some commands failed") with a shortcut to the log.
- The dependency-metadata block is no longer embedded in the APK; app data is excluded from backups and device
  transfer (it is device-specific modem state).
- `versionCode` scheme is now `major × 10000 + minor × 100 + patch` (0.2.0 → 200).

## [0.1.16] — 2026-08-30

### Changed
- Samsung One UI preset: turns off `volte_vt_enabled` and `enhanced_4g_mode_enabled` and sends
  `CARRIER_CONFIG_CHANGED` so the modem drops VoLTE registration without a reboot. Both keys are set back to `1` on
  turn-off.

## [0.1.15] — 2026-08-29

### Added
- Diagnostics dump `volte`/`ims` keys from the `secure`, `global` and `system` settings tables.

### Changed
- Samsung One UI preset description mentions that some models drop VoLTE registration only after a reboot.

## [0.1.14] — 2026-08-28

### Added
- Diagnostics list installed IMS packages (`pm list packages | grep -i ims`).

### Removed
- `com.sec.ims` from the Samsung preset — the package does not exist on the S24 Ultra.

## [0.1.13] — 2026-08-26

### Changed
- Samsung One UI preset disables both Samsung IMS stacks (`com.sec.imsservice` and
  `com.samsung.android.imsservice`); missing packages no longer fail the preset.

## [0.1.12] — 2026-08-25

### Fixed
- Diagnostics read the real modem state instead of assuming "blocked" while the mode was on.

### Changed
- Samsung One UI preset for S24-class devices: IMS disabled at the telephony level, LTE-only mask and
  `preferred_network_mode` LTE-only for every subscription.

## [0.1.11] — 2026-08-21

### Added
- Update check against GitHub Releases.
- Theme selection: system, dark, light.
- Prompt to disable battery optimization so the schedule and timer fire on time.
- Auto turn-off timer: 30 min, 1 h, 2 h, until morning.
- Launcher shortcuts: turn on, turn off, turn on for 1 hour.

## [0.1.10] — 2026-08-21

### Security
- Signing passwords removed from the repository; release signing reads `keystore.properties` or environment variables.
  The signing key itself did not change.

### Fixed
- Shell commands (`su`, `sh`, Shizuku) time out instead of hanging the app in the "running" state.
- Shizuku user service reconnects after a disconnect; binding times out after 10 s.
- Simultaneous toggles from the tile, widget and schedule no longer corrupt the state.
- The mode is reported as off only when restore commands actually succeeded.
- Statistics no longer show negative durations while the mode is on.

## [0.1.9] — 2026-08-21

### Added
- Dual SIM: block calls on SIM 1, SIM 2 or both. Network masks are saved and restored per SIM.

## [0.1.8] — 2026-08-20

### Fixed
- Quick Settings tile did not update when the mode was toggled in the app.

### Added
- Tile subtitle with the current state (Android 10+).

## [0.1.7] — 2026-08-20

### Fixed
- State is synchronised between the tile, widget, app, schedule and notification.
- The status notification can no longer be swiped away while the mode is on.

## [0.1.6] — 2026-08-20

### Fixed
- Tile and widget clicks were dropped before the root backend finished initialising.
- Status notification on Android 13+: runtime `POST_NOTIFICATIONS` request; the foreground service was replaced by a
  plain notification with a "Turn off" action.

### Added
- Unit tests for the controller, shell executor and schedule logic.

## [0.1.5] — 2026-08-20

### Fixed
- Shizuku forks (Sui, Nightzuku and others) were reported as "not installed"
  ([#1](https://github.com/Foxlape/GhostMode/issues/1)).
- Toggling from the tile and widget with root on OxygenOS, HyperOS and One UI.
- The mode was shown as on after a reboot when no schedule was set.

## [0.1.4] — 2026-08-18

### Added
- Menu action to add the Quick Settings tile (Android 13+).

## [0.1.3] — 2026-08-18

### Changed
- Diagnostics show a readable summary (calls, mobile data, network mode, IMS) instead of raw command output.

## [0.1.2] — 2026-08-18

### Changed
- Navigation drawer replaced with a top-bar menu; presets shown as a grid or a list.

### Fixed
- Status notification appeared when the mode was off.
- Long-pressing the Quick Settings tile opens the app.

### Security
- Pinned GitHub Actions to commit SHAs; app data backup disabled.

## [0.1.1] — 2026-08-17

### Added
- Separate screens for diagnostics, command log and settings (navigation drawer).
- Link to download Shizuku when it is not installed.

## [0.1.0] — 2026-08-17

First public release: Shizuku and root backends, presets for Pixel / AOSP, Xiaomi, Samsung, OnePlus, vivo and
Android 9–11, custom presets with JSON import/export, Quick Settings tile, home screen widget, daily schedule,
usage statistics, English and Russian UI.

[0.2.0]: https://github.com/Foxlape/GhostMode/compare/v0.1.16...v0.2.0
[0.1.16]: https://github.com/Foxlape/GhostMode/compare/v0.1.15...v0.1.16
[0.1.15]: https://github.com/Foxlape/GhostMode/compare/v0.1.14...v0.1.15
[0.1.14]: https://github.com/Foxlape/GhostMode/compare/v0.1.13...v0.1.14
[0.1.13]: https://github.com/Foxlape/GhostMode/compare/v0.1.12...v0.1.13
[0.1.12]: https://github.com/Foxlape/GhostMode/compare/v0.1.11...v0.1.12
[0.1.11]: https://github.com/Foxlape/GhostMode/compare/v0.1.10...v0.1.11
[0.1.10]: https://github.com/Foxlape/GhostMode/compare/v0.1.9...v0.1.10
[0.1.9]: https://github.com/Foxlape/GhostMode/compare/v0.1.8...v0.1.9
[0.1.8]: https://github.com/Foxlape/GhostMode/compare/v0.1.7...v0.1.8
[0.1.7]: https://github.com/Foxlape/GhostMode/compare/v0.1.6...v0.1.7
[0.1.6]: https://github.com/Foxlape/GhostMode/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/Foxlape/GhostMode/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/Foxlape/GhostMode/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/Foxlape/GhostMode/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/Foxlape/GhostMode/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Foxlape/GhostMode/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Foxlape/GhostMode/releases/tag/v0.1.0
