# Mora Project Status

Updated: 2026-07-25

## Current stage

Mora `v0.2.0-rc.1` (`versionCode` 3) is the first public signed release
candidate. It includes the transient reading-position marker, status-bar-aware
immersive reading, the top-edge reading fade, and the approved Fine Frame launcher
icon. Stable `v0.2.0` remains the first stable-release target.

The release path has two deliberately different outcomes:

1. A SemVer `-rc.N` tag rebuilds and verifies a signed candidate, then publishes it
   as a clearly warned public GitHub Pre-release.
2. A stable tag rebuilds through the same path but creates a draft. Publishing that
   draft remains blocked until its exact attachment passes the real-device gate.

The first long-term RSA-2048 identity was generated outside the repository on
2026-07-24. Its encrypted keystore backups and independently stored credentials
passed restore checks. The protected `release-signing` GitHub Environment and the
repository's public certificate record are the automation boundary; signing
material remains outside Git.

`docs/PRODUCT_SPEC.md` is an early exploration document, not the current implementation checklist. In particular, its CodeMirror and single-WebView proposal does not describe the current Compose source editor plus reader WebView architecture. Current code, `ROADMAP.md`, and this status document take precedence.

## Product principles

- Quiet, local-first, and reading-first
- Standard Markdown remains owned and portable
- Comfortable defaults with light editing close at hand
- Reliable file opening, permission handling, saving, and reading continuity
- No vault, backlinks, graph, account, built-in cloud sync, or plugin platform

## Repository audit

The complete 2026-07-25 audit found that Mora's single `app` module and
`data / markdown / model / ui` package boundaries are appropriate for its current
size. No build output, signing secret, anomalously large file, duplicate source
tree, package mismatch, or case conflict is tracked. The Gradle Wrapper checksum,
Windows line-ending policy, public signing certificate record, and repository
documentation layout remain intentional.

The release-preparation cleanup is deliberately focused:

- complete Markdown text is no longer placed in the saved-state `Bundle`;
- document-provider metadata queries run off the main thread, and stale failed
  open requests cannot change recent-document state;
- the reader WebView uses an allowlisted, non-resolving HTTPS origin with all
  legacy file access and cleartext access disabled;
- Android cloud backup and device transfer explicitly exclude Mora's private
  recent-document metadata and preferences;
- TOC layout decisions use the actual window, not the physical screen size;
- CI actions are pinned to current Node 24-compatible commits and monitored
  monthly by Dependabot.

Large-scale modularization, dependency injection, navigation frameworks, and UI
package moves were rejected because they add release risk without improving the
single-document workflow.

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

The final clean local verification on 2026-07-25 used Android Studio's bundled JBR
21 and passed all three tasks. The JVM suite ran 9 tests with no failures; lint
completed with 0 errors and 12 non-blocking follow-up warnings. No lint baseline or
new suppression was introduced.

## Release boundary

Release signing material must stay outside Git and be backed up independently;
GitHub Secrets are delivery infrastructure, not a backup.

- Pull-request and `main` CI build only Debug APKs and never receive signing secrets.
- A signed candidate may be produced only from `main` through the protected release environment.
- The candidate APK, certificate fingerprint, package name, version, and SDK levels must all be verified before upload.
- A public RC must remain labeled as a Pre-release and state that its real-device
  matrix is pending.
- Stable publication remains blocked until the exact stable draft attachment
  repeats the device gate in `docs/RELEASING.md`.

The operational checklist and credential names are maintained in [docs/RELEASING.md](docs/RELEASING.md).
