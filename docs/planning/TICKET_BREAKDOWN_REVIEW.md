# Red Square Android: proposed sequential feature tickets

Status: draft for `to-tickets` review. These are proposed feature boundaries,
not published tickets or a local work tracker. The parent will be the approved
app-wide GitHub specification. Genuine dependencies are listed below; execution
is sequential even where the dependency graph permits a different order.

Each published ticket will have its own measurable acceptance criteria,
applicable testing boundaries and review evidence. No ticket is complete from
a mock screen alone where the behavior requires Saito interoperability.

1. **Prove Saito wallet/signature compatibility on Android.**
   **Blocked by:** None.
   **Delivers:** A reproducible diagnostic Android build creates a local identity,
   round-trips reference wallet data and verifies compatible signing through the
   intended native integration on Android 10 and the review-device target.
   Resolve the bridge/toolchain feasibility and pin tested dependencies. Keep
   synthetic key fixtures out of user releases. This is an integration proof,
   not completion of the first live-feed review milestone.

2. **Install and update a persistent local-wallet preview.**
   **Blocked by:** 1.
   **Delivers:** A signed preview named Red Square automatically creates and
   securely retains its real local wallet across restarts and ordinary updates.
   Establish stable package/signing/versioning and a phone-accessible review
   link/build notes. Verify ordinary in-place updating on the Pixel 7.

3. **Read the owner's live feed with cached cold starts.**
   **Blocked by:** 2.
   **Delivers:** Fresh launch opens the owner's backend feed using real Saito
   connectivity; subsequent launches display cached posts and refresh. Show
   accurate loading/error/outage states, preserve position, and measure the
   accepted launch targets. Render basic post text and identity without requiring
   any production write test.

4. **Switch between the two backends while retaining identity.**
   **Blocked by:** 3.
   **Delivers:** Choose either approved server, keep the active wallet and
   persist selection. Keep histories/moderation distinct, retain the selected
   server through outages and restore its cache on returning. Reject custom
   endpoints and imported peer overrides. Finish the red/green recovery header.

5. **Export wallets with device authentication and recovery guidance.**
   **Blocked by:** 2.
   **Delivers:** Compatible authenticated export, a dismissible backup reminder,
   and accurate recovery guidance. Explain unavailable device authentication
   without silently weakening the requirement. Verify file export actually
   succeeds through the Android flow and preserves needed compatible options.

6. **Import or replace a wallet with Snake's safeguards.**
   **Blocked by:** 4, 5.
   **Delivers:** Validate and preview same/different identities, enforce the
   balance-dependent fresh-backup gate, acknowledge names/pending work and
   recover failed/interrupted activation. Isolate local state by identity and
   verify live/saved identity agreement. Together with 1 to 5, completes the
   requested first review milestone on both backends.

7. **Open threads and browse user profiles.**
   **Blocked by:** 4.
   **Delivers:** Navigate from a feed post into its available conversation and
   from an identity into a profile with posts, replies and likes. Retain usable
   cached views and truthful missing-history states.

8. **Follow accounts and read a Following feed.**
   **Blocked by:** 7.
   **Delivers:** Follow/unfollow from relevant views and a feed restricted to
   followed identities using the selected backend's records. Keep choices
   coherent across both backend sessions without promising cross-device sync.

9. **Publish text posts and replies with recoverable drafts.**
   **Blocked by:** 6, 7.
   **Delivers:** Compose, save drafts and publish compatible signed text posts
   and replies. Show truthful submission state, retain failed work and demonstrate
   a newly created unfunded identity against controlled compatible peers.

10. **Queue posts offline and resume on their original backend.**
    **Blocked by:** 9.
    **Delivers:** Explicit Send offline creates a durable Queued item that can
    be cancelled before sending. Resume only with its original backend selected;
    retries avoid duplicates and preserve the originating identity. Drafts
    remain unsent. Exercise restart and wallet-replacement interactions.

