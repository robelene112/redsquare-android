# Android review builds

The stable development review page is `https://davidengelen.nl/redsquare/`.
It is separate from the Saito nodes and serves APKs, build notes and public
checksum/version metadata. The initial wallet-only preview is not the
wallet-import/live-feed milestone or the completed application.

Run `scripts/review-build` to assemble, sign and verify a release APK. Run
`scripts/review-publish .scratch/review-builds/<version> --notes '<build notes>'`
to publish the verified artifact. Publishing writes only the `redsquare/`
directory under the existing website's static root; it changes no ingress,
container configuration or Saito service. Existing release directories are
immutable and the latest manifest changes atomically after its APK exists.

The isolated timing app is built with `scripts/review-build --performance`.
Artifacts go to `.scratch/performance-builds/<version>`, using the same signing
identity and shared version counter. It has package
`nl.davidengelen.redsquare.performance`, a separate launcher label and storage,
and is nondebuggable/profileable. It uses independent `pushPerformance*`,
`giphyPerformancePostsApiKey` and `giphyPerformanceChatApiKey` options. No Release
key fallback or ordinary-app update prompt is enabled in this package.

Publish that artifact only with
`scripts/review-publish .scratch/performance-builds/<version> --performance --notes '<notes>'`.
The dedicated page is `https://davidengelen.nl/redsquare/performance/`; its latest
manifest and immutable releases are separate from the ordinary preview. The
default publisher continues to accept only the ordinary package. A published
timing candidate is not physical performance or completed release acceptance.
Do not import the owner's private wallet for timing checks.

The optional workflow `performance_version` input installs the exact immutable
signed performance APK alongside accepted immutable beta29 by default on a
disposable emulator. Optional `main_reference_version=35` selects the current
ordinary preview with its independently pinned hash;29 remains the default.
The observer records separate public wallet identities/UIDs and verifies the
ordinary preview's address remains unchanged. It requires both packages absent
before setup and restores that absence afterward; it never performs this
installation or cleanup on a physical device. Its actual artifacts and public
UI observations are installation diagnostics, not Pixel latency measurements.

For an explicit Android17 16 KB environment, select `android17_16kb=true` with
an Android17 scope (or combined `full`). The hosted runner selects
`system-images;android-37.0;google_apis_ps16k;x86_64` in its own AVD directory.
Before installing the app, it retains the installed image properties, build/ABI
and actual `getconf PAGE_SIZE`, requiring16384 and x86_64. Ordinary targets and
scopes keep their default images. Full17 still requires all three partitions
and every existing identity/assertion/deadline. Actual complete51 execution on
revision7/x86_64/16384-byte pages passes431 identities, with actual signed35/36
wallet isolation. See [the exact scope](evidence/ticket51/README.md). This does
not establish ARM64, physical timing or Play-generated installation.

The application ID is `nl.davidengelen.redsquare`. The `.dev` debug application
and its synthetic tests are separate from the owner preview. Preview versions
increase monotonically; interrupted builds may leave gaps. The signing identity
must remain the same throughout review and future distribution. Losing or
changing it breaks ordinary in-place updates.

`scripts/signing-init` keeps the PKCS12 signing key and generated password file
under `~/.local/share/redsquare-android/signing/`, outside Git and public hosting.
The directory is mode 0700 and key/password files are 0600. The version counter
and established public certificate fingerprint live in the adjacent `review/`
directory. Never print, commit or upload the key or its password file. Make an
operator-managed encrypted recovery copy before relying on the key for public
distribution; the repository and hosted artifacts are not a signing-key backup.
Play App Signing configuration must preserve the chosen distribution identity.

On Android, download the APK and follow the system installation prompt. The
browser may need one-time permission to install apps. Install newer APKs over
the existing app. Do not uninstall or clear its storage to update it.

Wallet options are encrypted using an Android Keystore AES-GCM key in the
application's private no-backup directory. Wallet creation and disk/Keystore
operations run off the UI thread. A saved wallet that cannot be decrypted is
kept intact and the app shows a recovery error instead of creating another
identity. This encryption does not replace an authenticated wallet export:
uninstalling, clearing storage, losing the phone or losing its Keystore key can
require restoring an external wallet backup. Automatic device backup is disabled.

## Offline Play bundle preparation

Run `scripts/review-build --bundle` to reserve the next shared version and build
an ordinary Release App Bundle, with `REVIEW_BUILD=false`. It uses the same
protected signing identity/lock as preview and performance builds. Outputs are
`.scratch/play-builds/<version>/redsquare.aab`, a locally generated `universal.apk`
and actual verification outputs/`manifest.json`. This mode does not publish
anything. Do not pass a Play directory to the APK preview publisher; it has no
publishable `redsquare.apk`.

Official bundletool1.18.3 is downloaded atomically to
`${XDG_CACHE_HOME:-$HOME/.cache}/redsquare-android/bundletool-1.18.3` and checked
against its pinned SHA256 on every use. Set `REDSQUARE_BUNDLETOOL_DIR` to a
writable cache on the VPS additional disk when appropriate. No tool JAR or
signing credential belongs in Git. `scripts/play-bundle` is the internal adapter,
called only from the canonical builder while it owns the shared build lock.
The existing Docker build image supplies JDK/Android signing tools; no global
SDK or tool upgrade is required for this mode.

The metadata records enabled-state booleans from generated Release configuration;
artifact acceptance also inspects the actual derived APK DEX without retaining
option values. Push, Posts GIF and Chat GIF configuration remain independent.
Changing those options requires a fresh reserved build and corresponding native/
physical/provider proof. The bundle and local APK's matching certificate do not
prove Play App Signing enrollment or a genuine Play-track update. Resolve signing
continuity before enrollment, retain an operator-managed encrypted recovery copy,
and never export/enroll keys as a side effect of a build.

See the concrete [Play release package](release/PLAY_RELEASE_PACKAGE.md) and
[packaging evidence](evidence/ticket49/README.md). Publisher, policy, operational,
physical and final submission gates remain open under36. Native35 retains the
accepted development preparation.

## Final owner check on Pixel 7

Tracked in open36 PIXEL_CONTINUITY, transferred from native35 by the owner
instruction of2026-10-19 to park phone checks until app review:

1. Install the completed signed preview from the stable page.
2. Record its public receiving address and create a wallet backup through the
   authenticated app flow. Keep private backup contents out of screenshots.
3. Create a local draft and note cached feed/Chat items available for review.
4. Install the next signed preview through the same page, without uninstalling.
5. Verify the public address, draft, cached items and backend choice remain.
6. Relaunch the app and repeat the continuity check.

Automated Android 10/17 emulator checks complement this physical check. They do
not establish the Pixel's performance or physical installation behavior.
