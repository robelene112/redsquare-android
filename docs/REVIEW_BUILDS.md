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

## Final owner check on Pixel 7

Tracked in #35, after the owner requested app review at completion:

1. Install the completed signed preview from the stable page.
2. Record its public receiving address and create a wallet backup through the
   authenticated app flow. Keep private backup contents out of screenshots.
3. Create a local draft and note cached feed/Chat items available for review.
4. Install the next signed preview through the same page, without uninstalling.
5. Verify the public address, draft, cached items and backend choice remain.
6. Relaunch the app and repeat the continuity check.

Automated Android 10/17 emulator checks complement this physical check. They do
not establish the Pixel's performance or physical installation behavior.
