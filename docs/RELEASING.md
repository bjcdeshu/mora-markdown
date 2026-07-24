# Mora Release Guide

Mora uses one long-term Android app-signing key for every public APK. Losing that key or its passwords prevents existing users from installing future GitHub builds as updates. Treat this document as a release gate, not as a convenience checklist.

The first public target is `v0.2.0`. It remains unreleased until the maintainer approves the exact signed candidate after real-device testing.

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

The package must be `de.unbow.mora`, `minSdk` must be 26, `targetSdk` must be 36, and the signer certificate SHA-256 must equal the registered fingerprint.

## Signed candidate

After release infrastructure is on `main`, run **Signed Android release** manually from the `main` branch.

The workflow:

1. receives signing material only after entering the `release-signing` Environment;
2. runs unit tests, Release lint, and the Release build;
3. verifies package metadata, signing validity, and the fixed certificate fingerprint;
4. uploads an APK and SHA-256 sidecar as a 30-day Actions artifact;
5. creates no tag and no GitHub Release.

Download that artifact and record its SHA-256 before device testing.

## Real-device gate

Test the exact downloaded candidate, not a later local rebuild.

- Android 8.0 / API 26: install, launch, open, edit, save, and reopen a Markdown file.
- A current Android version: repeat the same flow.
- Open a `.md` file from a file manager and from an external sharing app while Mora is both closed and already running.
- Verify read-only input leads to Save As rather than silent failure.
- Verify recent documents, reading-position restoration, table of contents, search, dark mode, and an orientation change.
- Confirm a Debug-signed preview must be uninstalled before the first Release-signed install.
- Reinstall the same candidate over itself without losing access to recent documents.
- Check the final APK SHA-256 and certificate fingerprint again after download.

Record device models, Android versions, file providers, failures, and the approved APK SHA-256 in the release pull request.

## Version and tag gate

Before tagging:

1. confirm the candidate passed the real-device gate;
2. increment `versionCode` for every distributed build;
3. make `versionName` equal the tag without its leading `v`;
4. replace `Unreleased` in the matching `CHANGELOG.md` heading with the release date;
5. confirm the repository has an active `v*` tag ruleset that prevents deletion and non-fast-forward updates;
6. run normal CI and obtain explicit maintainer approval;
7. create an annotated `v<versionName>` tag on a commit contained in `main`.

Pushing the tag runs the same signed build and verification path. Only a tag push—not a manually selected tag ref—may invoke the publish job. The workflow creates a draft pre-release with the verified APK and checksum; it does not make that draft public.

The tag workflow rebuilds the APK, so its attachment is not assumed to be byte-for-byte identical to the manually dispatched candidate. Download the exact draft attachment, independently repeat the checksum, signer, and package-metadata checks, and run the complete real-device gate again. Publish the draft only after that exact APK passes and the maintainer explicitly approves publication.

## Recovery and rotation

- Do not rotate the app-signing key after a public GitHub release unless Android's supported key-upgrade path and every distribution channel have been reviewed.
- If the key may have leaked before the first public release, revoke the candidate identity, remove its GitHub secrets, generate a new key, update the registered fingerprint, and rebuild.
- If the key may have leaked after release, stop publishing and document the incident before taking action.
- If a backup cannot be restored, do not publish. Repair the backup set while the current key is still available.
