# Privacy

Mora is local-first. It has no account system, advertising SDK, analytics, telemetry, crash-reporting service, or Mora-operated backend.

## Data Mora handles

Mora may process:

- Markdown or plain-text documents that you select
- Files and text shared to Mora by another Android app
- Recently opened document URIs, display names, timestamps, and reading positions
- Reader preferences such as font size, line height, and page margins

Documents remain in the location managed by you and the Android file provider. Recent-document metadata, reading positions, and reader preferences are stored locally in the app's private Android preferences.

Mora requests access through Android's Storage Access Framework and may retain a URI permission when the file provider allows it. You can remove that access by clearing Mora's app data, uninstalling Mora, or revoking the relevant permission through Android or the file provider.

## Network access

Mora does not send documents or usage data to a Mora server. Cleartext HTTP
traffic is disabled for the app.

The app declares Android's internet permission because a Markdown document can reference remote images. Opening such a document may request those images directly from their hosts. Those hosts can receive normal network information such as your IP address and request metadata. Avoid opening untrusted remote images when this is a concern.

Tapping an `http`, `https`, or `mailto` link hands the link to an external app chosen by Android. That app's privacy policy then applies.

## JavaScript

JavaScript is enabled inside the reader WebView only for Mora-controlled interactions such as heading navigation and current-section detection. Raw HTML from Markdown is escaped, document content cannot inject scripts through that path, and Mora does not expose a JavaScript bridge.

## Backups

Mora excludes its private app data from Android cloud backup and device-to-device transfer. Recent-document metadata, reading positions, and reader preferences are therefore not intentionally copied through Android's backup or migration systems. The original documents remain controlled by their file provider and are never copied into Mora's private storage as a cloud-sync feature.

## Changes

Privacy-relevant behavior will be documented here and in the changelog. A feature that adds telemetry, an account, or a Mora-operated network service would require an explicit policy update; none exists in v0.2.0.
