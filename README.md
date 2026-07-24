# Mora

**A quiet, local-first Markdown reader and editor for Android.**

[简体中文](README.zh-CN.md)

Mora is built around one simple workflow: open a standard Markdown file, read it comfortably, make a quick edit when needed, and keep the file yours.

The project is intentionally document-first rather than vault-first. There is no account, proprietary document format, telemetry, or cloud service.

> Mora v0.2.0 is the first public release target. The project is still young; please report device- and file-provider-specific issues through GitHub Issues.

## Highlights

- Immersive reading with a toolbar that follows scroll direction
- Tuned typography with adjustable font size, line height, and page margins
- Table of contents for H1–H3 headings, current-section highlighting, and in-document search
- Recent documents and per-document reading-position restoration
- Local file access through Android's Storage Access Framework
- External opening through Android `VIEW`, `EDIT`, and `SEND` intents
- Basic Markdown source editing, a mobile formatting toolbar, save, and Save As
- CommonMark plus tables, strikethrough, and task lists
- Material 3, dynamic color, dark mode, and Android 8.0+

## What Mora is not

Mora is not trying to become a knowledge workspace. Vaults, backlinks, graph views, built-in cloud sync, accounts, and a plugin ecosystem are outside the product's current scope.

## Download

Installable APKs will be attached to this repository's [GitHub Releases](../../releases). Until a release is published, build the debug APK from source.

## Build from source

Requirements:

- JDK 17
- Android SDK 37
- A recent Android Studio version compatible with Android Gradle Plugin 9.3

Clone the repository and run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK will be written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Mora supports Android 8.0 (API 26) and later.

## Rendering and network behavior

Markdown is parsed with `commonmark-java`, rendered to HTML, styled with a controlled CSS document, and displayed in an Android WebView.

- Raw HTML in Markdown is escaped.
- Potentially unsafe URLs are sanitized by the renderer.
- JavaScript is enabled only for Mora-controlled reader interactions, such as heading navigation and current-section detection. Markdown content cannot inject script through raw HTML, and Mora exposes no JavaScript bridge.
- Local file and `content://` access are disabled inside the WebView.
- Remote images referenced by Markdown may connect to their host when the document is rendered. Mora therefore declares Android's internet permission.
- Tapped web links are handed to the system browser.

See [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md) for the complete boundaries.

## Project structure

```text
app/src/main/java/de/unbow/mora/
├── data/       # File access, reader settings, and recent documents
├── markdown/   # CommonMark parsing and reader HTML/CSS
├── model/      # Document state and persistence coordination
└── ui/         # Compose screens, editor, reader, search, and TOC
```

The Android shell uses Jetpack Compose and Material 3. Reading uses CommonMark → HTML/CSS → WebView; editing uses a native Compose source editor.

## Contributing

Bug reports and focused improvements are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a large pull request.

The project direction is documented in [ROADMAP.md](ROADMAP.md), and release changes are tracked in [CHANGELOG.md](CHANGELOG.md).

## License

Mora is available under the [MIT License](LICENSE).
