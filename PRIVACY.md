# Privacy

Mora is local-first. It has no account system, advertising SDK, analytics, telemetry, crash-reporting service, or Mora-operated backend.

## Data Mora handles

Mora may process:

- Markdown or plain-text documents that you select
- Files and text shared to Mora by another Android app
- Recently opened document URIs, display names, timestamps, and reading positions
- App-private recovery content and metadata for unsaved or interrupted writes
- Reader preferences such as font size, line height, and page margins
- App preferences such as appearance mode, dark-surface style, and launcher icon

Documents remain in the location managed by you and the Android file provider.
Recent-document metadata, reading positions, reader preferences, and app
preferences are stored locally in Mora's private Android preferences.

Mora requests access through Android's Storage Access Framework and may retain a
URI permission when the file provider allows it. A normal save writes directly
back to the current writable document; a new document or read-only source uses
Android's Save As flow.

To recover from a crash or failed provider write, Mora may temporarily store a
limited recovery record in app-private, no-backup storage. That record can include
the current unsaved document content, a known-good copy of the original bytes made
immediately before a write, the document URI and display name, and version or
recovery metadata. A verified save or an explicit discard clears the corresponding
unsaved recovery data. If a write cannot be verified, Mora can retain the
known-good original until you recover, export, or explicitly delete it. These
records are not uploaded and are not a general version-history or cloud-backup
feature.

You can remove retained access and all private recovery records by clearing Mora's
app data or uninstalling Mora. You can also revoke document access through Android
or the file provider.

## Network access

Mora does not send documents or usage data to a Mora server. Cleartext HTTP
traffic is disabled for the app.

The app declares Android's internet permission because a Markdown document can reference remote images. Opening such a document may request those images directly from their hosts. Those hosts can receive normal network information such as your IP address and request metadata. Avoid opening untrusted remote images when this is a concern.

Tapping an `http`, `https`, or `mailto` link hands the link to an external app chosen by Android. That app's privacy policy then applies.

## JavaScript

JavaScript is enabled inside the reader WebView only for Mora-controlled interactions such as heading navigation and current-section detection. Raw HTML from Markdown is escaped, document content cannot inject scripts through that path, and Mora does not expose a JavaScript bridge.

## Backups

Mora excludes its private app data from Android cloud backup and device-to-device
transfer. Recent-document metadata, reading positions, reader preferences, app
preferences, and recovery records are therefore not intentionally copied through
Android's backup or migration systems. Original documents remain controlled by
their file provider; any app-private document bytes are limited to the local
recovery behavior described above and are never used for cloud sync.

## Changes

Privacy-relevant behavior will be documented here and in the changelog. A feature
that adds telemetry, an account, or a Mora-operated network service would require
an explicit policy update. Mora v0.4 pre-releases add the local recovery behavior
described above, but add no telemetry, account, Mora-operated service, or new data
collection.
