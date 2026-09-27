"""Builds GitHub release notes for a version.

Usage: release_notes.py <versionName> <versionCode> <tag> <repository>

English notes come from the matching CHANGELOG.md section, Russian notes from the fastlane
changelog of the version code. Hard-wrapped lines are joined because GitHub renders every
newline in release notes as a line break.
"""
import re
import sys
from pathlib import Path

CERT = "FB:2A:E9:C4:80:BB:0F:04:55:65:F7:B5:CA:BF:01:7D:98:18:21:A9:33:F0:78:53:DD:47:12:28:D5:71:B0:50"


def changelog_section(version: str) -> str:
    lines = Path("CHANGELOG.md").read_text(encoding="utf-8").splitlines()
    out, inside = [], False
    for line in lines:
        if line.startswith("## ["):
            inside = line.startswith(f"## [{version}]")
            continue
        if inside:
            out.append(line)
    return "\n".join(out).strip()


def unwrap(text: str) -> str:
    """Joins continuation lines of paragraphs and list items."""
    result: list[str] = []
    for line in text.splitlines():
        is_continuation = (
            result
            and result[-1].strip()
            and line.strip()
            and not re.match(r"\s*([-*#>|<]|\d+\.)", line)
            and not result[-1].lstrip().startswith(("#", "|", "<"))
        )
        if is_continuation:
            result[-1] = result[-1].rstrip() + " " + line.strip()
        else:
            result.append(line)
    return "\n".join(result)


def main() -> None:
    version, code, tag, repository = sys.argv[1:5]
    parts = [unwrap(changelog_section(version))]
    ru = Path(f"fastlane/metadata/android/ru-RU/changelogs/{code}.txt")
    if ru.exists():
        parts.append(f"<details><summary>🇷🇺 На русском</summary>\n\n{ru.read_text(encoding='utf-8').strip()}\n\n</details>")
    parts.append(
        "---\n"
        f"**Verify:** `apksigner verify --print-certs` must show SHA-256 `{CERT}`; "
        "checksums are in `SHA256SUMS.txt`.\n\n"
        f"Full changelog: [CHANGELOG.md](https://github.com/{repository}/blob/{tag}/CHANGELOG.md)"
    )
    Path("notes.md").write_text("\n\n".join(parts) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
