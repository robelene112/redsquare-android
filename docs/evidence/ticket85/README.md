# Issue85 native design preview

- Install: https://davidengelen.nl/redsquare/design/
- Package: `nl.davidengelen.redsquare.design`, version `design.1` (code1).
- APK source: `a679952be769313799b6d68cb8a9c923b20a8072`.
- APK SHA256: `2c84183758a0431a2a01c7428799b8d12c5c015938316a730a2324f1b6c66cf5`.
- Certificate SHA256: `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.
- Live configuration revision: `design-3d989e9e8406`.
- Initial release build:206seconds on the configured VPS build container, with
  existing dependency/Gradle caches. This is not a cold environment measurement.

The public HTTPS APK download matches the signed local artifact. Package,
version and signing certificate were checked from the actual APK. The design
package installs beside the ordinary application and does not initialise its
wallet, transactions or push transport.

## Verification

Final Android10/17 emulator run:
https://github.com/robelene112/redsquare-android/actions/runs/41718412451

Both Android10/API29 and Android17/API37 pass on verification commit
`3442f0558`. The disposable native checks cover module switching, exact retained
Markdown and Red Square drafts, optional article sharing, the requested locked
article access flow, dark/light,150% system text and411x914 logical viewports.
This matches Pixel7's logical layout dimensions at420dpi, rather than claiming
that a small emulator panel became a physical Pixel7.

Actual Android17 captures: [Red Square](home-pixel7-viewport.png),
[Stack](stack-pixel7-viewport.png), [visual editor](visual-editor.png),
[publication review](publish-review.png), [active access](requested-article-unlocked.png),
[large text](home-large-text.png), [light](home-light.png).

### Public HTTPS update timing

Three updates per OS arrived from the separate public VPS proof feed without
another APK. Each interval brackets publication-to-subsequent-Compose-frame
latency, including a conservative HTTP publication and device-clock calibration
uncertainty. These are observed bounds, not a latency guarantee.

| OS | Update1 | Update2 | Update3 |
| --- | --- | --- | --- |
| Android10 |1.722–3.342s|1.108–2.169s|0.069–1.136s|
| Android17 |1.694–2.997s|1.215–2.227s|0.359–1.369s|

Raw bounds: `android10-public-vps-timing.json` and
`android17-public-vps-timing.json`. Separate loopback measurements are retained
as `android10-live-update-timing.json` and `android17-live-update-timing.json`;
those are not Internet latency measurements. UIAutomator observation includes
its polling overhead and is separate from the frame timestamp.

Earlier failed runs remain failures. They exposed synthetic key-input pacing,
HTTP client identification and preserved-scroll observation issues. The final
run retains exact content assertions and verifies actual applied viewport bounds.
The bounded public proof producer is stopped after this verification.
The paired specification and standards reviews report no outstanding findings.
The observer changes after the signed source do not change the APK's native
implementation, resources, dependencies or bundled design.

## Evidence limits

This is a native clickable UI shell with sample content and local actions.
It does not establish Saito Stack interoperability or send real payments,
publications, imports or messages. Module switching and live updates retain
in-memory work; process-death persistence belongs to functional integration.
Pixel7-sized layout checks do not prove physical Pixel7 performance. Parent55
remains open for the owner's visual review; specification1 and release36 remain
open for actual product/release acceptance.
