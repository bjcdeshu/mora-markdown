# Changelog

All notable changes to Mora are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - Unreleased

Stable release target. Changes made after the release candidate will be recorded
here.

## [0.2.0-rc.1] - 2026-07-25

First public signed release candidate.

### Added

- Immersive reader toolbar that hides while scrolling down and returns on upward scroll.
- Transient right-edge reading-position marker for scrollable documents.
- Refined default typography with persistent font-size, line-height, and margin settings.
- H1–H3 table of contents with current-section highlighting.
- In-document search with previous and next match navigation.
- Recent-document list and per-document reading-position restoration.
- External `VIEW`, `EDIT`, and `SEND` intent handling, including intents received while Mora is already open.
- Clear errors for unreadable external documents and Save As behavior for read-only sources.
- Basic Markdown source editor with a mobile formatting toolbar.
- CommonMark rendering with tables, strikethrough, and task lists.
- Android Storage Access Framework open, create, save, and persisted URI permissions.
- Public-repository documentation, contribution guidance, issue templates, and Android CI.
- Verified signed-candidate and tag-triggered release automation with signing secrets isolated from normal CI.

### Changed

- Extended immersive reading to hide the status bar and softly fade text at the top edge.
- Replaced the provisional launcher mark with the approved Fine Frame adaptive, round, legacy, and themed icon resources.
- Updated GitHub Actions to pinned Node 24-compatible releases and added monthly Actions dependency checks.
- Added an explicitly labeled public RC Pre-release path while retaining the exact-asset device gate for stable releases.
- Standardized local and CI builds on the stable Android 16 SDK (API 36) while retaining Android 8.0 (API 26) as the minimum supported version.
- Normalized the Windows Gradle launcher so fresh clones remain clean under the repository's line-ending rules.

### Fixed

- Kept complete Markdown documents out of the Android saved-state Bundle.
- Moved document-provider display-name queries off the main thread and prevented stale failed opens from changing recent documents.
- Removed an incorrect fixed Chinese language declaration from user-supplied document HTML.
- Kept navigation-bar icons readable on Android 8.0 while using light-navigation-bar styling only where the platform supports it.
- Made reader-setting number formatting react to locale changes in Compose.

### Security

- Raw Markdown HTML is escaped and potentially unsafe URLs are sanitized.
- Reader WebView local-file, `content://`, file-URL cross-origin, and cleartext access are disabled.
- The reader's internal page is restricted to a fixed, non-resolving HTTPS origin.
- Private preferences and recent-document metadata are excluded from Android cloud backup and device transfer.
- JavaScript use is limited to Mora-controlled reader interactions.

## Notes

The earlier v0.1 build was an internal product prototype and was not a public release.
