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
console.log(JSON.stringify({synthetic: true, reference, owner,
  actor: 'pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU',
  root: thread.root, incomingLike}, null, 2));
