// Independently verify an actual controlled Android registration; no wallet options or live writes.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const specimen = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
assert.equal(specimen.native, true);
const frame = Buffer.from(specimen.frame, 'base64');
assert.equal(frame[0], 4);
const bytes = frame.subarray(1);
assert.equal(bytes.readUInt32BE(0), 1); // One input.
assert.equal(bytes.readUInt32BE(4), 2); // Registrar first, wallet change second.
assert.equal(bytes.readUInt32BE(12), 1); // One independently verified routing hop.
assert.equal(bytes.readUInt32BE(88), 1);
assert.equal(bytes[92], 0);
const messageOffset = 93 + 3 * 59, messageLength = bytes.readUInt32BE(8);
assert(messageLength > 0 && messageLength <= 1024);
const hopOffset = messageOffset + messageLength;
assert.equal(bytes.length, hopOffset + 130);
const authorPoint = bytes.subarray(93, 126), author = base58.encode(authorPoint);
assert(bytes.subarray(126, 152).equals(Buffer.alloc(26)));
const registrar = 'sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK';
assert.equal(base58.encode(bytes.subarray(152, 185)), registrar);
assert(bytes.subarray(185, 211).equals(Buffer.alloc(26)));
assert(bytes.subarray(211, 244).equals(authorPoint));
assert(bytes.subarray(244, 268).equals(Buffer.alloc(24)));
assert.equal(bytes[268], 1); assert.equal(bytes[269], 0);
const transaction = wasm.WasmTransaction.deserialize(bytes);
transaction.generate_hash_for_signature();
const signature = transaction.signature;
assert(new EC('secp256k1').verify(Buffer.from(transaction.get_hash_for_signature()).toString('hex'),
  {r: signature.slice(0, 64), s: signature.slice(64)}, authorPoint.toString('hex'), 'hex'));
const hop = bytes.subarray(hopOffset), hopSignature = hop.subarray(66).toString('hex');
assert(hop.subarray(0, 33).equals(authorPoint));
assert.equal(base58.encode(hop.subarray(33, 66)), 'jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR');
assert(new EC('secp256k1').verify(wasm.hash(Buffer.concat([Buffer.from(signature, 'hex'), hop.subarray(33, 66)])),
  {r: hopSignature.slice(0, 64), s: hopSignature.slice(64)}, authorPoint.toString('hex'), 'hex'));
const body = JSON.parse(Buffer.from(transaction.data).toString('utf8'));
assert.deepEqual(body, {module: 'Registry', request: 'register', identifier: 'NativeCase7@saito'});
assert(Number(transaction.timestamp) > 1700002000000);
const detached = Buffer.from(bytes.subarray(0, messageOffset));
detached.writeUInt32BE(0, 8); detached.writeUInt32BE(0, 12);
const envelope = {t: detached.toString('base64'), m: bytes.subarray(messageOffset, hopOffset).toString('base64'),
  opt: Buffer.from('{}').toString('base64')};
assert.deepEqual(JSON.parse(specimen.envelope), envelope);
console.log(JSON.stringify({result: 'PASS', native: true, reference,
  boundary: 'Actual controlled Android zero-payment Registry request and routing signatures independently verified with pinned WASM/Elliptic. No live registration, namespace ownership or chain inclusion proof.',
  frameSha256: crypto.createHash('sha256').update(frame).digest('hex'),
  registration: {author, registrar, signature, timestamp: Number(transaction.timestamp), body,
    envelope: JSON.stringify(envelope)}}, null, 2));
