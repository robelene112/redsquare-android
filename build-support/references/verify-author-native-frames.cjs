// Independently verify public frames emitted by the controlled Android author flow.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const frames = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
assert([2, 3].includes(frames.length));
const rows = frames.map(encoded => {
  const frame = Buffer.from(encoded, 'base64');
  assert.equal(frame[0], 4);
  const bytes = frame.subarray(1);
  assert.equal(bytes.readUInt32BE(0), 1);
  assert.equal(bytes.readUInt32BE(12), 1);
  const outputs = bytes.readUInt32BE(4), length = bytes.readUInt32BE(8);
  const messageOffset = 93 + 59 * (1 + outputs);
  const hopOffset = messageOffset + length;
  assert.equal(bytes.length, hopOffset + 130);
  const authorPoint = bytes.subarray(93, 126);
  const transaction = wasm.WasmTransaction.deserialize(bytes);
  transaction.generate_hash_for_signature();
  const signature = transaction.signature;
  assert(new EC('secp256k1').verify(Buffer.from(transaction.get_hash_for_signature()).toString('hex'),
    {r: signature.slice(0, 64), s: signature.slice(64)}, authorPoint.toString('hex'), 'hex'));
  const hop = bytes.subarray(hopOffset), hopSignature = hop.subarray(66).toString('hex');
  assert(hop.subarray(0, 33).equals(authorPoint));
  assert(new EC('secp256k1').verify(wasm.hash(Buffer.concat([Buffer.from(signature, 'hex'), hop.subarray(33, 66)])),
    {r: hopSignature.slice(0, 64), s: hopSignature.slice(64)}, authorPoint.toString('hex'), 'hex'));
  const detached = Buffer.from(bytes.subarray(0, messageOffset));
  detached.writeUInt32BE(0, 8); detached.writeUInt32BE(0, 12);
  const tx = JSON.stringify({t: detached.toString('base64'), m: bytes.subarray(messageOffset, hopOffset).toString('base64'),
    opt: Buffer.from('{}').toString('base64')});
  return {sig: signature, created_at: Number(transaction.timestamp), field2: base58.encode(authorPoint), tx,
    body: JSON.parse(Buffer.from(transaction.data).toString('utf8')),
    recipients: Array.from({length: outputs}, (_, index) => base58.encode(bytes.subarray(93 + 59 * (1 + index), 126 + 59 * (1 + index)))),
    frameSha256: crypto.createHash('sha256').update(frame).digest('hex')};
});
assert.deepEqual(rows[0].body, {module: 'RedSquare', request: 'create tweet', data: {text: "Native author's original post"}});
assert.deepEqual(rows[1].body, {module: 'RedSquare', request: 'edit tweet', data: {tweet_id: rows[0].sig, text: "Native author's edited post"}});
assert.equal(rows[1].field2, rows[0].field2);
assert.deepEqual(rows[1].recipients, [...new Set([rows[0].field2, ...rows[0].recipients])]);
assert(rows[1].created_at > rows[0].created_at);
if (rows[2]) {
  assert.deepEqual(rows[2].body, {module: 'RedSquare', request: 'delete tweet', data: {tweet_id: rows[0].sig}});
  assert.equal(rows[2].field2, rows[0].field2);
  assert.deepEqual(rows[2].recipients, [...new Set([rows[0].field2, ...rows[0].recipients])]);
}
console.log(JSON.stringify({result: 'PASS', native: true, reference,
  boundary: 'Actual controlled Android post/edit frames, independently verified with pinned WASM and Elliptic. No live write or chain inclusion proof.',
  original: rows[0], edit: rows[1], deleted: rows[2]}, null, 2));
