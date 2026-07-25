# Mora Release Guide

Mora uses one long-term Android app-signing key for every public APK. Losing that key or its passwords prevents existing users from installing future GitHub builds as updates. Treat this document as a release gate, not as a convenience checklist.

The first stable target is `v0.3.1`. Stable v0.2.0 was superseded before
publication; signed `v0.2.0-rc.1` remains a historical public Pre-release.
The tagged v0.3.0 candidate failed its hidden Draft exact-asset device gate and
must remain unpublished. v0.3.1 will not use a public release candidate. Its
protected `main` candidate and its separately rebuilt hidden Draft attachment
must each pass the complete exact-asset gate before the same Draft is made public
as stable.

## v0.3.1 publication path

1. Merge only reviewed pull requests with green CI.
2. Before building the candidate, finalize `versionCode`, `versionName`, and the
   dated `CHANGELOG.md` heading in a release-finalization pull request. Merge it,
   then confirm `main` CI passes on that resulting commit.
3. Manually run **Signed Android release** from that exact `main` commit. Download
   its private Actions APK and SHA-256 sidecar, independently verify identity, and
   run the complete real-device matrix on that exact APK.
4. Record the commit, APK SHA-256, devices, Android versions, file providers, and
   results. An explicit maintainer approval is required before tagging.
5. Create annotated stable tag `v0.3.1` on the same tested commit. The tag workflow
   rebuilds and creates a hidden Draft Pre-release; it does not publish it.
6. Download that exact Draft APK and sidecar. Repeat all identity checks and the
   complete real-device matrix because this is a new build. Record a second
   explicit maintainer approval.
7. Edit that same GitHub Release to `draft=false`, `prerelease=false`, and latest.
   Do not move the tag or replace either attachment. Redownload the public APK and
   confirm its SHA-256 is identical to the approved Draft APK.

## Blocked v0.3.0 record

`v0.3.0` is permanently blocked from publication. Its annotated tag points to
commit `03fbe8ca1dd4062e19ca86b3fdf530e9cd80ffa0`; its hidden Draft APK has
SHA-256 `F0781946AD4BA2F13577292EE0A9BB5B3083771D2CC52073B748D809BDFF98D8`.
Real-device testing found that a full-screen Compose pointer consumer intercepted
reader WebView motion events and that the predictive document-to-Home transition
was too forceful. The tag, Draft, APK, and sidecar are retained unchanged as audit
history. Never move the tag, replace either attachment, or publish this Draft.
All stable successors must use a later version name and a `versionCode` greater
than 4.

## Safety model

- The private keystore and passwords never enter Git.
- Normal pull-request and `main` CI never receive signing secrets.
- The `release-signing` GitHub Environment holds the automation copy only.
- GitHub Secrets are not a backup and cannot be read back.
- Keep at least two independently stored encrypted keystore backups and keep the passwords in a password manager or equivalent recovery system.
- Restore-test each backup before the first public tag.
- Commit only the public certificate and its SHA-256 fingerprint; never commit the private key or keystore.

The GitHub and future Google Play builds must use the same app-signing identity if users should be able to move between those distribution channels without uninstalling.

## Required signing environment

Local and CI release builds require:

| Name | Purpose |
|---|---|
| `MORA_RELEASE_STORE_FILE` | Absolute path to the private keystore |
| `MORA_RELEASE_STORE_PASSWORD` | Keystore password |
| `MORA_RELEASE_KEY_ALIAS` | Signing-key alias |
| `MORA_RELEASE_KEY_PASSWORD` | Private-key password |

GitHub stores the keystore itself as the Environment secret `MORA_RELEASE_KEYSTORE_BASE64` and recreates `MORA_RELEASE_STORE_FILE` only inside the runner's temporary directory.

The `release-signing` Environment also contains:

- Secrets: `MORA_RELEASE_KEYSTORE_BASE64`, `MORA_RELEASE_STORE_PASSWORD`, `MORA_RELEASE_KEY_ALIAS`, and `MORA_RELEASE_KEY_PASSWORD`
- Variable: `MORA_RELEASE_CERT_SHA256`

