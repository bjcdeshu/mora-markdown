# Mora Roadmap

Mora's direction is simple:

> Make opening, reading, and lightly editing a standard Markdown file on Android feel calm, reliable, and complete.

This roadmap describes intent, not a delivery promise. Priorities may change after real-device feedback.

## v0.3.0 — first stable release

The current codebase includes:

- Immersive reading with a scroll-aware toolbar
- A subtle right-edge progress thumb with direct dragging for long documents
- Refined default typography and persistent reader settings
- H1–H3 table of contents and in-document search
- Recent documents and reading-position restoration
- Robust external opening through Android intents
- Clear read-only and Save As behavior
- Basic Markdown source editing and a mobile formatting toolbar
- English and Simplified Chinese UI, including Android 13+ per-app language access
- App-level system/light/dark appearance, optional pure-black dark surfaces, and
  three selectable launcher palettes
- Predictive document-to-Home back navigation on supported Android versions
- Reproducible tests, lint, APK builds, and public project documentation

The signed `v0.2.0-rc.1` build remains a historical public Pre-release. Stable
`v0.2.0` was not published and is superseded by this target.

Mora will not publish a separate public RC for v0.3.0. After pull-request and
`main` CI pass, a protected manual build from `main` supplies the first exact APK
for the complete device gate. Only after approval is the stable tag created; its
hidden Draft asset must independently repeat the same gate before that same
Release is made public as stable.

## v0.3.x — focused follow-up

- Add a simple option to disable Android 12+ dynamic color and use Mora's calm
  fallback palette; this does not expand into a custom color editor.

## Next

Priorities after v0.3.0:

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