11. **Like posts and perform ordinary reposts.**
    **Blocked by:** 9.
    **Delivers:** Compatible social interactions reflected coherently in feed,
    thread and profile views, with truthful pending/failure state and deduplicated
    retries rather than fabricated counts.

12. **Publish and display quote posts across supported formats.**
    **Blocked by:** 9.
    **Delivers:** Quote with commentary and an embedded original; recognize
    relevant archived/current representations and preserve compatible signatures.
    Establish current backend behavior and any required integration change from
    evidence. Rendering must include the author's commentary and original.
    Image quotes are completed by the media slice below.

13. **Edit and delete the author's posts.**
    **Blocked by:** 9.
    **Delivers:** Compatible authorized text edits and deletions update normal
    views and caches, handle failures/conflicts coherently and explain remaining
    historical copies. Prevent actions by unrelated identities.

14. **Attach and view images in posts, replies and quotes.**
    **Blocked by:** 9, 12.
    **Delivers:** Bounded compatible image selection, composition and viewing,
    including quote attachments, with failures preserving authored work and
    media loading keeping the feed usable.

15. **Search/select Giphy for posts, replies and quotes.**
    **Blocked by:** 14.
    **Delivers:** Compatible GIF selection and viewing, including quote
    attachments. Provider/search failures preserve authored work and permit
    retry or cancellation without losing the draft.

16. **Play embedded YouTube on tap.**
    **Blocked by:** 7.
    **Delivers:** Recognize compatible video embeds in feed and thread views;
    start playback only on deliberate user interaction. Recover navigation and
    provider failures without exposing private wallet capabilities to content.

17. **Edit profile bio, avatar and banner.**
    **Blocked by:** 6, 7, 14.
    **Delivers:** Compatible profile updates, generated avatar by default, and
    image selection for avatar/banner. Handle upload/save failures truthfully;
    updates preserve the public identity across backend sessions.

18. **Register an optional Saito username.**
    **Blocked by:** 6, 7.
    **Delivers:** Request and display a compatible registered name with truthful
    availability, pending and failure states. Registration remains optional and
    never gates first-launch reading, posting or Chat.

19. **Search posts and people in available backend history.**
    **Blocked by:** 7.
    **Delivers:** Search and navigate results on the selected backend, handling
    empty/error/partial-history cases without claiming exhaustive chain history.

20. **Mute and block accounts across backend sessions.**
    **Blocked by:** 7.
    **Delivers:** Explained personal controls retained through backend changes.
    Mute hides social posts/notifications; block additionally suppresses incoming
    Chat. Shared identity-scoped control state is ready for those later flows.

21. **Report posts to the selected backend.**
    **Blocked by:** 7.
    **Delivers:** Clearly name the reporting destination, submit compatible
    reports and show truthful transmission/failure state. Distinguish submission
    from moderator review; retain the selected backend's moderation boundaries.

22. **Show balances/receiving details and send authenticated SAITO.**
    **Blocked by:** 6.
    **Delivers:** Real balance/receive views and online transfers with
    recipient/amount/fee confirmation and device authentication. Exercise
    cancellation, failure and pending/confirmed distinctions with isolated funds;
    no automatic offline monetary queue or unapproved real-fund test.

23. **Show in-app social notifications.**
    **Blocked by:** 7, 20.
    **Delivers:** Available replies, mentions and social activity open the
    relevant post/thread/profile. Apply personal controls and selected-backend
    boundaries. Notification permission remains optional and contextual.

24. **Exchange encrypted private Chat messages with existing clients.**
    **Blocked by:** 6, 20.
    **Delivers:** A complete native DM send/receive/read path with compatible
    encryption and truthful delivery state. Recover available history with
    imported conversation/key information plus local cache; suppress blocked
    authors and keep account-muted Chat accessible. Verify both backend contracts.

