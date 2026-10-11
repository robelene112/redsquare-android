// Unmodified pinned receiver methods with in-memory archive adapters only.
// Run after independent verification of the actual controlled Android frame.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const proof = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
assert.equal(proof.result, 'PASS'); assert.equal(proof.body.request, 'flag tweet');
const pins = {
  A: {commit: '9a4ca7e9442eb5e872168ced361ce741d7d139d7', hash: 'e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8'},
  B: {commit: '214504ecece76d1cd20a245fe4468eccbfca2951', hash: '2a83af18010fc4fc044aabec2741ff401c93b4672decdd5d78e1db5f81d4c5b0'}
};
function method(text, name) {
  const start = new RegExp(`^  async ${name}\\(`, 'm').exec(text);
  assert(start); const end = text.indexOf('\n  }\n', start.index); assert(end > start.index);
  return text.slice(start.index, end + 5);
}
async function main() {
  const observations = [];
  for (const [pin, metadata] of Object.entries(pins)) {
    const source = fs.readFileSync(path.join(process.argv[3], `${pin}-node_mods_redsquare_redsquare.js`), 'utf8');
    assert.equal(crypto.createHash('sha256').update(source).digest('hex'), metadata.hash);
    const receiver = vm.runInNewContext(`new (class extends (class {async handlePeerTransaction() {return 0;}}) {
      ${method(source, 'handlePeerTransaction')}
      ${method(source, 'receiveFlagTweetTransaction')}
    })()`, {}, {timeout: 2000});
    receiver.name = 'RedSquare';
    for (const available of ['resident', 'archive', 'absent']) {
      const target = {optional: {}}; const updates = []; let refreshes = 0; const replies = [];
      const tweet = {tx: target, refresh() {refreshes++;}};
      receiver.getTweet = signature => {assert.equal(signature, proof.body.data.signature); return available === 'resident' ? tweet : null;};
      receiver.app = {storage: {
        loadTransactions(criteria, callback, destination) {
          assert.equal(JSON.stringify(criteria), JSON.stringify({sig: proof.body.data.signature, field1: 'RedSquare'}));
          assert.equal(destination, 'localhost'); callback(available === 'archive' ? [target] : []);
        },
        async updateTransaction(tx, fields, destination) {
          assert.equal(tx, target); assert.equal(destination, 'localhost');
          assert.equal(fields.flagged, 1); assert.equal(tx.optional.flagged, 1); updates.push(fields);
        }
      }};
      const tx = {timestamp: 1700003000000, signature: proof.signature, from: [{publicKey: proof.author}],
        returnMessage() {return proof.body;}};
      assert.equal(await receiver.handlePeerTransaction(receiver.app, tx, null, response => replies.push(response)), 1);
      assert.equal(JSON.stringify(replies), '[{}]');
      assert.equal(updates.length, available === 'absent' ? 0 : 1);
      assert.equal(refreshes, available === 'resident' ? 1 : 0);
      observations.push({pin, ...metadata, available, acknowledgement: {}, updates: updates.length,
        moderatorsReviewed: 'not established', boundary: 'extracted unmodified receiver methods, controlled in-memory archive; no deployed service or chain proof'});
    }
    let wrongReply = false;
    assert.equal(await receiver.handlePeerTransaction(receiver.app,
      {returnMessage() {return {...proof.body, module: 'OtherModule'};}}, null, () => {wrongReply = true;}), 0);
    assert.equal(wrongReply, false);
  }
  console.log(JSON.stringify({result: 'PASS', frameSha256: proof.frameSha256, observations}, null, 2));
}
main().catch(failure => {console.error(failure); process.exitCode = 1;});
