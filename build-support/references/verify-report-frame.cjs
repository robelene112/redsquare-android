// Controlled Android public report frame only. Network-none/read-only/env-i.
// Pure pinned WASM and independent Elliptic; no Saito application initialization.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const frame = Buffer.from(fs.readFileSync(process.argv[2], 'utf8').trim(), 'base64');
assert.equal(frame[0], 12);
assert(frame.readUInt32BE(1) > 0 && frame.readUInt32BE(1) <= 0x7fffffff);
const bytes = frame.subarray(5);
assert.equal(bytes.readUInt32BE(0), 1);
assert.equal(bytes.readUInt32BE(4), 1);
assert.equal(bytes.readUInt32BE(12), 0); // No peer-routing hop.
assert.equal(bytes.length, 211 + bytes.readUInt32BE(8));
assert.equal(bytes.readUInt32BE(88), 1);
assert.equal(bytes[92], 0);
const transaction = wasm.WasmTransaction.deserialize(bytes);
const body = JSON.parse(Buffer.from(transaction.data).toString('utf8'));
const root = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/research/NATIVE_FEED_REFERENCE.json'), 'utf8')).archiveRow;
assert.deepEqual(body, {module: 'RedSquare', request: 'flag tweet', data: {signature: root.sig}});
const point = bytes.subarray(93, 126), author = base58.encode(point);
assert(bytes.subarray(152, 185).equals(point)); // Only self, not the reported author.
transaction.generate_hash_for_signature();
const hash = Buffer.from(transaction.get_hash_for_signature()).toString('hex');
const signature = transaction.signature;
assert(new EC('secp256k1').verify(hash, {r: signature.slice(0, 64), s: signature.slice(64)}, point.toString('hex'), 'hex'));
const damaged = Buffer.from(bytes); damaged[damaged.length - 2] ^= 1;
const changed = wasm.WasmTransaction.deserialize(damaged); changed.generate_hash_for_signature();
assert(!new EC('secp256k1').verify(Buffer.from(changed.get_hash_for_signature()).toString('hex'),
  {r: signature.slice(0, 64), s: signature.slice(64)}, point.toString('hex'), 'hex'));
console.log(JSON.stringify({result: 'PASS', frameSha256: crypto.createHash('sha256').update(frame).digest('hex'),
  index: frame.readUInt32BE(1), author, signature, body, recipients: [author], reference,
  boundary: 'WASM deserialize/hash and independent Elliptic of controlled Android signed type12, no backend/moderator/chain receipt'}));
