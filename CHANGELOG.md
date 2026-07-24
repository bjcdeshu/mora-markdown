# Changelog

All notable changes to Mora are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - Unreleased

First public release target.

### Added

- Immersive reader toolbar that hides while scrolling down and returns on upward scroll.
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

### Security

- Raw Markdown HTML is escaped and potentially unsafe URLs are sanitized.
- Reader WebView local-file and `content://` access are disabled.
- JavaScript use is limited to Mora-controlled reader interactions.

## Notes

The earlier v0.1 build was an internal product prototype and was not a public release.
