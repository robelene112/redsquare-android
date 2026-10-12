# Null optional repost commentary public reader RED

Pinned offline WASM/Elliptic independently signs an ordinary unsafe2 retweet with
text:null/images:null and actual owner recipient. The public activity test first
passes ordinary signed action ID/time/zeroignored checks, then fails at line47:
quote reader incorrectly reports one ignored row instead of quietly excluding
this valid ordinary repost. Focused seven tests: six pass/one failure,0.696s;
Gradle42s. Two hashes preserve raw build/result XML. No node initialization or
network writes; published unsafe reference scalars only. Core decoder correction
and full73test GREEN are pending. No Android cache/recreation proof yet.
