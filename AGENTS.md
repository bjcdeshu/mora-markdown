# Mora Agent Guide

These instructions apply to the entire repository.

## Start here

Before changing code, read `README.md`, `ROADMAP.md`, `PROJECT_STATUS.md`, and the relevant build or source files. Inspect `git status` and the current diff first. Existing uncommitted work belongs to the user and must not be discarded or folded into an unrelated change.

`docs/PRODUCT_SPEC.md` contains early product and architecture exploration. When it differs from the implemented code, roadmap, or project status, treat those current sources as authoritative; do not implement the older proposal as a backlog by default.

## Product contract

Mora is a quiet, local-first, reading-first Android Markdown reader with light editing.

- Optimize the single-document flow: open, read, navigate, lightly edit, save, and resume.
- Keep standard Markdown files under the user's control.
- Preserve Android Storage Access Framework behavior and explicit WebView security boundaries.
- Prefer a calm, useful default experience over settings or feature volume.
- Do not expand Mora into a vault, backlink or graph system, account service, cloud-sync product, or plugin platform without explicit product approval.

## Engineering baseline

- Android Studio stable channel with its bundled JetBrains Runtime
- CI runs on JDK 17; the current local Android Studio bundles JBR 21
- Android Gradle Plugin 9.3 and Gradle Wrapper 9.5
- `compileSdk = 36`, `targetSdk = 36`, and `minSdk = 26`
- Android SDK Platform 36 and Build Tools 36.0.0

Use the checked-in Gradle Wrapper; do not require a separately installed Gradle.

On Windows, run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS or Linux, run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The expected debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Change discipline

- Keep changes focused and add tests for behavior that can be verified reliably.
- Do not add dependencies unless their reading or editing benefit justifies binary size and maintenance cost.
- Treat external URIs, provider permissions, and Markdown content as untrusted.
- Keep raw Markdown HTML escaped, local WebView file access disabled, and document content unable to execute scripts.
- Preserve `.gitattributes`; Windows batch files are checked out as CRLF but stored canonically by Git. Verify a fresh checkout is clean after line-ending changes.
- Before committing, run the full validation command and inspect the complete diff.

## Release boundary

Normal development may produce debug APKs and draft pull requests. Do not merge `main`, create tags or GitHub Releases, add an automatic publishing workflow, or generate/configure a long-term signing key unless the maintainer explicitly authorizes that release step. Never commit signing material or credentials.
