# Native Kotlin protocol and wallet compatibility

Use a Kotlin/JVM shared core and Jetpack Compose native UI. Directly implement
the inspected Saito key/signature and binary peer contracts, verified against
independent pinned Rust/WASM vectors. A hidden web app is not the native runtime.

The wallet boundary uses Bouncy Castle's lightweight BLAKE3 and secp256k1
primitives: hash bytes once, RFC6979 HMAC-SHA256 signing, compact R/S and low-S.
Pin Bouncy Castle 1.86, the current September 2026 stable release, including
ECDSA timing hardening. The initial 1.81 proof pin was replaced before review
distribution. Verify unchanged independent signatures after upgrades. See the
[official release notes](https://www.bouncycastle.org/download/bouncy-castle-java/).
Public keys encode raw compressed points in Base58. Preserve complete imported
wallet option data for compatible re-export, while keeping remote endpoints inert.

This avoids embedding a browser/WASM bridge and gives Android control over
persistence, lifecycle and signing. It creates an explicit compatibility
maintenance obligation: pin source/version references, keep independent vectors,
and verify both supported backends before claiming interoperability. See
[the inspected native contracts](../research/NATIVE_SAITO_PROTOCOL.md).

Kotlin/JVM core boundaries stay distinct from Red Square and Chat screens.
Android 10 is the minimum OS. Compile/target SDK 36 is the current tested build
choice, not an assertion about future Play eligibility. Verify release policy
when preparing the store submission.