25. **Create, invite, join and leave unencrypted group Chat.**
    **Blocked by:** 24.
    **Delivers:** Named groups, invitations, member views and leaving, using
    compatible existing membership rules. Label group privacy accurately and
    preserve recoverable group/history state through ordinary app restarts.

26. **Manage group members and administrators.**
    **Blocked by:** 25.
    **Delivers:** Compatible admin promotion and member removal with visible
    results and truthful failure states. Enforce authorization and handle stale
    membership/admin information without claiming server acceptance prematurely.

27. **Queue private and group Chat while offline.**
    **Blocked by:** 10, 24, 25.
    **Delivers:** Cancellable persistent Chat queues bound to originating identity
    and backend. Reuse the established queued-work guarantees; verify recovery,
    retries and wallet-replacement interactions without duplicate messages.

28. **Mute individual private and group conversations.**
    **Blocked by:** 24, 25.
    **Delivers:** Persistent conversation mute retains readable messages while
    silencing their notifications. Explain its distinction from account mute
    and block, and demonstrate the controls with incoming compatible messages.

29. **Send and view images in private and group Chat.**
    **Blocked by:** 14, 24, 25.
    **Delivers:** Compatible visual messages, cached viewing and bounded media
    failures without losing composed text or adding arbitrary FileShare.

30. **Select and send GIFs in private and group Chat.**
    **Blocked by:** 15, 29.
    **Delivers:** Reuse compatible Giphy selection in Chat with real transmitted
    GIF messages, cached rendering and recoverable provider/send failures.

31. **Deliver reply and mention pushes with the app closed.**
    **Blocked by:** 23.
    **Delivers:** Actual closed-app social notification delivery and navigation;
    optional permission, category settings and account controls work. Verify a
    viable integration for both backends. Any required gateway/server change
    needs its own bounded authorized integration work rather than an implicit
    production mutation. Push API success alone is not delivery evidence.

32. **Deliver private Chat pushes with privacy and mute controls.**
    **Blocked by:** 24, 28, 31.
    **Delivers:** DMs notify the closed app and open the correct conversation.
    Private notifications default to New message; previews require opt-in.
    Permission/category/conversation/account controls apply, and delivery never
    requires uploading wallet private keys.

33. **Deliver group Chat pushes with category/conversation controls.**
    **Blocked by:** 25, 28, 32.
    **Delivers:** Group notifications reach the closed app, obey group and
    conversation controls, and open the correct group with its privacy label.

34. **Verify release polish, performance and update continuity.**
    **Blocked by:** 8, 11, 13, 16, 17, 18, 19, 21, 22, 26, 27, 30, 33.
    **Delivers:** End-to-end acceptance across the agreed scope, system/light/dark
    themes and English translation-ready copy without em dashes. Repeat the
    precise Pixel launch measurements and minimum-OS checks. Publish signed
    review updates with build notes and an update notice, demonstrating wallet
    and local-data continuity. Styling/theme/copy conventions apply in earlier
    slices too; this ticket is integrated acceptance, not the first UI.

35. **Prepare and submit the Google Play release.**
    **Blocked by:** 34.
    **Delivers:** Verified account/store eligibility, applicable testing track,
    signing continuity, truthful listing/privacy/declarations and a concrete
    release package. Complete reviewable submission preparation before the
    final publication step, following the owner's release authorization.

## Breakdown review

The owner reviews granularity and genuine blocking edges before publication.
Dependencies capture behavior or shared contracts that genuinely gate a slice;
the numbered delivery order is not itself a dependency. For example, quote
posts need publishing but do not depend on implementing likes first.

The initial protocol proof may expose a prerequisite requiring a smaller bounded
experiment. Closed-app push may require a separately scoped gateway integration
once its actual contracts are established. Preserve the agreed first-release
scope when adjusting slices; do not quietly remove features.

Feature-focused grills can refine an upcoming slice when there is a real product
choice. Routine engineering facts and compatibility checks remain agent work.
No implementation or ticket publication has begun.
