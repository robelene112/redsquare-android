// Pure pinned WASM reference. Run with no network, read-only mounts and env -i.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const root = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/research/NATIVE_FEED_REFERENCE.json'), 'utf8')).archiveRow;
function reply(scalar, text, parent, timestamp) {
  const privateKey = '0'.repeat(63) + scalar; // Public synthetic fixtures only, never fund.
  const author = wasm.generate_public_key(privateKey);
  const point = Buffer.from(base58.decode(author));
  const message = Buffer.from(JSON.stringify({module:'RedSquare', request:'create tweet',
    data:{text, parent_id:parent, thread_id:root.sig}}));
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
  const preimage = Buffer.concat([time, signedSlip, signedSlip, Buffer.from([0,0,0,1]), Buffer.alloc(4), message]);
  transaction.generate_hash_for_signature();
  const hash=wasm.hash(preimage);
  assert.equal(Buffer.from(transaction.get_hash_for_signature()).toString('hex'),hash);
  const signature=wasm.sign_buffer(preimage,privateKey);
  assert(wasm.verify_signature(preimage,signature,author));
  assert(new EC('secp256k1').verify(hash,{r:signature.slice(0,64),s:signature.slice(64)},point.toString('hex'),'hex'));
  transaction.signature=signature;
  const serialized=Buffer.from(transaction.serialize());
  const restored=wasm.WasmTransaction.deserialize(serialized);
  assert.equal(restored.signature,signature);
  assert.equal(Buffer.from(restored.data).toString('hex'),message.toString('hex'));
  transaction.data=Buffer.alloc(0);
  const envelope={t:Buffer.from(transaction.serialize()).toString('base64'),m:message.toString('base64'),
    opt:Buffer.from('{}').toString('base64')};
  return {sig:signature,created_at:timestamp,updated_at:timestamp,field1:'RedSquare',field2:author,
    field4:parent,field5:root.sig,flagged:0,tx:JSON.stringify(envelope)};
}
const firstReply=reply('2','Independent Saito reply',root.sig,1700000000010);
const nestedReply=reply('1','Nested Saito reply',firstReply.sig,1700000000020);
const tamperedReply=JSON.parse(JSON.stringify(firstReply));
const changed=JSON.parse(tamperedReply.tx);
changed.m=Buffer.from(JSON.stringify({module:'RedSquare',request:'create tweet',data:{text:'Forged reply'}})).toString('base64');
tamperedReply.tx=JSON.stringify(changed);
console.log(JSON.stringify({synthetic:true,reference:{saitoCommit:'9a4ca7e9442eb5e872168ced361ce741d7d139d7',
  saitoWasmVersion:require('/opt/saito/node/node_modules/saito-wasm/package.json').version,
  ellipticVersion:require('/opt/saito/node/node_modules/elliptic/package.json').version,
  wasmSha256:crypto.createHash('sha256').update(fs.readFileSync('/opt/saito/node/node_modules/saito-wasm/pkg/node/index_bg.wasm')).digest('hex')},
  root,firstReply,nestedReply,tamperedReply},null,2));
