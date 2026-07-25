# Mora Release Guide

Mora uses one long-term Android app-signing key for every public APK. Losing that
key or its passwords prevents existing users from installing future GitHub builds
as updates. Treat this document as a release gate, not as a convenience checklist.

Stable v0.3.2 uses one exact signed candidate and one short current-device smoke
session:

```text
Pull-request and final main CI green
→ create annotated v0.3.2 tag on that final main commit
→ tag workflow creates a signed hidden Draft Pre-release
→ independently verify that exact Draft APK once
→ run one short smoke session on one current Android device
→ edit the same Draft to stable/latest
→ redownload the public attachment and confirm SHA-256 is unchanged
```

The v0.3.2 publication path uses no separate protected manual candidate and no
second full device matrix. Pull-request and `main` CI already exercise the
minified Release pipeline with an ephemeral non-release key. The retained manual
dispatch can produce a protected signed Actions artifact for another explicitly
approved purpose, but it is not used for v0.3.2 and creates no GitHub Release; only
the tag-triggered run creates the distributable v0.3.2 Draft candidate.

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
- Normal pull-request and `main` CI never receive the protected release keystore
  or its credentials.
- Their runner-generated validation key exists for one job only, signs no uploaded
  artifact, and must never be described as a release candidate.
- The `release-signing` GitHub Environment holds the automation copy only.
- GitHub Secrets are not a backup and cannot be read back.
- Keep at least two independently stored encrypted keystore backups and keep the passwords in a password manager or equivalent recovery system.
- Restore-test the backup set periodically and before any signing-identity change.
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

The normal local gate does not need signing material:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The Debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Pull-request and `main` CI repeat that Debug gate and then run
`lintRelease assembleRelease` with a runner-generated one-run key. The job checks
that R8, resource shrinking, mapping generation, Release packaging, and signature
verification complete. It confirms that this temporary signer does not equal
Mora's registered certificate, deletes the key and Release APK, and uploads only
the Debug APK.

That ephemeral Release build is a build-time validation artifact, not an exact
candidate. It cannot update an officially signed Mora installation and must not be
distributed.

If a maintainer performs an additional local Release build, the four signing
environment variables above remain mandatory:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache --no-daemon lintRelease assembleRelease
```

Do not weaken `verifyReleaseSigningEnvironment`, add an unsigned fallback, or use
a locally rebuilt APK in place of the tag workflow's Draft attachment.

## Pre-tag gate

Before creating `v0.3.2`, confirm:

1. the release pull request is reviewed and its Android CI job is green;
2. final `main` CI is green on the exact commit to tag;
3. `versionName` is `0.3.2` and `versionCode` is `6`;
4. `CHANGELOG.md` contains exactly one dated `## [0.3.2] - YYYY-MM-DD` heading;
5. all intended code, launcher resources, screenshots, and documentation are in
   that commit;
6. there are no uncommitted or unpushed release changes;
7. the repository's `v*` tag ruleset prevents deletion and non-fast-forward
   updates.

Create and push the annotated tag on that exact `main` commit:

```powershell
git tag -a v0.3.2 -m "Mora v0.3.2"
git push origin v0.3.2
```

Never create the tag speculatively. If code must change after it is pushed, do not
move or replace the tag; stop v0.3.2 publication and prepare a later version.

## Tag workflow and hidden Draft

The `v0.3.2` push starts **Signed Android release**. The workflow:

1. checks that the tag is valid and its commit is contained in `main`;
2. enters the protected `release-signing` Environment;
3. runs unit tests, Release lint, R8, resource shrinking, and the Release build;
4. verifies package metadata, minimum and target SDKs, signing validity, and the
   registered certificate fingerprint;
5. produces `Mora-v0.3.2.apk` and `Mora-v0.3.2.apk.sha256`;
6. retains the official R8 mapping output as a private Actions artifact, not a
   public Release attachment;
7. creates a hidden Draft Pre-release for the tag.

The Draft APK is the only signed candidate that receives the v0.3.2 identity and
device gate.

## Public release candidate

This optional path is retained for a future version that explicitly needs public
testing. It is not used for v0.3.2.

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
sidecar. The release notes must state in both English and Chinese that real-device
testing is pending and advise users to back up important files before editing.

