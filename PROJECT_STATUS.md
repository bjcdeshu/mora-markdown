# Mora Project Status

Updated: 2026-07-24

## Current stage

Mora v0.2.0 is the first public-release target and remains unreleased. This local-development baseline starts from `main` commit `92e5382`.

The current goal is a reproducible debug build and a passing pull-request CI run. A public release still requires explicit maintainer approval and real-device validation.

`docs/PRODUCT_SPEC.md` is an early exploration document, not the current implementation checklist. In particular, its CodeMirror and single-WebView proposal does not describe the current Compose source editor plus reader WebView architecture. Current code, `ROADMAP.md`, and this status document take precedence.

## Product principles

- Quiet, local-first, and reading-first
- Standard Markdown remains owned and portable
- Comfortable defaults with light editing close at hand
- Reliable file opening, permission handling, saving, and reading continuity
- No vault, backlinks, graph, account, built-in cloud sync, or plugin platform

## Toolchain baseline

| Component | Baseline |
|---|---|
| Android Studio | Quail 2 (2026.1.2.10), stable channel |
| JVM | Bundled JBR 21 locally; JDK 17 in CI |
| Android Gradle Plugin | 9.3.0 |
| Gradle Wrapper | 9.5.0 |
| Compile / target SDK | Android 16 / API 36 |
| Minimum SDK | API 26 / Android 8.0 |
| Build Tools | 36.0.0 |

The repository uses the checked-in Gradle Wrapper. A separate Gradle installation is not required.

## Validation gate

Run from the repository root on Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The same tasks must pass in GitHub Actions for a pull request targeting `main`. The expected local artifact is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Changes involving file providers, external intents, saving, layout, or accessibility also require focused emulator or real-device checks.

Last local verification on 2026-07-24 used Android Studio's bundled JBR 21 and passed all three tasks. The JVM suite ran 3 tests with no failures; lint completed with 0 errors and 17 non-blocking follow-up warnings. No lint baseline or suppression was introduced.

## Release boundary

The repository currently has no tag, GitHub Release, long-term signing key, or automatic release workflow. Until the maintainer approves the release phase:

- Build and share debug APKs only.
- Do not generate or configure long-term signing material.
- Do not create a tag or Release.
- Do not merge the initialization pull request into `main`.

Before the first public release, validate the candidate APK on representative real devices, configure recoverably backed-up long-term signing, review release notes, and obtain maintainer approval.
