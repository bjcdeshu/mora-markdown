# Mora Release Guide

Mora uses one long-term Android app-signing key for every public APK. Losing that
key or its passwords prevents existing users from installing future GitHub builds
as updates. Treat this document as a release gate, not as a convenience checklist.

Stable v0.3.3 uses one exact signed candidate and one short Android 16 / API 36
emulator smoke session. This physical-device exception was explicitly approved
because v0.3.3 changes only launcher resources, version metadata, and brand
documentation:

```text
Pull-request and final main CI green
→ create annotated v0.3.3 tag on that final main commit
→ tag workflow creates a signed hidden Draft Pre-release
→ independently verify that exact Draft APK once
→ run one short smoke session on one Android 16 / API 36 emulator
→ edit the same Draft to stable/latest
→ redownload the public attachment and confirm SHA-256 is unchanged
```

The v0.3.3 publication path uses no separate protected manual candidate and no
second full device matrix. Pull-request and `main` CI already exercise the
minified Release pipeline with an ephemeral non-release key. The retained manual
dispatch can produce a protected signed Actions artifact for another explicitly
approved purpose, but it is not used for v0.3.3 and creates no GitHub Release; only
the tag-triggered run creates the distributable v0.3.3 Draft candidate.

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

### Automated document-provider instrumentation

Android CI also boots one Android 16 / API 36 emulator and runs:

```bash
./gradlew connectedDebugAndroidTest
```

This job talks directly to a deterministic `androidTest` DocumentsProvider; it
does not automate the system file picker. It covers strict and bounded provider
reads (including absent or false size metadata), malformed UTF-8, provider and
session write flags, temporary versus persistable URI grants, provider-side
revocation, write-and-reopen verification, external content-version changes,
slow bounded reads, and provider read/write failures. The failure fixtures also
cover providers that truncate a target or persist only a prefix before reporting
a write error, so the recovery contract cannot assume a failed write left the
original target intact.

The same emulator job exercises the ViewModel and Compose state machine against
that provider: read-only Save As, permission loss, conflict rechecks, Save Copy,
Reload, dirty recovery after a process-like ViewModel restart, failed-save
Retry/Cancel, dirty-close choices, incoming-request replacement protection, and
pending failure/post-save continuation across Activity recreation. It also checks
managed-grant release/retention and the external `application/markdown` intent
contract without launching the system picker.

This is a repeatable SAF contract gate, not a claim that every Android storage
provider behaves identically. It does not exercise picker UI, OEM file managers,
networked cloud-provider lifecycle, or provider-specific permission restoration.
The repository API also cannot promise prompt cancellation of an already-blocked
provider stream because `ContentResolver.openInputStream` does not accept a
`CancellationSignal`; the slow-read fixture therefore asserts the byte bound and
completion deadline, not transport-level cancellation.
When the maintainer explicitly accepts emulator-only validation for a release,
record that omission and use this green API 36 job plus the exact-candidate
emulator smoke as the storage baseline.

### v0.4 real-device and provider gate

Emulator-only validation is sufficient for development pull requests, but it
does not authorize a stable v0.4 tag. Before publishing stable v0.4, install the
exact signed candidate on real hardware and record device, Android version,
provider name/version, scenario, result, and evidence for every row below:

| Provider or condition | Required candidate checks |
|---|---|
| Local storage / Downloads | Open → edit → save → reopen; process kill with dirty edits; process kill during save and recovery export |
| Read-only DocumentsProvider | No in-place Save; Save As and Save copy succeed without changing the source |
| Representative sync or cloud DocumentsProvider | External modification conflict; Reload; confirmed Overwrite after a second version check; Save copy leaves the source unchanged |
| Persisted permission revoked between sessions | Reopen and Save fail safely with a specific permission path; Retry/Save copy keep the edit available |
| Recovery decisions | Recover, Save copy, and explicit Discard preserve or remove only the selected recovery record |
| Support and launcher surfaces | About links, diagnostics allowlist/copy confirmation, and all launcher aliases work on the candidate |

Any missing row, unexplained failure, or candidate rebuild blocks the stable
tag. A maintainer may explicitly defer this matrix for a development snapshot or
pre-release, but must record the omission and must not describe that build as
having passed the stable provider gate.

Pull-request and `main` CI repeat that Debug gate and then run
`lintRelease assembleRelease` with a runner-generated one-run key. The job checks
that R8, resource shrinking, mapping generation, Release packaging, and signature
verification complete. The Release pre-build also resolves
`releaseRuntimeClasspath`, maps every external module to a documented license
family, and writes the exact coordinate audit to
`app/build/reports/runtime-third-party-notices.txt`; an unknown module or missing
notice fails the build. It confirms that this temporary signer does not equal
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

Before creating `v0.3.3`, confirm:

1. the release pull request is reviewed and its Android CI job is green;
2. final `main` CI is green on the exact commit to tag;
3. `versionName` is `0.3.3` and `versionCode` is `7`;
4. `CHANGELOG.md` contains exactly one dated `## [0.3.3] - YYYY-MM-DD` heading;
5. all intended code, launcher resources, screenshots, and documentation are in
   that commit;