The fingerprint is public. It is an integrity check that prevents a valid APK signed by the wrong key from being uploaded.

Mora's public release certificate is versioned at `docs/mora-release-certificate.pem`; its normalized SHA-256 fingerprint is in `docs/mora-release-certificate.sha256`. The workflow requires the APK signer, these repository records, and the protected GitHub Environment variable to agree.

## Local validation

Debug validation does not need signing material:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

A local signed candidate uses the four environment variables above:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache --no-daemon testDebugUnitTest lintRelease assembleRelease
```

The expected output is:

```text
app/build/outputs/apk/release/app-release.apk
```

Before sharing it, verify the APK with Build Tools 36:

```powershell
$sdk = $env:ANDROID_SDK_ROOT
& "$sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs -Werr app\build\outputs\apk\release\app-release.apk
& "$sdk\build-tools\36.0.0\aapt.exe" dump badging app\build\outputs\apk\release\app-release.apk
Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

The package must be `de.unbow.mora`, `minSdk` must be 26, `targetSdk` must be 36,
and the signer certificate SHA-256 must equal the registered fingerprint. For the
v0.3.1 stable path, `versionName` must be `0.3.1` and `versionCode` must be `5`.
The downloaded `.sha256` sidecar must verify the exact APK before and after each
device gate.

## Signed candidate

After release infrastructure and the intended version are on `main`, finalize the
dated changelog through normal review and CI. Only then run **Signed Android
release** manually from the exact `main` commit selected for the candidate. Do
not make a release-metadata commit between this build and its stable tag.

The workflow:

1. receives signing material only after entering the `release-signing` Environment;
2. runs unit tests, Release lint, and the Release build;
3. verifies package metadata, signing validity, and the fixed certificate fingerprint;
4. uploads an APK and SHA-256 sidecar as a 30-day Actions artifact;
5. creates no tag and no GitHub Release.

Download that artifact and its sidecar, verify them independently, and record its
SHA-256 before device testing. For v0.3.1, this protected Actions artifact is the
first exact-asset gate and must complete the full matrix before any tag is created.

## Public release candidate

This optional path is retained for a future version that explicitly needs public
testing. It is not used for v0.3.1.

A release-candidate tag uses `v<major>.<minor>.<patch>-rc.<number>`. Before pushing
one:

1. increment `versionCode`;
2. make `versionName` equal the tag without its leading `v`;
3. add one dated heading for that exact version to `CHANGELOG.md`;
4. run normal pull-request and `main` CI;
5. create an annotated tag on a commit contained in `main`.

The tag workflow rebuilds the APK with the protected long-term key, verifies its
package metadata and certificate, verifies the downloaded workflow artifact, and
publishes an explicitly labeled public Pre-release with the APK and SHA-256
sidecar. The release notes must state in both English and Chinese that the complete
real-device matrix is pending and advise users to back up important files before
editing.

Publishing an RC makes a test build easy to download; it does not satisfy or weaken
the stable-release gate. Download the exact Release attachment, independently
repeat its checksum, signer, and package-metadata checks, and use that same APK for
the device matrix.

## Real-device gate

Test the exact downloaded candidate, not a later local rebuild.

- Android 8.0 / API 26: install, launch, open, edit, save, and reopen a Markdown file.
- A current Android version: repeat the same flow.
- Open a `.md` file from a file manager and from an external sharing app while Mora is both closed and already running.
- Verify read-only input leads to Save As rather than silent failure.
- Verify recent documents, reading-position restoration, table of contents,
  search, and an orientation change.
- Use both long and short documents in portrait and landscape. The right-edge
  progress thumb must stay hidden for a short document; for a long document it
  must remain subtle while idle, become clearer while scrolling, drag without
  jumping, and reach 0%, 50%, and 100%. Confirm its local right-edge drag area
  does not block system Back outside the thumb.
