// Pure reference generation. Published unsafe scalars only; never initializes a node.
const fs = require('node:fs');
const path = require('node:path');
const {reference, signedReference} = require('./signed-reference.cjs');
const thread = JSON.parse(fs.readFileSync(path.join(__dirname,
  '../../docs/research/NATIVE_THREAD_REFERENCE.json'), 'utf8'));
const owner = 'jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR';
const incomingLike = {
  ...signedReference('2', {module: 'RedSquare', request: 'like tweet',
    data: {signature: thread.root.sig}}, 1700000000050, [owner]),
  field1: 'RedSquareLike', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000050, flagged: 0
};
const incomingReply = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Reply notification reference', parent_id: thread.root.sig,
      thread_id: thread.root.sig}}, 1700000000055, [owner]),
  field1: 'RedSquare', field3: owner, field4: thread.root.sig,
  field5: thread.root.sig, updated_at: 1700000000055, flagged: 0
};
// A valid quote signature does not turn into a reply because it carries a parent field.
const parentedQuote = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: thread.root.sig, text: 'Quote carrying a parent reference',
      parent_id: thread.root.sig, thread_id: thread.root.sig}}, 1700000000056, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000056, flagged: 0
};
const incomingMention = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: `Hello @${owner}, mention reference`}}, 1700000000060, [owner]),
  field1: 'RedSquare', field3: owner, field4: '', updated_at: 1700000000060, flagged: 0
};
const addressedPost = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'An addressed post without a mention'}}, 1700000000061, [owner]),
  field1: 'RedSquare', field3: owner, field4: '', updated_at: 1700000000061, flagged: 0
};
const listedMention = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'A signed mention list', mentions: [owner]}}, 1700000000062, [owner]),
  field1: 'RedSquare', field3: owner, field4: '', updated_at: 1700000000062, flagged: 0
};
const usernameMention = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Hello @NativeCase7@saito, username mention reference'}}, 1700000000063, [owner]),
  field1: 'RedSquare', field3: owner, field4: '', updated_at: 1700000000063, flagged: 0
};
const incomingRepost = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: thread.root.sig}}, 1700000000070, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000070, flagged: 0
};
// Null optional media is absent, not commentary or an invalid ordinary repost.
const incomingNullRepost = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: thread.root.sig, text: null, images: null}}, 1700000000071, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000071, flagged: 0
};
const original = JSON.parse(Buffer.from(JSON.parse(thread.root.tx).m, 'base64')).data;
const incomingQuote = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: thread.root.sig, text: 'Quote notification reference',
      retweet_tx: thread.root.tx}}, 1700000000075, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000075, flagged: 0
};
const legacyQuote = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {signature: thread.root.sig, text: 'Legacy quote notification reference',
      retweet_tx: thread.root.tx, embedded: {signature: thread.root.sig,
        publicKey: owner, text: original.text, images: []}}}, 1700000000076, [owner]),
  field1: 'RedSquare', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000076, flagged: 0
};
// A separately signed original withheld from the first lookup, then made available on Retry.
const recoveredOriginal = {
  ...signedReference('1', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Original recovered after retry'}}, 1700000000080),
  field1: 'RedSquare', flagged: 0
};
const missingOriginalLike = {
  ...signedReference('2', {module: 'RedSquare', request: 'like tweet',
    data: {signature: recoveredOriginal.sig}}, 1700000000085, [owner]),
  field1: 'RedSquareLike', field3: owner, field4: recoveredOriginal.sig,
  updated_at: 1700000000085, flagged: 0
};
const missingOriginalRepost = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: recoveredOriginal.sig}}, 1700000000090, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: recoveredOriginal.sig,
  updated_at: 1700000000090, flagged: 0
};
// Owner output addressing does not override this independently verified non-owner author.
const knownOtherPost = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'A known post by another account'}}, 1699999999900),
  field1: 'RedSquare', flagged: 0
};
const knownOtherLike = {
  ...signedReference('2', {module: 'RedSquare', request: 'like tweet',
    data: {signature: knownOtherPost.sig}}, 1700000000091, [owner]),
  field1: 'RedSquareLike', field3: owner, field4: knownOtherPost.sig,
  updated_at: 1700000000091, flagged: 0
};
// A current commentary quote is itself a signed post with a Retweet archive namespace.
const ownCurrentQuote = {
  ...signedReference('1', {module: 'RedSquare', request: 'retweet',
    data: {signature: thread.root.sig, text: 'Own quote recovered after retry',
      retweet_tx: thread.root.tx}}, 1700000000100),
  field1: 'RedSquareRetweet', field4: thread.root.sig, flagged: 0
};
const incomingQuoteLike = {
  ...signedReference('2', {module: 'RedSquare', request: 'like tweet',
    data: {signature: ownCurrentQuote.sig}}, 1700000000105, [owner]),
  field1: 'RedSquareLike', field3: owner, field4: ownCurrentQuote.sig,
  updated_at: 1700000000105, flagged: 0
};
const missingOriginalReply = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Reply with unavailable parent', parent_id: recoveredOriginal.sig,
      thread_id: recoveredOriginal.sig}}, 1700000000110, [owner]),
  field1: 'RedSquare', field3: owner, field4: recoveredOriginal.sig,
  field5: recoveredOriginal.sig, updated_at: 1700000000110, flagged: 0
};
// Owner addressing cannot turn a reply to a verified other account into an own-post notification.
const knownOtherReply = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Reply to a known other account', parent_id: knownOtherPost.sig,
      thread_id: knownOtherPost.sig}}, 1700000000111, [owner]),
  field1: 'RedSquare', field3: owner, field4: knownOtherPost.sig,
  field5: knownOtherPost.sig, updated_at: 1700000000111, flagged: 0
};
// Signed commentary remains available without an embedded original.
const missingOriginalQuote = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: recoveredOriginal.sig, text: 'Quote with unavailable original'}},
    1700000000115, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: recoveredOriginal.sig,
  updated_at: 1700000000115, flagged: 0
};
const knownOtherQuote = {
  ...signedReference('2', {module: 'RedSquare', request: 'retweet',
    data: {signature: knownOtherPost.sig, text: 'Quote of a known other account',
      retweet_tx: knownOtherPost.tx}}, 1700000000116, [owner]),
  field1: 'RedSquareRetweet', field3: owner, field4: knownOtherPost.sig,
  updated_at: 1700000000116, flagged: 0
};
// Distinct signed actors at a shared boundary, plus an older event for pagination.
const boundaryIncomingLike = {
  ...signedReference('4', {module: 'RedSquare', request: 'like tweet',
    data: {signature: thread.root.sig}}, 1700000000050, [owner]),
  field1: 'RedSquareLike', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000050, flagged: 0
};
const olderIncomingLike = {
  ...signedReference('3', {module: 'RedSquare', request: 'like tweet',
    data: {signature: thread.root.sig}}, 1700000000040, [owner]),
  field1: 'RedSquareLike', field3: owner, field4: thread.root.sig,
  updated_at: 1700000000040, flagged: 0
};
// Public-only points for an eleven-account continuation fixture; only fixed unsafe3 signs.
const farContactPost = {
  ...signedReference('3', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Activity from the eleventh followed account'}}, 1700000000120),
  field1: 'RedSquare', flagged: 0
};
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const continuationContacts = Array.from({length: 64}, (_, index) =>
  wasm.generate_public_key((index + 1).toString(16).padStart(64, '0')))
  .filter(key => key < farContactPost.field2 && key !== owner).sort().slice(0, 10);
require('node:assert/strict').equal(continuationContacts.length, 10);
continuationContacts.push(farContactPost.field2);
// The parent owner is the second nonactor recipient, so field3 indexes someone else.
const parentIndexedReply = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Reply indexed by its own-post parent', parent_id: thread.root.sig,
      thread_id: thread.root.sig}}, 1700000000125, [farContactPost.field2, owner]),
  field1: 'RedSquare', field3: farContactPost.field2, field4: thread.root.sig,
  field5: thread.root.sig, updated_at: 1700000000125, flagged: 0
};
// Ten newer verified own posts place the original root eleventh for parent-query continuation.
const newerOwnPosts = Array.from({length: 10}, (_, index) => ({
  ...signedReference('1', {module: 'RedSquare', request: 'create tweet',
    data: {text: `Newer own post ${index + 1} for parent discovery`}}, 1700000000500 + index),
  field1: 'RedSquare', field4: '', flagged: 0
}));
// More than one Archive page, still below the300-event device budget.
const cachedLikes = Array.from({length: 150}, (_, index) => {
  const timestamp = 1700000000200 + index;
  return {...signedReference('2', {module: 'RedSquare', request: 'like tweet',
    data: {signature: thread.root.sig}}, timestamp, [owner]),
    field1: 'RedSquareLike', field3: owner, field4: thread.root.sig,
    updated_at: timestamp, flagged: 0};
});
// One signed event eligible to two wallet identities, for independent read-marker checks.
const sharedWalletMention = {
  ...signedReference('2', {module: 'RedSquare', request: 'create tweet',
    data: {text: 'Shared mention for wallet isolation', mentions: [owner, farContactPost.field2]}},
    1700000000175, [owner, farContactPost.field2]),
  field1: 'RedSquare', field3: owner, field4: '', updated_at: 1700000000175, flagged: 0
};
console.log(JSON.stringify({synthetic: true, reference, owner,
  actor: 'pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU',
  root: thread.root, incomingLike, incomingReply, parentedQuote, incomingMention,
  addressedPost, listedMention, usernameMention, incomingRepost, incomingNullRepost, incomingQuote, legacyQuote, recoveredOriginal, missingOriginalLike, missingOriginalRepost, knownOtherPost, knownOtherLike, ownCurrentQuote, incomingQuoteLike, missingOriginalReply, knownOtherReply, missingOriginalQuote, knownOtherQuote, boundaryIncomingLike, olderIncomingLike, farContactPost, continuationContacts, parentIndexedReply, newerOwnPosts, cachedLikes, sharedWalletMention}, null, 2));