6. there are no uncommitted or unpushed release changes;
7. the repository's `v*` tag ruleset prevents deletion and non-fast-forward
   updates.

Create and push the annotated tag on that exact `main` commit:

```powershell
git tag -a v0.3.3 -m "Mora v0.3.3"
git push origin v0.3.3
```

Never create the tag speculatively. If code must change after it is pushed, do not
move or replace the tag; stop v0.3.3 publication and prepare a later version.

## Tag workflow and hidden Draft

The `v0.3.3` push starts **Signed Android release**. The workflow:

1. checks that the tag is valid and its commit is contained in `main`;
2. enters the protected `release-signing` Environment;
3. runs unit tests, Release lint, R8, resource shrinking, and the Release build;
4. verifies package metadata, minimum and target SDKs, signing validity, and the
   registered certificate fingerprint;
5. produces `Mora-v0.3.3.apk` and `Mora-v0.3.3.apk.sha256`;
6. retains the official R8 mapping output as a private Actions artifact, not a
   public Release attachment;
7. creates a hidden Draft Pre-release for the tag.

The Draft APK is the only signed candidate that receives the v0.3.3 identity and
emulator gate.

## Public release candidate

This path is used for v0.4.0-rc.1 public testing. It was not used for v0.3.3 and
does not alter either the historical v0.3.3 gate or the stable v0.4 gate.

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

### v0.4.0-rc.1 exact-asset check

For the first v0.4 candidate, download `Mora-v0.4.0-rc.1.apk` and its `.sha256`
sidecar from the public Pre-release, not from a local rebuild or only from the
intermediate Actions artifact. Reuse the checksum, `apksigner`, and `aapt`
commands in the next section with those filenames, then confirm:

- package: `de.unbow.mora`;
- `versionName`: `0.4.0-rc.1`;
- `versionCode`: `8`;
- `minSdk`: `26`;
- `targetSdk`: `36`;
- signer certificate SHA-256 equals
  `docs/mora-release-certificate.sha256`;
- APK SHA-256 equals the sidecar and is recorded before any installation or test.

Keep the Release marked as a Pre-release and retain its bilingual pending-matrix
and backup warning. A fix after the tag requires a later immutable RC tag and a
higher `versionCode`; never move the tag or replace either attachment.

## Verify the exact Draft APK

Wait for the tag-triggered **Signed Android release** workflow to succeed. Download
the APK and sidecar from the hidden Draft Release, not from a local rebuild and not
only from the intermediate Actions artifact.

Verify the sidecar from the directory containing both files:

```powershell
$apk = "Mora-v0.3.3.apk"
$sidecar = "Mora-v0.3.3.apk.sha256"
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
- `versionName`: `0.3.3`;
- `versionCode`: `7`;
- `minSdk`: `26`;
- `targetSdk`: `36`;
- signer certificate SHA-256 equals
  `docs/mora-release-certificate.sha256`;
- APK SHA-256 equals the sidecar and is recorded for the release report.

Do not print or record private-key material, passwords, or protected secret values.
The certificate fingerprint and APK checksum are public integrity data.

## One-emulator smoke gate

Use the exact verified Draft APK on one Android 16 / API 36 emulator. Keep this
to a short, release-focused session:

1. install it as an in-place update over public v0.3.2 and confirm
   recent-document state remains available;
2. confirm the selected folded-passage mark is comfortable in the launcher and a
   launcher folder on light and dark wallpaper, with no clipping, excessive
   weight, color error, or pressure against the available circle/squircle mask;
3. inspect the icon in recent apps and system App info when those surfaces expose
   it, and check the themed/monochrome icon when the launcher supports it;
4. switch through Indigo, Pine, and Night and confirm every launcher alias uses
   the same silhouette and still launches Mora;
5. launch Home, open and read a sanitized Markdown document, return Home, and
   reopen it from Recents;
6. capture one final simulator launcher screenshot and compare it with the
   approved small-size design.

The following are useful future coverage but do not block v0.3.3:

- Android 8 and every OEM or file provider;
- a complete 200% font-size and accessibility matrix;
- every theme, orientation, and navigation-mode combination;
- automatic layout reflow of an off-screen App Settings language row during the
  system's predictive-Back scale transform;
- any absolute APK-size target, provided shrinking is effective and behavior is
  correct.

Record the emulator profile, Android version, upgrade result, failures, APK byte
size, and approved Draft SHA-256. Physical-device coverage is an explicitly
accepted non-blocking omission for this resource-only release.

## Publish the same Draft

Only after every blocking check above passes and the maintainer has explicitly
approved publication after this emulator gate:

1. replace the placeholder Draft notes with concise English and Chinese v0.3.3
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
- If the tag workflow requires a code change, stop v0.3.3 publication and prepare a
  later version. Never force-update the tag.
- If a blocking defect appears after public release, keep the attachment and tag
  unchanged and fix it in a later version.
- Do not rotate the app-signing key unless Android's supported key-upgrade path and
  every distribution channel have been reviewed.
- If the key may have leaked, stop publishing, restrict the affected automation,
  and document the incident before taking action.
- If a backup cannot be restored, do not publish. Repair the backup set while the
  current key is still available.
