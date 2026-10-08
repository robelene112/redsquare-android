# Red Square repost and quote-post trace

Read-only source inspection on 2026-10-08. No transactions were submitted and
no saved wallet, private configuration or database contents were inspected.
The public service's actual posting behavior was not exercised.

## Published Saito implementation

Official SaitoTech/saito-lite-rust source at commit
`7200a9886f3dd02f66e8d6c146960e2a5402929f` explicitly supports quote posts:

- [post.js:305](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/post.js#L305)
  distinguishes plain reposts from commentary or image reposts. Plain reposts
  send a retweet interaction; commentary or images attach the serialized
  original as `data.retweet_tx` and send a normal create-tweet transaction.
- [redsquare.js:1775](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/redsquare.js#L1775)
  carries those fields in a signed outer post with its own author, timestamp,
  signature, text and images.
- [tweet.js:151](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L151)
  reconstructs the embedded original; rendering includes the quote's own images
  and original-post preview at line 464.
- [redsquare.js:1980](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/redsquare.js#L1980)
  archives the quote through the ordinary RedSquare post/feed path.

This establishes published feature support. It does not establish that every
current deployment uses this exact module revision.

## Deployed redesign on the owner's node

Static sources under `/opt/saito/node` follow a different path:

1. `mods/redsquare/lib/ui/overlays/compose.js:577` builds an original signature
   with optional text/images. Both variants call `createRetweetTransaction`
   (`:657`), without a standalone create-tweet or `retweet_tx`/`embedded` field.
2. `mods/redsquare/redsquare.js:1177` serializes that payload into a retweet.
   It is archived as RedSquareRetweet (`:1455`), preserving the commentary/images
   in its signed payload.
3. The receiver updates the original's counts, retweeters and timestamps and adds
   a notification (`:1708`), rather than building a standalone quote-feed post.
4. Main feed/profile requests select RedSquare records (`redsquare.js:656`,
   `lib/manager.js:1092`). Retweet records are separately loaded for notifications
   (`redsquare.js:911` and `:979`).
5. `lib/notification.js:61` labels a commented/image repost as quote, but renders
   the referenced original rather than commentary/attachments (`:155` to `:171`).
6. The nested-post renderer (`lib/tweet.js:206`, `lib/tweet.template.js:120`)
   expects `data.embedded`, not published upstream's `data.retweet_tx`.

This is an apparent deployed compatibility/regression issue. Live posting was
not performed. Resolve transaction interpretation and any necessary backend
integration in the implementation work; do not infer feature deferral from an
incomplete source inventory. The owner confirmed published Red Square feature
parity as the baseline and rejected deferring quote posts.
