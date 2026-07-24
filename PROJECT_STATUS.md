# Mora Project Status

Updated: 2026-07-25

## Current stage

Mora `v0.3.0` (`versionCode` 4) is the first stable-release target and the current
release-preparation baseline. It supersedes the planned stable v0.2.0, which was
not published; signed `v0.2.0-rc.1` remains a historical public Pre-release.

The v0.3.0 implementation adds six focused improvements to the existing
single-document workflow:

- a subtle proportional right-edge reading-position thumb with direct dragging;
- English and Simplified Chinese UI, including Android 13+ per-app language access;
- separate Home-level application settings and Reader typography controls;
- system/light/dark appearance plus an optional pure-black dark surface;
- three selectable Fine Frame launcher palettes;
- predictive document-to-Home Back on supported Android versions.

No public v0.3.0 RC is planned. The release path deliberately validates two exact
assets: first the protected manual Actions artifact built from `main`, then the
new APK rebuilt behind the hidden stable-tag Draft. The same approved Draft
Release is finally edited to stable without moving its tag or replacing its
attachments.

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

The release-preparation work remains deliberately focused:

- complete Markdown text is no longer placed in the saved-state `Bundle`;
- document-provider metadata queries run off the main thread, and stale failed
  open requests cannot change recent-document state;
- the reader WebView uses an allowlisted, non-resolving HTTPS origin with all
  legacy file access and cleartext access disabled;
- Android cloud backup and device transfer explicitly exclude Mora's private
  recent-document metadata and preferences;
- TOC layout decisions use the actual window, not the physical screen size;
- user-visible interface text is resource-backed without translating document
  content, provider filenames, or Markdown source;
- launcher switching uses three stable component aliases and never intentionally
  disables every launcher entry;
- the Reader keeps its WebView security boundary while adding native progress
  drawing and pointer handling;
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

The v0.3.0 preparation branch's full local run on 2026-07-25 passed all three
tasks. The JVM suite ran 51 tests with no failures; lint completed with 0 errors
and 11 non-blocking follow-up warnings; the Debug APK assembled successfully. A
fresh clean run and the same green GitHub Actions gate remain required before
merge. No lint baseline or new suppression was introduced.

## Release boundary

Release signing material must stay outside Git and be backed up independently;
GitHub Secrets are delivery infrastructure, not a backup.

- Pull-request and `main` CI build only Debug APKs and never receive signing secrets.
- The first v0.3.0 signed candidate may be produced only from `main` through the
  protected release environment; its exact downloaded APK must pass identity
  checks and the complete device matrix.
- Only explicit maintainer approval of that exact candidate permits the annotated
  stable tag.
- The stable tag rebuilds a different candidate into a hidden Draft Pre-release.
  Its exact attachment must independently repeat identity checks and the complete
  device matrix.
- Only a second explicit maintainer approval permits editing that same Release to
  `draft=false`, `prerelease=false`, and latest. Do not move the tag or replace
  attachments.
- Future public RCs, if used, must remain labeled as Pre-releases and never weaken
  the stable gate; v0.3.0 does not use one.

The operational checklist and credential names are maintained in [docs/RELEASING.md](docs/RELEASING.md).
