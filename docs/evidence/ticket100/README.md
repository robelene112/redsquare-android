# Robust directory display names, issue100

Review base: `1f12812635690547656e6373f0a45a5c85542780`, accepted beta108.
Beta108 stays public until replacement acceptance. This packet is in progress.

The owner supplied the same signed conversation on Android and Saitoverse.
`oT3f7KeqeeG6VRBVvWKJpDNtigRUMYXUgtu3ZjNrNUrZ` is reported as
`digirok@saito`; `i6LwT9rFjrsNgExzLeSghGqjHhaNwwjeDrsLr2LkTUk8` as
`CryptoKen@saito`. Fresh public read-only native-protocol probes found empty
reverse maps and unanswered namechecks on the owner node, but both mappings
on saito.io. CryptoKen's current registrar proof verifies; digirok's historical
cached record does not verify, even against its historical signer. No alternate
trust key or weakened ownership verifier is used.

The [pinned official Registry implementation](https://github.com/SaitoTech/saito/blob/e6c937c686495a37d8fc94ecc894189e23a453c3/node/mods/registry/registry.js)
uses reverse maps for browser labels separately from registration proofs.
The app follows that display distinction while preserving original signing keys,
strict registration/availability checks and the verified-name cache. Post text
never supplies an authenticated author name.

The actual Android10 [RED run42987309843](https://github.com/robelene112/redsquare-android/actions/runs/42987309843)
on `0db5ee942ab7d9d1c6dd720337c93c97a8a2af47` shows a genuine signed post but
cannot display its directory name within the original20-second goal. The first
implementation [run42990063478](https://github.com/robelene112/redsquare-android/actions/runs/42990063478)
on `2c87f2ce9` reaches the named profile and full-key identity dialog, then fails
an ambiguous test selector that also matched the background profile. It remains
failed, not accepted. The selector now requires the name beside the exact key.

The actual [recovery RED run42990483914](https://github.com/robelene112/redsquare-android/actions/runs/42990483914)
on `9c8341f81` cannot recover after two directory errors without navigation,
failing the unchanged20-second goal. A core tracer separately proves that an
error object was incorrectly treated as a successful absence. The correction
classifies the read as failed and retries only still-visible identities with
bounded backoff, independently of the selected node's ownership checks.

Large original native artifacts, probes and capture hashes remain outside Git
at `/mnt/storage/redsquare-android-artifacts/identity100`. Final source, complete
Android10/17, signed-update and delivery evidence will be recorded here before
closing100. Parent1/36/55/84/86 remain open; no phone QA or node changes are used.
