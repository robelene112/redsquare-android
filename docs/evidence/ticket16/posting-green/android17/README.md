# GIF posting and fresh-Activity checkpoint

Native source92845df998cbb3fac08d0ae403a40857190448a3, Android17 run39011213336
SUCCESS. GiphyPreviewTest:2 cases/0 failures/37.359s. GiphyPostingTest:6 cases/0
failures/25.056s. Separate source39011213667 SUCCESS. Local corrected endpoint
build30442 PASS1m47s. Standards and Spec reviews have no outstanding findings.

The native journeys prove selected GIF frames/clipping, exact original URL/query,
visible provider/creator/source credits, saved draft reopening through a fresh
Activity/VM, removal preserving authored text, and no Saito writes before Send.
The fresh launch uses normal ActivityScenario close/launch, not process death.
All six post/reply/quote combinations use explicit owned HTTP and Saito peers,
including the official backend binding. The signed original URL and authored
text are indexed and visible through the normal UI; reply/thread, quote original,
recipients, message limit and no private draft attribution in payload are checked.
No live Giphy query, production transaction or real key is involved.

All21 XMLs parse. Root viewed all9 preview PNGs, including fully visible selected
and fresh-Activity credits. The Standards reviewer viewed all12 posting PNGs
and paired XMLs; root additionally viewed owner post/reply and official quote
indexed captures. **Exclude owner-post-indexed.png from visible indexed-post
claims:** its bitmap shows the older reference card while the paired XML and
native display assertion contain the new post. Available-state quote captures
leave much of the selected GIF below the viewport. Posting captures do not
prove creator/source credits or animation; separate preview observations cover
those behaviors. No additional clipping/overlap was observed.

## Independent controlled interoperability

The six actual emitted public test frames are checked offline by
`build-support/references/verify-gif-frame.cjs`: pinned WASM0.3.2/Elliptic6.6.1,
outer and quoted signatures, routed hop, exact original URL/query, authored text,
recipients, reply/thread linkage, embedded original and body limit. All six pass.
The WASM hash is f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e.

`gif-web-render.cjs` executes the unmodified A/B Tweet/template graphs pinned to
9a4ca7e9442eb5e872168ced361ce741d7d139d7 and214504ecece76d1cd20a245fe4468eccbfca2951.
All12 source hashes match the accepted ticket14 baseline. All six frames retain
exact GIF URL, commentary and ordered original images in generated HTML.
`gif-web-browser.cjs` then proves actual offline Chromium DOM attributes and owned
GIF16x16/image12x8 decode for both pins and each frame. Browser requests are
fulfilled only for the exact owned GIF URL; other requests abort. These browser
checks do not prove animation. Native frame observations remain separate.

**Known partial web compatibility:** for quotes, these pinned clients normalize
the authenticated original text but omit embedded.app. Their template therefore
omits the original text body. Original identity and images remain rendered.
This matches the existing ticket14 result and quote proof plan. The corrected
check asserts and reports the limitation without injecting app or modifying the
web source. `interop/owner-quote-web-failure.txt` retains the earlier mistaken
oracle failure requiring visible original text. It is not an application RED.
Android quote rendering preserves the original text.

All six final frame/template/browser outputs are retained under interop. Tools
were reviewed through96ed9094; ordinary browser decode tool is unchanged since
0fefba85. The runner reuses completed frame/browser outputs when their inputs
are unchanged. Render HTML before/after the oracle correction is identical for
post/reply cases. Docker uses read-only named public dependency/source mounts,
network disabled and an empty environment. No Saito application/node initializes.
Actual reference Node is24.20.0 from the retained Playwright1.63.0 image.

Whole #16 remains open: the additional search journey, final Android10/17 checks
and live authorized Giphy acceptance remain. Queue credits and external source
navigation are source-reviewed only. No new beta, key/account or node change.
