// Independent verification of an actual Android Profile frame. Public unsafe fixtures only.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const specimen = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
assert.equal(specimen.synthetic, true);
const frame = Buffer.from(specimen.frame, 'base64');
assert.equal(frame[0], 4);
const bytes = frame.subarray(1);
assert.equal(bytes.readUInt32BE(0), 1);
assert.equal(bytes.readUInt32BE(4), 1);
assert.equal(bytes.readUInt32BE(12), 1);
const messageOffset = 93 + 2 * 59;
const messageLength = bytes.readUInt32BE(8);
assert(messageLength > 0 && messageLength <= 1024 * 1024);
const hopOffset = messageOffset + messageLength;
assert.equal(bytes.length, hopOffset + 130);
assert.equal(bytes.readUInt32BE(88), 1);
assert.equal(bytes[92], 0);
const authorPoint = bytes.subarray(93, 126);
const owner = 'jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR';
assert.equal(base58.encode(authorPoint), owner);
assert(bytes.subarray(152, 185).equals(authorPoint));
assert(bytes.subarray(126, 152).equals(Buffer.alloc(26)));
assert(bytes.subarray(185, 211).equals(Buffer.alloc(26))); // Both slips: zero amount/metadata/type/index.
const transaction = wasm.WasmTransaction.deserialize(bytes);
transaction.generate_hash_for_signature();
const signature = transaction.signature;
assert(new EC('secp256k1').verify(Buffer.from(transaction.get_hash_for_signature()).toString('hex'),
  {r: signature.slice(0, 64), s: signature.slice(64)}, authorPoint.toString('hex'), 'hex'));
const hop = bytes.subarray(hopOffset), hopSignature = hop.subarray(66).toString('hex');
assert(hop.subarray(0, 33).equals(authorPoint));
assert(new EC('secp256k1').verify(wasm.hash(Buffer.concat([Buffer.from(signature, 'hex'), hop.subarray(33, 66)])),
  {r: hopSignature.slice(0, 64), s: hopSignature.slice(64)}, authorPoint.toString('hex'), 'hex'));
const body = JSON.parse(Buffer.from(transaction.data).toString('utf8'));
assert.deepEqual(body, {module: 'Profile', request: 'update profile', snapshot: 1, data: {
  description: 'A newly published bio 🌱\nKeep my other profile fields',
  image: 'https://images.example.test/avatar.png', banner: 'https://images.example.test/banner.png',
  archive: ['https://archive.example.test'], extension: {theme: 'blue'}
}});
assert(Number(transaction.timestamp) > 1700001000004);
const detached = Buffer.from(bytes.subarray(0, messageOffset));
detached.writeUInt32BE(0, 8); detached.writeUInt32BE(0, 12);
const envelope = {t: detached.toString('base64'), m: bytes.subarray(messageOffset, hopOffset).toString('base64'),
  opt: Buffer.from('{}').toString('base64')};
assert.deepEqual(JSON.parse(specimen.envelope), envelope);
console.log(JSON.stringify({result: 'PASS', native: true, reference,
  boundary: 'Actual controlled Android Profile frame independently verified with pinned WASM/Elliptic. No live write, deployment, DOM or chain inclusion proof.',
  frameSha256: crypto.createHash('sha256').update(frame).digest('hex'),
  profile: {sig: signature, created_at: Number(transaction.timestamp), field2: owner, tx: JSON.stringify(envelope), body}}, null, 2));
