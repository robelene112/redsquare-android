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
console.log(JSON.stringify({synthetic: true, reference, owner,
  actor: 'pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU',
  root: thread.root, incomingLike, incomingReply, parentedQuote, incomingMention,
  addressedPost, listedMention, usernameMention, incomingRepost, incomingQuote, legacyQuote, recoveredOriginal, missingOriginalLike}, null, 2));
