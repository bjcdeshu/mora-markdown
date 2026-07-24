# Security Policy

## Supported versions

Security fixes are applied to the latest public Mora release. Pre-release builds and older versions may be used to reproduce a report, but are not guaranteed to receive patches.

## Reporting a vulnerability

Please do not disclose a suspected vulnerability in a public Issue, Discussion, or pull request.

Use GitHub's private vulnerability reporting for this repository:

1. Open the repository's **Security** tab.
2. Choose **Advisories**.
3. Select **Report a vulnerability**.

If the private reporting button is unavailable, open a public issue containing no vulnerability details and ask the maintainer to establish a private reporting channel.

Include:

- The affected Mora version and Android version
- A concise impact description
- Reproduction steps or a minimal test file
- Whether the issue requires a particular file provider or external app
- Any suggested mitigation

Remove personal document contents, credentials, and unrelated device information. You should receive an acknowledgement through the advisory thread; remediation and disclosure timing will be coordinated there.

## Security boundaries

Mora reads files selected by the user or delegated by another Android app. It does not treat Markdown as trusted executable content.

- Raw HTML is escaped during rendering.
- Potentially unsafe URLs are sanitized by the Markdown renderer.
- JavaScript is enabled for controlled reader interactions only; Mora does not expose a JavaScript bridge to document content.
- WebView local-file, `content://`, file-URL cross-origin, and cleartext access
  are disabled; Mora's internal reader origin is fixed and allowlisted.
- External links open in the system browser.
- Remote images may make network requests to their original hosts.

Reports that demonstrate a bypass of these boundaries, unintended file access, unsafe intent handling, or destructive save behavior are especially important.
