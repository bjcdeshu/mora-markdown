# Mora Roadmap

Mora's direction is simple:

> Make opening, reading, and lightly editing a standard Markdown file on Android feel calm, reliable, and complete.

This roadmap describes intent, not a delivery promise. Priorities may change after real-device feedback.

## v0.3.2 — stable polish

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
  three selectable launcher palettes built from one Mora mark
- Predictive document-to-Home back navigation on supported Android versions
- Scrollable Reader Appearance controls for landscape and compact-height windows
- Serialized, revision-aware writes that keep normal saves on the current file
- Minified, resource-shrunk Release builds with retained private R8 mappings
- Reproducible tests, lint, APK builds, and public project documentation

Public `v0.3.1` established the first stable line. Signed `v0.2.0-rc.1` remains a
historical public Pre-release, while stable v0.2.0 was never published. The
`v0.3.0` tag and hidden Draft remain an unpublished, blocked audit record and must
not be moved, replaced, or published.

## v0.3.x — focused follow-up

- Add a simple option to disable Android 12+ dynamic color and use Mora's calm
  fallback palette; this does not expand into a custom color editor.
- Improve draft recovery and protection against accidental data loss.

## Next

Priorities after v0.3.2:

- Detect external file changes and resolve save conflicts safely
- Improve relative local-image handling within Android's permission model
- Strengthen accessibility, large-text, tablet, and landscape behavior
- Expand parser, intent, persistence, and file-provider test coverage
- Polish editor selection, undo, and input-method behavior
- Add focused code-block syntax highlighting without turning the editor into an IDE

## Later, if they fit

- Optional math rendering with clear security boundaries
- Optional Mermaid rendering only if its execution and network boundaries remain
  compatible with Mora's controlled reader
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
