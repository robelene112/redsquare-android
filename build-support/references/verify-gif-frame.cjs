// Independent ordinary GIF interoperability check of an owned Android test frame.
// Never initializes a Saito node. Arguments: frame kind fixture backend exact-source-file.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const kind = process.argv[3];
const backend = process.argv[5];
assert(['owner', 'official'].includes(backend));
const source = fs.readFileSync(process.argv[6], 'utf8').trim();
const url = new URL(source);
assert.equal(url.protocol, 'http:'); assert.equal(url.hostname, '127.0.0.1');
assert.equal(url.pathname, '/media/published.gif');
assert.equal(url.search, '?token=owned%2Bonly&keep=1');
assert(['post', 'reply', 'quote'].includes(kind));
const frame = Buffer.from(fs.readFileSync(process.argv[2], 'utf8').trim(), 'base64');
assert.equal(frame[0], 4);
const bytes = frame.subarray(1);
function verified(network) {
  const tx = wasm.WasmTransaction.deserialize(network);
  tx.generate_hash_for_signature();
  const signature = tx.signature, point = network.subarray(93, 126);
  assert(new EC('secp256k1').verify(Buffer.from(tx.get_hash_for_signature()).toString('hex'),
    {r: signature.slice(0, 64), s: signature.slice(64)}, point.toString('hex'), 'hex'));
  return {signature, author: base58.encode(point), timestamp: Number(tx.timestamp),
    body: JSON.parse(Buffer.from(tx.data).toString('utf8'))};
}
const outer = verified(bytes);
const fixture = JSON.parse(fs.readFileSync(process.argv[4], 'utf8'));
const root = fixture.ordinary;
assert.equal(outer.body.module, 'RedSquare');
assert.equal(outer.body.request, 'create tweet');
const data = outer.body.data;
assert.equal(data.text, `Native GIF ${kind} ${backend}`);
assert.deepEqual(data.images, [source]);
assert(!Object.hasOwn(data, 'gifAttributions'));
assert(!Object.hasOwn(outer.body, 'gifAttributions'));
assert(Buffer.byteLength(JSON.stringify(outer.body)) <= 1024 * 1024);
let original = null;
if (kind === 'reply') {
  assert.equal(data.parent_id, root.sig);
  assert.equal(data.thread_id, root.sig);
} else assert(!Object.hasOwn(data, 'parent_id'));
if (kind === 'quote') {
  assert.equal(data.signature, root.sig);
  assert.equal(data.retweet_tx, root.tx);
  const envelope = JSON.parse(data.retweet_tx), message = Buffer.from(envelope.m, 'base64');
  const originalBytes = Buffer.concat([Buffer.from(envelope.t, 'base64'), message]);
  originalBytes.writeUInt32BE(message.length, 8);
  original = verified(originalBytes);
  assert.equal(original.signature, root.sig);
  assert.equal(original.author, root.field2);
  assert.deepEqual(original.body.data.images, [fixture.images.red, fixture.images.blue]);
  assert.deepEqual(data.embedded, {signature: original.signature, publicKey: original.author,
    created_at: original.timestamp, text: original.body.data.text, images: original.body.data.images});
} else assert(!Object.hasOwn(data, 'retweet_tx'));
const inputs = bytes.readUInt32BE(0), outputs = bytes.readUInt32BE(4), length = bytes.readUInt32BE(8);
assert.equal(inputs, 1); assert.equal(bytes.readUInt32BE(12), 1);
const recipients = Array.from({length: outputs}, (_, i) =>
  base58.encode(bytes.subarray(93 + 59 * (inputs + i), 126 + 59 * (inputs + i))));
assert.deepEqual(recipients, [...new Set(kind === 'post' ? [outer.author] : [outer.author, root.field2])]);
const hopOffset = 93 + 59 * (inputs + outputs) + length;
assert.equal(bytes.length, hopOffset + 130);
const hop = bytes.subarray(hopOffset), signature = hop.subarray(66).toString('hex');
assert.equal(base58.encode(hop.subarray(0, 33)), outer.author);
assert(new EC('secp256k1').verify(wasm.hash(Buffer.concat([Buffer.from(outer.signature, 'hex'), hop.subarray(33, 66)])),
  {r: signature.slice(0, 64), s: signature.slice(64)}, hop.subarray(0, 33).toString('hex'), 'hex'));
console.log(JSON.stringify({result: 'PASS', kind, frameSha256: crypto.createHash('sha256').update(frame).digest('hex'),
  outer, original, recipients, reference}, null, 2));
