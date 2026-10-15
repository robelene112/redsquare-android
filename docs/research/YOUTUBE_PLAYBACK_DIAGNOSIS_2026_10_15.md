# YouTube playback diagnosis, 2026-10-15

Read-only investigation for [#17](https://github.com/robelene112/redsquare-android/issues/17),
while the primary agent owns #40 and its device runs. Read project instructions,
the current issue/comments, handoff, existing media contract/verification notes,
player and public fixture source, and retained public results. Checked current
official YouTube, Android and Chromium documentation. Inspected both original
Oct 15 screenshots. Only this research document was written. No build, emulator,
CI dispatch, upgrade, provider key, production/node/wallet access or playback
request was made.

## Finding

No evidence-supported production identity fix is established. The native
wrapper already follows the documented local-HTML app identity technique.
The useful next step is a bounded diagnostic correction: preserve the actual
numeric player error and actual initial embed request identity before the
failed WebView is destroyed. Do that before another unchanged public probe.
The existing 101/150 mapping does not prove why these particular requests were
refused, and it does not prove missing integrity credentials.

There is also a concrete environment discrepancy: Android 10 public checks use
WebView 74, whereas the earlier Android 17 checks use Google WebView 145. That
deserves separate compatibility work, but cannot explain both failures by
itself. No update was performed.

## What is actually observed

- The [Oct 15 Android 10 record](../evidence/ticket17/public-oct15-recheck/android10/README.md)
  is run 38681078177 at `e3edf1c971c25f49d5b793d98cfb2de85dd70aac`.
  The second signed reference selects `bHQqvYy5KYo`. The public fixture found
  and clicked the provider Play control, then failed waiting for Playing at
  `PublicYouTubeReadTest.kt:64`, one failure in 52.838 s. Later successful
  playback/close/feed assertions were not reached.
- The original ready PNG shows a black player area and native start guidance.
  It is not a decoded-video screenshot. The failure PNG shows native refusal
  copy, Retry and Open in YouTube. That copy comes from `EMBED_DISABLED`, whose
  only provider mappings are 101/150. The exact number is not retained.
  [Player status mapping](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/VideoBrowser.kt),
  [original screenshots](../evidence/ticket17/public-oct15-recheck/android10/video-evidence/),
  [public fixture](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/PublicYouTubeReadTest.kt).
- [Retained browser samples](../evidence/ticket17/public-oct15-recheck/android10/video-browser.jsonl)
  show wrapper origin `https://nl.davidengelen.redsquare.dev`, YouTube frame
  origin `https://www.youtube.com`, and 296x472 viewports in both frames. The
  video remains paused with readyState 0 and zero decoded dimensions. These
  samples establish origin/layout observations, not actual HTTP request
  headers, successful media decoding, or a provider decision reason.
- Previous Android 10 and17 checks also refused both independent references.
  [Existing chronology](NATIVE_VIDEO_VERIFICATION.md) and
  [issue evidence comment](https://github.com/robelene112/redsquare-android/issues/17#issuecomment-6102476905)
  remain qualified. This investigation does not reinterpret them as passes.

## Current identity and policy comparison

YouTube requires an identifying Referer, including sideloaded apps. Its
documented Android local-HTML technique is `loadDataWithBaseURL`; the HTTPS
host should be the installed app ID. It recommends
`strict-origin-when-cross-origin`, permits OS WebView, and mentions additional
credentials for high request volumes. No Play publication prerequisite is
stated for ordinary embedded playback.
[Current required functionality](https://developers.google.com/youtube/terms/required-minimum-functionality#api-client-identity-and-credentials).

The implementation, last changed in `c6113d2fb3c2b519a2e91be40af0485261522bce`,
does the following:

| Boundary | Actual source behavior | Assessment |
| --- | --- | --- |
| Local HTML base | `https://${context.packageName}/` | Uses actual installed `.dev` identity for debug and unsuffixed identity for release. |
| Referrer policy | HTML meta `strict-origin-when-cross-origin` | Matches the recommended policy. |
| IFrame API origin | The same HTTPS app origin without trailing slash | Consistent with wrapper origin. |
| Provider requests | Public mode returns null from `shouldInterceptRequest` | No app proxy, injected header, fixture replacement or rewritten provider response in public mode. |
| User agent | No override | No app-side browser identity spoofing. |
| Media integrity | No AndroidX integrity override | Absence of explicit configuration does not establish that integrity is disabled. |
| Web permissions | No file/content access or JS native bridge; mixed content blocked; third-party cookies disabled | Security choices are present; no retained observation ties one to this refusal. |

[Player construction](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/VideoBrowser.kt),
[application IDs](../../app/build.gradle.kts).
Android documents that this base URL supplies both page origin and subresource
Referer. Runtime headers still need observation rather than inference from
source alone.
[WebView base URL contract](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)).

YouTube describes `origin` as an IFrame API security measure.
`widget_referrer` addresses a widget embedded inside another application/page;
the docs do not establish that adding it fixes this direct app-owned wrapper.
[Player parameters](https://developers.google.com/youtube/player_parameters#origin).
Do not replace the app ID with `saito.io`, the owner backend, or an unrelated
working web domain. Such a change would conceal the actual app identity.

The official error reference distinguishes 101/150 (embedding refused) from 153
(missing Referer/equivalent identity). It does not document a rule that every
integrity failure must emit 153. Consequently 101/150 is neither proof of an
identity mismatch nor proof that identity/integrity was accepted.
[IFrame errors](https://developers.google.com/youtube/iframe_api_reference#onError).

## Integrity and environment limits

YouTube says Android WebView embeds automatically send attested app metadata
(package, version, signing certificate) and a Play-services-generated token.
That description does not establish this run's actual token generation or
provider verdict. AndroidX's feature-gated configuration defaults to enabled
with app identity; the app does not call its disabling API.
[YouTube integration](https://developers.google.com/youtube/iframe_api_reference#android-webview-media-integrity-api-integration),
[AndroidX configuration/default](https://developer.android.com/reference/androidx/webkit/WebViewMediaIntegrityApiStatusConfig).
Do not introduce a separate app Play Integrity project, API key, opt-out or
attestation bypass based on this failure alone.

| Retained runtime | Selected WebView |
| --- | --- |
| Oct 15 Android 10 recheck and earlier Android 10 public checks | `com.android.webview`, 74.0.3729.185 |
| Earlier Android 17 original/alternate public refusal checks | `com.google.android.webview`, 145.0.7632.218 |

Sources: [Oct 15 Android 10 provider](../evidence/ticket17/public-oct15-recheck/android10/video-webview.txt),
[earlier Android 17 provider](../evidence/ticket16/public-alternate-refusal/android17/video-webview.txt).
The CI harness selects Android 29 `default` and Android 37.0 `google_apis`
system images. Image labels alone do not prove installed Play-services state,
certification, successful attestation or current provider availability.
[Harness](../../scripts/ci-android-device).

Chrome 74 is a 2019 generation, verified against the
[official Chrome release record](https://chromereleases.googleblog.com/2019/05/chrome-for-android-update.html).
YouTube recommends up-to-date browsers, but its public help page does not give
a precise minimum embedded WebView version.
[YouTube system requirements](https://support.google.com/youtube/answer/78358?hl=en).
An Android 10 fixture with a supported, current eligible provider is therefore
a reasonable future comparison, not a proved fix. Chromium documents provider
package/signature/SDK restrictions, so installing a random Google WebView APK
onto the default AOSP image is not a justified update plan.
[Provider eligibility](https://chromium.googlesource.com/chromium/src/+/main/android_webview/docs/webview-providers.md).
The Android 17 refusal prevents attributing all failures to WebView 74.

## Concrete next diagnostic, before changing playback behavior

The missing observation is narrow and obtainable without changing acceptance
assertions or weakening the media security boundary:

1. Preserve the integer already received by `onError` and read in
   `VideoBrowser.readStatus`, with video ID and bounded state/timestamp, before
   mapping it to product copy. A debug-only structured record at that native
   parsing point can capture it before `PostVideo` removes the failed browser.
   Keep error visibility and the unchanged Playing requirement.
2. Observe the real initial `https://www.youtube.com/embed/<known-id>` request
   from navigation start, including its actual Referer, sanitized origin
   parameter, wrapper base URI and installed package. This embed navigation is
   a child-frame navigation, not the app wrapper's main-frame request. Seeing
   the script request's Referer alone is insufficient. Use metadata observation,
   not a proxy/replay of provider responses or an injected replacement Referer.
3. Record selected WebView package/version, actual user agent, app
   package/version/public signing-certificate digest, system image and whether
   Play services is installed. These describe environment, not a successful
   integrity verdict. If feature availability is inspected, label it separately
   from a token or provider decision. Do not retain tokens, cookies, full request
   bodies or opaque provider query parameters.
4. Capture the original provider error surface before native teardown if
   feasible, then retain normal native failure PNG/XML. Provider-specific
   reason text can guide the next question, but undocumented private fields
   must not become an application dependency or acceptance oracle.

The current [browser diagnostic](../../build-support/video-browser-diagnostics.cjs)
only samples layout in isolated worlds every 5 s. It does not enable request
recording, inspect actual HTTP headers, or preserve `window.rsVideo`. Simply
adding `window.rsVideo` to its isolated-world expression would not reliably
read the page world's JavaScript state, consistent with Chromium's
[isolated-world separation](https://developer.chrome.com/docs/extensions/develop/concepts/content-scripts#work_in_isolated_worlds).
Prefer the existing native status
parsing boundary for the integer, or deliberately select the exact wrapper's
default execution context. Attach request observation before initial navigation
so the first embed request is not missed. Official tools support
[WebView remote debugging](https://developer.chrome.com/docs/devtools/remote-debugging/webviews)
and [network header inspection](https://developer.chrome.com/docs/devtools/network/reference#headers).
This is proposed diagnostic work, not an implemented or dispatched check.

If the observed Referer is missing/wrong, correct that boundary while preserving
the real installed app ID. If it is correct and the provider gives a specific
integrity/environment reason, compare an eligible current provider and actual
physical device environment before changing product identity. If the only
remaining signal is 101/150, retain the unresolved cause. Neither a backend/node
upgrade nor a new YouTube credential is supported by the evidence collected
here. #17 remains open pending actual deliberate public media playback.
