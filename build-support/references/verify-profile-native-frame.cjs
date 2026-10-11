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
const selectedMedia = process.argv[3] === 'selected-media';
assert(process.argv[3] === undefined || selectedMedia, 'Unknown Profile specimen scenario');
function jpegDimensions(source, width, height) {
  assert.equal(typeof source, 'string');
  assert(source.length <= 512 * 1024);
  assert(/^data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}$/.test(source));
  const payload = source.substring(source.indexOf(',') + 1), jpeg = Buffer.from(payload, 'base64');
  assert.equal(jpeg.toString('base64'), payload);
  assert.equal(jpeg.readUInt16BE(0), 0xffd8);
  assert.equal(jpeg.readUInt16BE(jpeg.length - 2), 0xffd9);
  let cursor = 2;
  while (cursor < jpeg.length - 2) {
    assert.equal(jpeg[cursor++], 0xff);
    while (jpeg[cursor] === 0xff) cursor++;
    const marker = jpeg[cursor++];
    assert.notEqual(marker, 0xda, 'JPEG scan arrived before dimensions');
    assert(cursor + 2 <= jpeg.length);
    const length = jpeg.readUInt16BE(cursor);
    assert(length >= 2 && cursor + length <= jpeg.length);
    if ([0xc0, 0xc1, 0xc2].includes(marker)) {
      assert(length >= 8);
      assert.equal(jpeg[cursor + 2], 8);
      assert.equal(jpeg.readUInt16BE(cursor + 3), height);
      assert.equal(jpeg.readUInt16BE(cursor + 5), width);
      assert.equal(jpeg[cursor + 7], 3);
      return;
    }
    cursor += length;
  }
  assert.fail('No JPEG dimensions found');
}
if (selectedMedia) {
  // Independently specified 12x8 red/blue test PNGs produce these literal crops.
  jpegDimensions(body.data.image, 8, 8);
  jpegDimensions(body.data.banner, 12, 4);
}
assert.deepEqual(body, {module: 'Profile', request: 'update profile', snapshot: 1, data: {
  description: selectedMedia ? 'My selected profile 🌱\nKeep this bio and my identity' : 'A newly published bio 🌱\nKeep my other profile fields',
  image: selectedMedia ? body.data.image : 'https://images.example.test/avatar.png',
  banner: selectedMedia ? body.data.banner : 'https://images.example.test/banner.png',
  archive: ['https://archive.example.test'], extension: {theme: 'blue'}
}});
assert(Number(transaction.timestamp) > 1700001000004);
const detached = Buffer.from(bytes.subarray(0, messageOffset));
detached.writeUInt32BE(0, 8); detached.writeUInt32BE(0, 12);
const envelope = {t: detached.toString('base64'), m: bytes.subarray(messageOffset, hopOffset).toString('base64'),
  opt: Buffer.from('{}').toString('base64')};
assert.deepEqual(JSON.parse(specimen.envelope), envelope);
console.log(JSON.stringify({result: 'PASS', native: true, reference,
  scenario: selectedMedia ? 'selected-media' : 'retained-media-bio',
  boundary: 'Actual controlled Android Profile frame independently verified with pinned WASM/Elliptic. No live write, deployment, DOM or chain inclusion proof.',
  frameSha256: crypto.createHash('sha256').update(frame).digest('hex'),
  profile: {sig: signature, created_at: Number(transaction.timestamp), field2: owner, tx: JSON.stringify(envelope), body}}, null, 2));
