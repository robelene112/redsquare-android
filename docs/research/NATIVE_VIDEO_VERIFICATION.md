# Native YouTube verification

Active #17, fixed review base711e42e. Parent approved seams1–4 apply: observable
native feed/thread flows against controlled Saito and provider boundaries;
verified signed content, failure/lifecycle recovery and pinned web behavior.
Read docs/research/NATIVE_GIF_VIDEO_CONTRACT.md for source/API provenance.

Completed #15 has full Android10/17 acceptance. #16 remains open with an external
Android-authorized Giphy-key/URL-retention dependency; no credentials were reused.
#17 is independently ready (native prerequisite#8 closed), assigned sequentially.
No node, wallet configuration, production transaction or store write.

First core tracer RED: compatible first YouTube link API absent, compile failed
in24s. GREEN: exact M7lc1UVf-VE canonical target passes in42s. UI tracer launches
the native feed with a controlled signed post and provider boundary. It requires
zero provider requests before a visible tap, player readiness after tap, close
back to the retained post and no emitted transaction. Provider code deliberately
has no playVideo implementation, so automatic playback cannot pass readiness.
An initial local test-compile attempt used a nonexistent transaction timestamp
property; corrected to the fixture's explicit public timestamp. No runtime
result is claimed until the hosted tracer completes.


Explicit signed-link tracer RED23s (property absent), GREEN46s. Verified archive
content now exposes only bounded string links; malformed link metadata does not
replace authenticated text. Real host/path boundaries and 11-character IDs are
required before constructing a fixed HTTPS playback target. Primary YouTube
IFrame/client-identity/security documentation rechecked2026-10-10, including
installed package name as base URL and error153. Source note has direct links.


Android17 RED38078560019 at64bab9c reaches the controlled signed post then fails
because Watch on YouTube is absent (9.924s). Original screenshot shows its link,
with leading text clipped in the small development viewport; retained with that
limit. First player/core/test compilation PASS; before runtime, API33-only
InputStream.readNBytes was replaced with bounded Okio buffering for Android10.
Provider failure releases the WebView; renderer-crash cleanup avoids querying a
dead renderer. The unchanged tap tracer additionally requires the actual API
request Referer to identify the installed .dev package and autoplay0/correct ID.

First player runtime at18027d6 did not pass: Android10 run38079399700 and
Android17 run38079401831 both timed out waiting for provider readiness
(22.570s/31.930s test totals). Individually inspected original failure images
show the YouTube dialog with the load-failure explanation, Retry, Close video
and Open in YouTube. They establish the visible failure state, not the cause of
loading failure or successful playback. No provider request count or scoped
console capture was retained in those runs. Logs/images are retained under
ticket16/first-player-failure, with these limits.

Interim two-axis source review at18027d6 found an unenforced minimum player
viewport in both axes and a primary-error masking fixture cleanup in Standards.
The next candidate measures the actual remaining viewport before creating a
WebView, offers external playback in windows below200dp on either axis, and
uses primary-preserving fixture cleanup. Controlled-provider-only diagnostics
record request count, console severity/line and network error codes, never user
content or wallet information. Its runtime checks remain pending.

Read-only support traced MockWebServer3.5.4.0 URL construction to
InetAddress.getHostName, which can turn a numeric loopback bind into localhost.
The debug provider deliberately accepts only literal127.0.0.1 and otherwise
fails closed to port1. The fixture now constructs its allowed numeric URL
explicitly, retaining that restriction and the Referer/tap assertions. This is
a source-supported hypothesis; the earlier runs do not prove their actual
supplied host or the failure cause. Viewport/core/test compile passes1m40s;
four video-core cases pass, including independent WASM/Elliptic signed posts
and quote commentary/original IDs. No runtime success is claimed here.
