# Mora Project Status

Updated: 2026-07-25

## Current stage

Mora `v0.3.2` (`versionCode` 6) is the current stable-release target. It is a
focused reliability and public-release polish update over the public `v0.3.1`
release; signed `v0.2.0-rc.1` remains a historical public Pre-release.

The v0.3 series keeps Mora's single-document workflow while providing:

- a subtle proportional right-edge reading-position thumb with direct dragging;
- English and Simplified Chinese UI, including Android 13+ per-app language access;
- separate Home-level application settings and Reader typography controls;
- system/light/dark appearance plus an optional pure-black dark surface;
- three selectable launcher palettes built from one Mora mark;
- predictive document-to-Home Back that previews the real composed Home beneath
  the current document on supported Android versions.

The v0.3.2 update closes five focused release gaps:

- Reader Appearance content scrolls in compact-height and landscape windows so
  every typography control and Restore defaults remain reachable above system
  navigation insets;
- repeated save requests are serialized, the write uses a revisioned content
  snapshot, and edits made during a save remain visibly unsaved without changing
  the normal one-tap write-back path;
- the launcher mark uses a deep ink-blue field and a warm-white folded-page form,
  with matching adaptive, themed, round, legacy, and alias resources;
- Release builds enable R8 optimization and resource shrinking, and the protected
  release workflow retains the private mapping as an Actions artifact;
- current screenshots, bilingual public documentation, issue intake, repository
  metadata, and private security reporting are aligned with the v0.3.2 release
  target.

App Settings remains a scrollable Material bottom sheet. During predictive Back,
Android transforms the already drawn sheet surface; content outside the current
viewport is not remeasured into view. That transform-without-reflow behavior is
accepted for v0.3.2 as long as cancellation and completion restore the correct
state without clipping, flashing, data loss, or a crash.

The v0.3.2 stable path validates one exact signed asset. Green pull-request and
final `main` CI are followed by an annotated `v0.3.2` tag. The tag workflow creates
a hidden Draft Pre-release; its exact APK receives independent checksum, package,
version, and certificate checks plus one short smoke session on a current Android
device. That same Draft is then made stable/latest without moving the tag or
replacing its attachments.

The annotated `v0.3.0` tag and its hidden Draft are permanently blocked from
publication. Exact Draft-asset testing found a full-screen Compose pointer
consumer intercepting reader motion events and an overly forceful predictive-Back
transition. The tag and attachments remain unchanged as audit history; they must
not be moved, replaced, or published. Public v0.3.1 superseded it, and every later
candidate must use a later immutable tag and a higher `versionCode`.

The first long-term RSA-2048 identity was generated outside the repository on
2026-07-24. Its encrypted keystore backups and independently stored credentials
passed restore checks. The protected `release-signing` GitHub Environment and the
repository's public certificate record are the automation boundary; signing
material remains outside Git.

`docs/PRODUCT_SPEC.md` is an early exploration document, not the current
implementation checklist. In particular, its CodeMirror and single-WebView
proposal does not describe the current Compose source editor plus reader WebView
architecture. Current code, `ROADMAP.md`, and this status document take
precedence.

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
- save coordination permits only one active write per document session, keeps the
  current destination URI for normal writable files, and preserves dirty state
  when the editor advances beyond the saved snapshot;
- the Reader keeps its WebView security boundary while adding native progress
  drawing and local pointer handling without placing a full-screen Compose
  consumer above WebView motion events;
- predictive-Back progress updates invalidate graphics layers instead of
  recomposing the text-heavy document tree, while Home remains visually stable;
- pull-request and `main` CI exercise both the Debug gate and the minified Release
  pipeline without receiving Mora's protected release-signing identity;
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

Pull-request and `main` CI also run `lintRelease assembleRelease` with a
runner-generated, one-run validation key. That job proves that R8, resource
shrinking, Release lint, packaging, mapping generation, and signature validation
complete, but the temporary key and APK are deleted and the APK is never uploaded.
It is not a release candidate and cannot update an officially signed installation.

Changes involving file providers, external intents, saving, layout, launcher
aliases, or accessibility also require focused emulator or real-device checks.

The final release commit must pass the full local Debug command and both GitHub
Actions paths before the immutable stable tag is created. No lint baseline, broad
keep rule, or new suppression may be introduced merely to make the gate pass.

## Release boundary

Release signing material must stay outside Git and be backed up independently;
GitHub Secrets are delivery infrastructure, not a backup.

- Pull-request and `main` CI publish only the Debug APK artifact and never receive
  the protected keystore or its credentials. Their ephemeral Release validation
  key is generated and destroyed inside one runner job.
- The annotated stable tag must point to the final green commit already contained
  in `main`; it must never be moved or force-updated.
- Only the protected **Signed Android release** workflow may use the long-term
  signing identity. For v0.3.2, only its tag-triggered run creates the stable
  candidate and hidden Draft.
- The exact hidden Draft APK and SHA-256 sidecar must pass independent checksum,
  package, SDK, version, and signer-certificate checks.
- One current Android device must pass the documented short smoke session on that
  exact Draft APK, including a `v0.3.1` in-place upgrade when available.
- Publication edits that same Draft to `draft=false`, `prerelease=false`, and
  latest. Do not replace the APK or sidecar.
- The public attachment must be downloaded again and match the approved Draft
  APK's SHA-256.
- Future public RCs, if used, remain visibly labeled Pre-releases and never weaken
  the stable gate.

The operational checklist and credential names are maintained in [docs/RELEASING.md](docs/RELEASING.md).
