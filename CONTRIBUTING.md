# Contributing to Mora

Mora is deliberately small: it aims to make opening, reading, and lightly editing a Markdown file on Android excellent. Contributions that strengthen that workflow are welcome.

## Before you start

- Search existing issues before opening a new one.
- Use the bug or feature issue form and provide a focused reproduction or use case.
- Open an issue before implementing a large feature or changing product direction.
- Do not include private document contents, access tokens, signing files, or personal paths in issues, logs, or fixtures.

Vaults, backlinks, graph views, built-in cloud sync, accounts, and plugin systems are not current project goals. A proposal in one of these areas needs product-direction agreement before implementation.

## Development setup

You need JDK 17 or newer (Android Studio's bundled runtime is supported) and Android SDK 36. Then run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Please also test file opening and saving on an emulator or device when your change touches Storage Access Framework or Android intents.

## Pull requests

1. Fork the repository and create a focused branch.
2. Keep unrelated formatting or dependency changes out of the pull request.
3. Add or update tests where behavior can be tested reliably.
4. Update user-facing documentation and `CHANGELOG.md` when appropriate.
5. Run the full validation command before submitting.
6. Explain the problem, solution, testing, and visible behavior in the pull request.

Small pull requests are easier to review. A pull request may be declined when it expands the product beyond Mora's document-first scope, duplicates platform behavior, or adds maintenance cost without a clear reading or editing benefit.

## Code expectations

- Follow the existing Kotlin and Compose style.
- Keep document access behind Android's Storage Access Framework.
- Preserve standard Markdown files; do not introduce a required private storage format.
- Treat external `Uri` values and file-provider permissions as untrusted and temporary.
- Keep Markdown rendering boundaries explicit: raw HTML remains escaped, local WebView file access remains disabled, and document content must not gain script execution.
- Add dependencies only when the benefit justifies the binary size and maintenance cost.

## Reporting security issues

Do not open a public issue for a vulnerability. Follow [SECURITY.md](SECURITY.md).

By contributing, you agree that your contribution is licensed under the project's MIT License.
