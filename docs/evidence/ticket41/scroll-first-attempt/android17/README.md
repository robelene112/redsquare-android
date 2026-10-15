# First full-content scroll attempt, android17

Actual run38805691367 at9bb4cb8824f2e34f4b961c86e8583cbf76167efd FAILS in50.439s. Two cases, bothFAIL. Immediate physical lookup misses Members after scrollToNode and Cancel after private preview end. Root PNGs show the labels; Members is absent from that XML, while private end XML contains Cancel at[140,602][296,622]. This establishes an observer/snapshot mismatch, not a proven application cause. Group dialog/recreation tail and private post-scroll reopening/cancellation tail remain unexecuted.

Root viewed Members and private end originals on both SDKs and live-warning original on Android10; every retained XML parses. Genuine warning contents and complete final private message lines are visible in their scoped captures. No complete scroll-acceptance claim follows from this attempt.

Fixture484bc048 now positively observes group finite scroll end using the same bounded helper and retains public initial/max/end values before the unchanged physical oracle. Fixture36b66a7d uses a bounded positive Android accessibility lookup before capturing/measuring; full48dp/system-bar/enabled/tap/preservation assertions remain. Production is unchanged. Corrected runtime remains pending.

Core114/0failures/0ignored and debug/test APK assembly pass. Focused scopes do not execute release/lint or full acceptance. No live Saito submission or physical Pixel acceptance.
