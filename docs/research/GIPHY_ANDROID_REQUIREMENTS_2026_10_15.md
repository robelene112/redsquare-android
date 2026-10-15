# Giphy Android requirements and selected-message references

Primary-source research checked 2026-10-15 for future #16/#31. Read
[the existing media contract](NATIVE_GIF_VIDEO_CONTRACT.md) first. This note
creates no implementation or license approval. No provider key, node
configuration, wallet, credential-bearing request, build, CI run, release
metadata or application file was accessed or changed. Only this document was
written; no provider inquiry was sent.

## Finding that changes the handoff

Giphy's Android documentation explicitly suggests sending media IDs in
messaging. Its receiver example resolves an ID using `GPHCore.gifById`;
`GPHMediaView.setMediaWithId` is another documented path. It also documents
built-in recently picked items and Fresco asset caching. These are positive
evidence for reference-based messaging and SDK-managed behavior, rather than
evidence that every retained GIF reference is a prohibited catalogue.
[Pinned Android docs, Media IDs, Recents and Caching](https://github.com/Giphy/giphy-android-sdk/blob/8c690ab2d0651f5a7a9177d519aaf424ce2dcb6c/Docs.md#media-ids).

The existing Saito format signs the selected `images.original.url`, without
embedding downloaded/reencoded GIF bytes. Preserve that interoperability
constraint from [the existing contract](NATIVE_GIF_VIDEO_CONTRACT.md#existing-saito-gif-path).
The inspected public docs do not expressly classify immutable selected-message
URLs, encrypted drafts, local message history or queued signed candidates.
That absence establishes neither permission nor prohibition, and does not prove
that bespoke approval is mandatory for ordinary message references.

## Concrete current requirements

- Direct API integrations require separate keys per platform and app section.
  Keys begin with a 100-calls/hour beta allowance. Higher volume requires a
  production application and review, including pricing discussion. The
  quickstart also instructs integrations nearing launch to upgrade. Do not
  infer an unconditional paid-production prerequisite from the hourly limit.
- Calls and media loads go directly from the client. Preserve returned media
  URLs and their query parameters, result order and provider separation.
  Analytics integration is highly recommended in the quickstart, rather than
  stated there as universally mandatory.
- Standard integrations must not cache media URLs or assets. Custom media
  caching requires explicit approval; the approved delivery exception requires
  prior written approval and revalidation. The exception describes media
  delivery caches, while the URL prohibition remains broad.

Source: [API quickstart and Best Practices](https://developers.giphy.com/docs/api/).

The API terms prohibit building or augmenting a GIF/sticker database,
directory or index from provider content. They require conspicuous Powered by
Giphy text/logo and available user/source attribution. They do not explicitly
name signed social posts, draft retention or message history as such an index.
[API terms, sections 1, 4 and 5](https://support.giphy.com/hc/en-us/articles/360028134111-GIPHY-API-Terms-of-Service).

The SDK requires AndroidX, min SDK 19, compile SDK 35+, Kotlin 2.1+, and an
**Android SDK key**, separately allocated per platform. It offers a picker,
grid and Compose `GiphyDialogView` prototype. Its documented default rating is
`pg13`; choose the intended rating explicitly.
[Pinned Android requirements and settings](https://github.com/Giphy/giphy-android-sdk/blob/8c690ab2d0651f5a7a9177d519aaf424ce2dcb6c/Docs.md#requirements).
Stable SDK **2.5.3** is the current published release, confirmed against
[official release metadata](https://github.com/Giphy/giphy-android-sdk/releases/tag/v2.5.3)
and [Maven Central metadata](https://repo.maven.apache.org/maven2/com/giphy/sdk/ui/maven-metadata.xml).
This confirms the earlier research version; it is not a proposed dependency
update. No current app dependency changes or compatibility build were attempted.

SDK use also binds the integrator to its license, which incorporates API terms,
service terms and privacy policy. Sections 2.1, 3.2 and 4.3 contain application
distribution sublicense, end-user notice and branding obligations. Choosing the
SDK therefore needs those provisions reviewed as part of integration, without
assuming that a public example repository grants an unrestricted SDK license.
[SDK license](https://support.giphy.com/hc/en-us/articles/360027858552-Giphy-SDK-License-Agreement),
[official key-request guidance](https://support.giphy.com/hc/en-us/articles/360020283431-Request-A-GIPHY-API-Key).

## Apply the distinction in #16/#31

These are implementation recommendations and remaining questions, not new
provider requirements:

| Data or behavior | Evidence and useful action |
| --- | --- |
| Selected GIF ID | Documented messaging recommendation. Retain an ID alongside the selected URL where the local model can do so without changing signed interoperability. |
| Original URL in a signed post/Chat message | Existing Saito contract. Keep the original signed content intact; investigate display resolution separately. Public Giphy docs leave this precise retention case unclear. |
| Encrypted draft, queued candidate or cached message containing the reference | Treat as user-authored message state, with existing identity/backend isolation. Encryption does not itself create a provider exception. Do not claim a documented retention duration or exemption. |
| Persisted GIF bytes or reusable search catalogue | Avoid a custom offline media store or durable search catalogue. SDK cache documentation does not authorize a separate generic app cache. |
| Displaying old references | Consider fresh ID resolution for display and handle removed/unavailable results. The official [Get GIF by ID endpoint](https://developers.giphy.com/docs/api/endpoint/get-gif-by-id/) returns current metadata; it does not promise permanent availability. Never rewrite signed history to refresh a rendition. |

The real outstanding setup prerequisite remains a valid key whose holder
authorizes the Android integration and appropriate sections. No existing key's
type, authorization or availability was inspected. Selected-peer service
advertising is insufficient evidence. Do not repeat the owner's pending key
question or borrow a backend web key.

If the authorized holder needs clarification, a narrowly scoped question is:
"Does retaining the user-selected original Giphy URL and optional ID inside
signed Saito posts/Chat, encrypted drafts, queued candidates and local message
history count as prohibited URL caching when media is fetched directly from
Giphy and no search catalogue or offline media cache is created? Is fresh
ID-based display resolution expected?" No such inquiry was sent.

This note establishes documentation evidence only. Live authorized search,
selected-reference persistence, actual SDK cache/removal behavior and Android
runtime interoperability remain implementation verification work.
