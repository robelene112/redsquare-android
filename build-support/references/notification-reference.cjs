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
console.log(JSON.stringify({synthetic: true, reference, owner,
  actor: 'pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU',
  root: thread.root, incomingLike, incomingReply, parentedQuote}, null, 2));
