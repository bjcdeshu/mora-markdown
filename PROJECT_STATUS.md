# Mora Project Status

Updated: 2026-07-24

## Current stage

Mora v0.2.0 is the first public-release target and remains unreleased. The stable local-development baseline was merged into `main` at commit `eaeba069e318fd2a9d1dedcf9eceb0e7401e66ed`.

The current phase is preparing recoverable long-term signing and a two-step release path:

1. A manual workflow on `main` builds and verifies a signed candidate without creating a tag or Release.
2. Only an explicitly approved `v*` tag can rebuild through the same verified path and create a draft pre-release.

A public release still requires real-device validation of the manually dispatched candidate, explicit approval to tag, and a second validation of the exact draft attachment before publication.

The first long-term RSA-2048 identity was generated outside the repository on 2026-07-24. The encrypted keystore copy and protected credential copy passed a local restore test, both passwords were independently stored and restored from a password manager without mismatch, the `release-signing` GitHub Environment is restricted to `main` and `v*` refs with maintainer review, and an active tag ruleset blocks deletion or non-fast-forward updates of `v*` tags. The signing backup gate is complete; the exact cloud-built candidate still requires the documented real-device matrix before any tag is created.

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

The repository still has no tag or GitHub Release. Release signing material must stay outside Git and be backed up independently; GitHub Secrets are delivery infrastructure, not a backup.

- Pull-request and `main` CI build only Debug APKs and never receive signing secrets.
- A signed candidate may be produced only from `main` through the protected release environment.
- The candidate APK, certificate fingerprint, package name, version, and SDK levels must all be verified before upload.
- Do not create a tag before the manually dispatched signed candidate passes the device matrix in `docs/RELEASING.md`.
- A tag workflow rebuilds and creates a draft pre-release. Its APK may not be byte-for-byte identical to the earlier candidate; publishing remains blocked until the exact draft attachment repeats the device gate.

The operational checklist and credential names are maintained in [docs/RELEASING.md](docs/RELEASING.md).
