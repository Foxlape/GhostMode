# Security policy

## Supported versions

Only the latest release receives fixes.

| Version | Supported |
|---------|-----------|
| 0.2.x   | ✅        |
| < 0.2   | ❌        |

## Reporting a vulnerability

Ghost Mode runs shell commands with elevated privileges (Shizuku / root), so please report security issues
privately through a [GitHub security advisory](https://github.com/Foxlape/GhostMode/security/advisories/new) —
not in public issues.

Include the app version, device, Android version, access method (Shizuku / Sui / root manager) and steps to
reproduce.

## Scope notes

- Presets are arbitrary shell commands by design. Importing a preset shows every command before it is saved; only
  import presets from people you trust.
- The app has no network access unless the optional update check is enabled, which only queries the GitHub
  Releases API.
- Official APKs are signed with the key whose SHA-256 fingerprint is listed in the [README](README.md#install).