Publishing an RC makes a test build easy to download; it does not satisfy or weaken
the stable-release gate. Download the exact Release attachment, independently
repeat its checksum, signer, and package-metadata checks, and use that same APK for
any declared device test.

## Verify the exact Draft APK

Wait for the tag-triggered **Signed Android release** workflow to succeed. Download
the APK and sidecar from the hidden Draft Release, not from a local rebuild and not
only from the intermediate Actions artifact.

Verify the sidecar from the directory containing both files:

```powershell
$apk = "Mora-v0.3.2.apk"
$sidecar = "Mora-v0.3.2.apk.sha256"
$expected = ((Get-Content -LiteralPath $sidecar -Raw).Trim() -split "\s+")[0]
$actual = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
if ($actual -ne $expected.ToUpperInvariant()) {
    throw "Release APK SHA-256 does not match its sidecar."
}
```

Then use Android Build Tools 36:

```powershell
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
& "$sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs -Werr $apk
& "$sdk\build-tools\36.0.0\aapt.exe" dump badging $apk
```

Confirm all of the following before installation:

- package: `de.unbow.mora`;
- `versionName`: `0.3.2`;
- `versionCode`: `6`;
- `minSdk`: `26`;
- `targetSdk`: `36`;
- signer certificate SHA-256 equals
  `docs/mora-release-certificate.sha256`;
- APK SHA-256 equals the sidecar and is recorded for the release report.

Do not print or record private-key material, passwords, or protected secret values.
The certificate fingerprint and APK checksum are public integrity data.

## One-device smoke gate

Use the exact verified Draft APK on one current Android phone. Keep this to a
short, release-focused session:

1. when possible, install it as an in-place update over public v0.3.1 and confirm
   recent-document state remains available;
2. launch Home and open a real, sanitized, long Markdown document;
3. scroll, use the table of contents and search, return Home, reopen from Recents,
   and confirm reading position restoration;
4. rotate to landscape, open Reader Appearance, and reach all three sliders and
   Restore defaults by display or scrolling without system-navigation overlap;
5. edit one small passage, save to the original writable file, reopen it, and
   confirm the exact edit;
6. trigger Save rapidly and edit once during a save; confirm there is only one
   active write for that document and a later edit remains dirty;
7. complete and cancel predictive Back without a crash, flash, state loss, or an
   incorrect destination;
8. switch through Indigo, Pine, and Night and confirm every launcher alias still
   launches Mora.

The following are useful future coverage but do not block v0.3.2:

- Android 8 and every OEM or file provider;
- a complete 200% font-size and accessibility matrix;
- every theme, orientation, and navigation-mode combination;
- automatic layout reflow of an off-screen App Settings language row during the
  system's predictive-Back scale transform;
- any absolute APK-size target, provided shrinking is effective and behavior is
  correct.

Record the device model, Android version, file provider, upgrade result, failures,
APK byte size, and approved Draft SHA-256.

## Publish the same Draft

Only after every blocking check above passes:

1. replace the placeholder Draft notes with concise English and Chinese v0.3.2
   release notes;
2. edit that same Release to `draft=false`, `prerelease=false`, and latest;
3. leave the tag, APK, and sidecar unchanged.

Do not delete and recreate the Release, move the tag, or replace either attachment.

After publication, download the public APK and sidecar again. Verify the sidecar
and confirm that the public APK SHA-256 is identical to the approved hidden Draft
APK. Also confirm that the public Release is latest and that both attachments are
downloadable.

## Recovery and rotation

- A transient infrastructure failure may be rerun against the same immutable tag.
- If the tag workflow requires a code change, stop v0.3.2 publication and prepare a
  later version. Never force-update the tag.
- If a blocking defect appears after public release, keep the attachment and tag
  unchanged and fix it in a later version.
- Do not rotate the app-signing key unless Android's supported key-upgrade path and
  every distribution channel have been reviewed.
- If the key may have leaked, stop publishing, restrict the affected automation,
  and document the incident before taking action.
- If a backup cannot be restored, do not publish. Repair the backup set while the
  current key is still available.
