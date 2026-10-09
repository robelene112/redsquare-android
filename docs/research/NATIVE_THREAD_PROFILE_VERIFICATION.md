# Native threads and profiles

Ticket #8, delivery slice 7 of 35. Parent specification is GitHub issue #1.
This report remains provisional until final full verification and review.

The native client verifies detached Saito transactions before displaying post
content or like associations. Archive metadata supplies pagination hints, not
proof of authorship or chain inclusion. Signed parent and thread identifiers
must be valid transaction signatures before they can become navigation routes.

Conversation reads use RedSquare/thread archive criteria. Profile Posts and
Replies share the author's archive and filter verified signed relationships.
Likes reads verify the liking author and retrieve each target by exact signed
transaction ID. Twenty-row profile pages retain an advancing archive cursor,
including rows belonging to the other tab or rows whose content is rejected.
Unavailable history and like targets are explained in the interface.

The fixed selected backend scopes bounded public caches. Conversation and
profile caches store signed public envelopes and verify them again when read.
They contain no wallet or private Chat data. Ordinary restart/outage recovery
does not promise complete or permanent history. Profile author navigation keeps
a destination stack and restores the originating tab when going Back.

## Independent compatibility references

The network-disabled reference generators use pinned Saito WASM 0.3.2 and
Elliptic 6.6.1, independently verify manually assembled signing preimages, and
round-trip serialized transactions through WASM. Only deliberately unsafe
synthetic keys with scalar 1 or 2 are used. No wallet is initialized on the node
and no transaction is broadcast. See NATIVE_THREAD_REFERENCE.json,
NATIVE_PROFILE_LIKE_REFERENCE.json and NATIVE_PROFILE_PAGING_REFERENCE.json.
Source pin is 9a4ca7e9442eb5e872168ced361ce741d7d139d7.

## Observable device checks

The actual MainActivity runs against authenticated controlled loopback peers.
Each case starts with cleared fixture-owned public caches; restart cases keep
the cache during the recovery being tested. The initial four cases pass on
Android 10 and 17 at aef6742 in runs 37932930501 and 37932934354. Later review
fixes have progressively passed focused Android 10 runs:

- 37935302107: five cases, including malformed signed thread rejection.
- 37937666159: six cases, including older Replies and Likes behind full pages.
- 37940579836: seven cases, including reply and liked-post identity navigation
  and returning to the original Likes tab. Source dfc5000; 17.072 seconds.

Actual functional failures preceding those fixes are retained separately under
docs/evidence/ticket07. The initial profile implementation also regressed feed
body taps into author navigation; the failed runs and subsequent correction
are retained. A successful retry does not change what those failed runs show.

Final full Android 10/17 verification remains pending. These emulator results
do not establish physical Pixel performance or actual closed-app push delivery.
There has been no production node change or on-chain write for this ticket.
