// Public controlled Android frame only. Pure pinned WASM, no application initialization.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const frame = Buffer.from(fs.readFileSync(process.argv[2], 'utf8').trim(), 'base64');
assert.equal(frame[0], 4);
const bytes = frame.subarray(1);
function verifiedTransaction(network) {
  const transaction = wasm.WasmTransaction.deserialize(network);
  const authorPoint = network.subarray(93, 126);
  transaction.generate_hash_for_signature();
  const hash = Buffer.from(transaction.get_hash_for_signature()).toString('hex');
  const signature = transaction.signature;
  assert(new EC('secp256k1').verify(hash,
    {r: signature.slice(0, 64), s: signature.slice(64)}, authorPoint.toString('hex'), 'hex'));
  return {signature, author: base58.encode(authorPoint), timestamp: Number(transaction.timestamp),
    body: JSON.parse(Buffer.from(transaction.data).toString('utf8'))};
}
const outer = verifiedTransaction(bytes);
const root = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/research/NATIVE_FEED_REFERENCE.json'), 'utf8')).archiveRow;
assert.equal(outer.body.module, 'RedSquare');
assert.equal(outer.body.request, 'create tweet');
assert.equal(outer.body.data.text, 'Native quote commentary');
assert.equal(outer.body.data.signature, root.sig);
assert(!Object.hasOwn(outer.body.data, 'parent_id'));
const originalEnvelope = JSON.parse(outer.body.data.retweet_tx);
const message = Buffer.from(originalEnvelope.m, 'base64');
const originalNetwork = Buffer.concat([Buffer.from(originalEnvelope.t, 'base64'), message]);
originalNetwork.writeUInt32BE(message.length, 8);
const original = verifiedTransaction(originalNetwork);
assert.equal(original.signature, root.sig);
assert.equal(original.author, root.field2);
assert.equal(original.body.data.text, 'Native Red Square reference post');
assert.deepEqual(outer.body.data.embedded, {
  signature: original.signature, publicKey: original.author, created_at: original.timestamp,
  text: original.body.data.text, images: []
});
const inputs = bytes.readUInt32BE(0), outputs = bytes.readUInt32BE(4), length = bytes.readUInt32BE(8);
assert.equal(inputs, 1);
assert.equal(bytes.readUInt32BE(12), 1);
const recipients = Array.from({length: outputs}, (_, index) =>
  base58.encode(bytes.subarray(93 + 59 * (inputs + index), 126 + 59 * (inputs + index))));
assert.deepEqual(recipients, [...new Set([outer.author, original.author])]);
const hopOffset = 93 + 59 * (inputs + outputs) + length;
assert.equal(bytes.length, hopOffset + 130);
const hop = bytes.subarray(hopOffset);
assert.equal(base58.encode(hop.subarray(0, 33)), outer.author);
const hopSignature = hop.subarray(66).toString('hex');
assert(new EC('secp256k1').verify(wasm.hash(Buffer.concat([Buffer.from(outer.signature, 'hex'), hop.subarray(33, 66)])),
  {r: hopSignature.slice(0, 64), s: hopSignature.slice(64)}, hop.subarray(0, 33).toString('hex'), 'hex'));
console.log(JSON.stringify({result: 'PASS', frameSha256: crypto.createHash('sha256').update(frame).digest('hex'),
  outer, original, recipients, reference}, null, 2));
