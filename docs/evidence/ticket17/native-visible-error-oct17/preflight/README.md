# Public video observer preflight

Source `7d9e1d0ca6c995139c0cf0b8f59464471bc4748e`, reviewed against
`c0f2da7e3de00b15c1953db26416f90ed427899b`. Both source review axes report
zero outstanding findings after correction. This prepares one changed instance
of the existing Android17 public video check. It does not prove playback or a
provider failure cause, and #17 remains open.

The instrumentation publishes a bounded request tied to its process, selected
public reference and fresh nonce. Before the existing deliberate Play action,
it requires the same request back as readiness. The existing diagnostic helper
arms an isolated observer of only the provider's reason and subreason nodes.
It rejects hidden or clipped ancestors before reading those fields, bounds each
text to512 characters and disposes the observer before replacement or CDP close.
An outer instrumentation finally covers handshake cleanup even if peer startup
or Activity launch fails. Actual Playing, no-autoplay, close, feed and zero
Saito-write assertions remain unchanged.

Review found a cooked-template regex bug and incomplete observer/handshake
cleanup. The retained RED output demonstrates the original generated expression
failing whitespace normalization. The corrected controlled check additionally
covers literal s preservation, initial and mutation-triggered observation,
hidden/clipped ancestors without text reads, text bounds and observer/timer
cleanup. It uses a controlled DOM in Node24.13.0 with network disabled, not a
browser or YouTube. This check does not establish CDP/ADB synchronization or
actual Android behavior. The host had no Node command; validation used the
existing pinned runtime in a disposable read-only container, with no install.

The initial APK build at747f1408 passed in3m23s. Its output is retained separately
from the corrected candidate's build, which passed in1m46s (debug and test APK).
JavaScript syntax, shell syntax and
`git diff --check` passed for the corrected source. No production code, identity,
header, login, proxy or provider settings changed; beta24 is unchanged.
