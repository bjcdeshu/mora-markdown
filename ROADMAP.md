# Mora Roadmap

Mora's direction is simple:

> Make opening, reading, and lightly editing a standard Markdown file on Android feel calm, reliable, and complete.

This roadmap describes intent, not a delivery promise. Priorities may change after real-device feedback.

## v0.2.0 — first public release

The current codebase targets:

- Immersive reading with a scroll-aware toolbar
- Refined default typography and persistent reader settings
- H1–H3 table of contents and in-document search
- Recent documents and reading-position restoration
- Robust external opening through Android intents
- Clear read-only and Save As behavior
- Basic Markdown source editing and a mobile formatting toolbar
- Reproducible tests, lint, APK builds, and public project documentation

The `v0.2.0-rc.1` build is the first public signed candidate. It may be published
as a clearly labeled Pre-release after automated build and signing checks so that
real-device feedback can be collected from one exact downloadable APK.

The stable `v0.2.0` release should be cut only after CI passes, the long-term
signing key has recoverable independent backups, and the exact signed asset has
passed the documented real-device matrix.

## Next

Priorities after v0.2.0:

- Improve draft recovery and protection against accidental data loss
- Detect external file changes and resolve save conflicts safely
- Improve relative local-image handling within Android's permission model
- Strengthen accessibility, large-text, tablet, and landscape behavior
- Expand parser, intent, persistence, and file-provider test coverage
- Polish editor selection, undo, and input-method behavior

## Later, if they fit

- Code-block syntax highlighting
- Optional Mermaid and math rendering with clear security boundaries
- Export workflows
- More reader themes without weakening the default experience

## Not planned

- Vault management
- Backlinks or graph views
- A required account
- Built-in cloud sync
- A proprietary document format
- A plugin marketplace

Mora can revisit a non-goal only when it directly improves the single-document workflow without turning the app into a knowledge workspace.
