# Native shell design preview (#85)

Question: can David review the complete Red Square + Stack UI in actual Android
while common design changes arrive from his VPS without another APK build?
This isolated prototype is captured on branch `prototype/native-shell-live-design`.
It is not the production Saito integration.

## One design, native components

`shell-ui` holds the native Compose renderer and presentation components.
`shell-preview` is a small Android host, package
`nl.davidengelen.redsquare.design`, launcher **Red Square Design**. Its debug
package ends in `.dev`. Neither package initializes the ordinary wallet, push
transport, node connections or transaction submission.

`design.json` is the editable source of screen composition and sample data.
It currently describes 60 routes: Red Square feeds/search/activity/thread,
compose/reply/quote, profiles, private/group Chat and membership, wallet
import/export/receive/send, settings, and Stack discovery/following/author
feeds/reading/access, drafts, visual/Markdown writing and publication review.
All write/payment/import actions demonstrate a local preview flow.

The top Apps launcher and account menu are shared. Bottom navigation belongs to
the selected module. Each module keeps its route, list positions and in-memory
edits during switching and live design updates. Drafts are sample session data;
process-death/reinstall recovery belongs to subsequent functional integration.

## Fast iteration

```sh
scripts/design-preview validate
scripts/design-preview publish-design
```

The publisher validates the source and atomically updates only
`/home/david/projects/docker/website/html/redsquare/design/`. It changes no
Saito service, ingress or ordinary review APK. Every published content change
gets a derived revision. The foreground app checks for changes every two
seconds. A failed fetch keeps the last usable design. Returning to the app
resumes checking. The bundled design also makes first launch usable offline.

Without rebuilding, change:

- Colours, per-role typography size/line-height/weight under `tokens.type`, global
  text scale, reader type size, header height, margins, spacing and card/control
  corner radii.
- Copy, sample content, article images and account identity/menu content.
- Modules, tabs, routes, and ordering/composition of supported native blocks.
- Buttons, internal destinations, form fields, choices, toggles, and conditional
  fields controlled by another field or toggle.

Supported blocks are validated by the publisher. New block implementations,
new platform capabilities, parser/editor behaviour or new network integration
require Kotlin changes and another APK. Remote configuration is data only.

The visual editor uses Compose Rich Editor `1.0.0-rc13`, pinned for the existing
Kotlin 2.1 toolchain. Its underlying draft is Markdown. Untouched source is
not normalised just by switching modes. Tables, raw HTML and footnotes stay in
source mode in this prototype to avoid silent conversion loss; comprehensive
Stack-format compatibility remains functional integration work.

## Build and deliver

```sh
scripts/design-preview build
scripts/design-preview publish .scratch/design-builds/<version>
```

Builds use the existing container and protected signing identity. The design
package has its own version ledger and publication directory. Actual APK
package/certificate/hash are checked before publishing. The install page is
<https://davidengelen.nl/redsquare/design/>. Ordinary beta83 stays available at
its existing page. Design updates do not require touching signing material.

Review settings are under the avatar: theme, loading/empty/error/offline sample
states and live revision. Sample QR/address/balances are explicitly examples.
Clipboard actions copy only example text; no wallet material is generated.

## Native observations

The existing Android proof workflow's `design-preview` scope calls the isolated
preview workflow. It builds only the preview and runs on disposable Android 10
and 17 emulators. The observer uses actual native controls to switch modules,
edit Markdown, return to the same draft and open publication review. It also
captures native dark/light/large-text views and three live config changes
without reinstalling.

`live-update-timing.json` records a conservative device-clock interval from
publication to a subsequent Compose frame, plus a separate interval to native
UI observation (which includes UIAutomator polling overhead). Measurements use
an isolated loopback server in CI. They do not establish Internet round-trip
latency or physical Pixel 7 performance. The public configuration's HTTPS
publication is verified separately.

## Sample artwork

The two sample cover photographs are fetched from Unsplash's image CDN. Source
URLs and exact file hashes are in `assets.json`; files are checked into this
prototype branch and served from David's VPS. They are illustrative sample
covers, not asserted to be actual Saito article metadata.

The observer additionally watches three changes from the isolated public HTTPS
feed at `/redsquare/design/proof/design.json`, generated by
`scripts/design-preview-proof-feed`. It brackets publication between the last
old HTTP response request and the first new response completion. Android
`elapsedRealtime` is calibrated against the observer's monotonic clock with an
ADB interval, avoiding a wall-clock assumption. The result is a conservative
publication-to-frame range in `public-vps-timing.json`. The normal design feed
is never changed by this probe. Stop its bounded producer after verification.
