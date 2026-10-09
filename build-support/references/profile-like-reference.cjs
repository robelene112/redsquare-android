// Pure independently checked public fixtures. No application/wallet initialization.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
assert.equal(require('/opt/saito/node/node_modules/saito-wasm/package.json').version,'0.3.2');
assert.equal(require('/opt/saito/node/node_modules/elliptic/package.json').version,'6.6.1');
assert.equal(crypto.createHash('sha256').update(fs.readFileSync('/opt/saito/node/node_modules/saito-wasm/pkg/node/index_bg.wasm')).digest('hex'),
  'f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e');
const thread = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/research/NATIVE_THREAD_REFERENCE.json'), 'utf8'));
function like(scalar, target, timestamp) {
  const privateKey = '0'.repeat(63) + scalar; // Public, unsafe test keys. Never fund.
  const author = wasm.generate_public_key(privateKey);
  const point = Buffer.from(base58.decode(author));
  const message = Buffer.from(JSON.stringify({module:'RedSquare',request:'like tweet',data:{signature:target}}));
  const transaction = new wasm.WasmTransaction();
  for (const input of [true, false]) {
    const slip = new wasm.WasmSlip();
    slip.public_key=author; slip.amount=0n; slip.block_id=0n; slip.tx_ordinal=0n;
    slip.slip_index=0; slip.slip_type=0;
    if (input) transaction.add_from_slip(slip); else transaction.add_to_slip(slip);
  }
  transaction.timestamp=BigInt(timestamp); transaction.txs_replacements=1;
  transaction.type=0; transaction.data=message;
  const time = Buffer.alloc(8); time.writeBigUInt64BE(BigInt(timestamp));
  const signedSlip = Buffer.concat([point, Buffer.alloc(8), Buffer.from([0,0])]);
  const preimage = Buffer.concat([time,signedSlip,signedSlip,Buffer.from([0,0,0,1]),Buffer.alloc(4),message]);
  transaction.generate_hash_for_signature();
  const hash=wasm.hash(preimage);
  assert.equal(Buffer.from(transaction.get_hash_for_signature()).toString('hex'),hash);
  const signature=wasm.sign_buffer(preimage,privateKey);
  assert(wasm.verify_signature(preimage,signature,author));
  assert(new EC('secp256k1').verify(hash,{r:signature.slice(0,64),s:signature.slice(64)},point.toString('hex'),'hex'));
  transaction.signature=signature;
  const restored=wasm.WasmTransaction.deserialize(transaction.serialize());
  assert.equal(restored.signature,signature);
  assert.equal(Buffer.from(restored.data).toString('hex'),message.toString('hex'));
  transaction.data=Buffer.alloc(0);
  const envelope={t:Buffer.from(transaction.serialize()).toString('base64'),m:message.toString('base64'),opt:Buffer.from('{}').toString('base64')};
  return {sig:signature,created_at:timestamp,field1:'RedSquareLike',field2:author,field4:target,tx:JSON.stringify(envelope)};
}
const likedReply=like('1',thread.firstReply.sig,1700000000030);
const unavailableLike=like('1','a'.repeat(128),1700000000031);
const otherAuthorLike=like('2',thread.root.sig,1700000000032);
const alteredLike=JSON.parse(JSON.stringify(likedReply));
const envelope=JSON.parse(alteredLike.tx);
envelope.m=Buffer.from(JSON.stringify({module:'RedSquare',request:'like tweet',data:{signature:thread.root.sig}})).toString('base64');
alteredLike.tx=JSON.stringify(envelope);
console.log(JSON.stringify({synthetic:true,reference:{saitoCommit:'9a4ca7e9442eb5e872168ced361ce741d7d139d7',
  saitoWasmVersion:require('/opt/saito/node/node_modules/saito-wasm/package.json').version,
  ellipticVersion:require('/opt/saito/node/node_modules/elliptic/package.json').version,
  wasmSha256:crypto.createHash('sha256').update(fs.readFileSync('/opt/saito/node/node_modules/saito-wasm/pkg/node/index_bg.wasm')).digest('hex')},
  likedReply,unavailableLike,otherAuthorLike,alteredLike},null,2));