- Verify English and Simplified Chinese follow the selected device language. On
  Android 13+, also switch Mora through the system per-app language page and
  confirm document text and filenames are unchanged. Confirm the settings sheet
  closes before the system page opens and that returning or an unavailable system
  language activity does not crash Mora.
- Force an Activity recreation through a language change while editing and while
  overlays are open. Unsaved text, search, table of contents, pending confirmation
  dialogs, and the current reading position must remain intact.
- Verify system, light, dark, default-dark, and pure-black appearance behavior.
  Include Android 12+ dynamic color, system-light with Mora-dark, and system-dark
  with Mora-light. Status/navigation-bar icons must remain readable after restart;
  the pure-black Reader surface must be `#000000`, and theme changes must not move
  the reading position.
- Switch among Indigo, Pine, and Night launcher palettes. Confirm Mora remains
  launchable after every switch, process restart, cleared app data, and an
  in-place upgrade from `v0.2.0-rc.1`. Verify launch from Recents and with the
  system themed-icon option both on and off; record OEM cache delays or shortcut
  recreation in the release notes.
- On a predictive-Back-capable current Android device, verify a clean document
  reveals Home, cancellation keeps it open, and Home then previews and returns to
  the system launcher. Search, table of contents, Reader/App sheets, and dialogs
  must close before the document. Repeat with unsaved edits and confirm discard
  remains explicit. Repeat the same routes with three-button navigation.
- At 200% English system font size, check Home, search, the editor formatting bar,
  and all application and Reader settings for clipping or inaccessible controls.
- Recheck file-manager and sharing-app opening after launcher-icon switching to
  confirm activity aliases did not change external `VIEW`, `EDIT`, or `SEND`
  entry behavior.
- Confirm a Debug-signed preview must be uninstalled before the first Release-signed install.
- Reinstall the same candidate over itself without losing access to recent documents.
- Check the final APK SHA-256 and certificate fingerprint again after download.

Record device models, Android versions, file providers, failures, and the approved
APK SHA-256 in the release pull request or the release-tracking issue.

## Stable version and tag gate

Before tagging a stable version:

1. confirm the exact protected `main` candidate passed the complete identity and
   real-device gate;
2. confirm `versionCode` was incremented for this distributed build;
3. confirm `versionName` equals the tag without its leading `v`;
4. confirm the matching `CHANGELOG.md` heading was dated before the candidate
   build and contains no `Unreleased` marker;
5. confirm no commit has been added to the tested `main` commit;
6. confirm the repository has an active `v*` tag ruleset that prevents deletion and non-fast-forward updates;
7. obtain explicit maintainer approval;
8. create an annotated `v<versionName>` tag on the exact tested commit in `main`.

Pushing a stable tag runs the same signed build and verification path. Only a tag
push—not a manually selected tag ref—may invoke the release job. Unlike an `-rc.`
tag, a stable tag creates a draft pre-release with the verified APK and checksum;
it does not make that draft public.

The tag workflow rebuilds the APK, so its attachment is not assumed to be byte-for-byte identical to the manually dispatched candidate. Download the exact draft attachment, independently repeat the checksum, signer, and package-metadata checks, and run the complete real-device gate again. Publish the draft only after that exact APK passes and the maintainer explicitly approves publication.

Stable publication means editing that same Release to remove both Draft and
Pre-release status and mark it latest. Do not move the tag, replace the APK, or
replace its sidecar after approval. Redownload the public APK once more and verify
that its SHA-256 still matches the approved Draft attachment.

## Recovery and rotation

- Do not rotate the app-signing key after a public GitHub release unless Android's supported key-upgrade path and every distribution channel have been reviewed.
- If the key may have leaked before the first public release, revoke the candidate identity, remove its GitHub secrets, generate a new key, update the registered fingerprint, and rebuild.
- If the key may have leaked after release, stop publishing and document the incident before taking action.
- If a backup cannot be restored, do not publish. Repair the backup set while the current key is still available.
