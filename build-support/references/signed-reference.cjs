// Reference-only pure crypto. Run network-none/read-only/env-i. Never initializes Saito.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const reference = {
  saitoCommit:'9a4ca7e9442eb5e872168ced361ce741d7d139d7',
  saitoWasmVersion:require('/opt/saito/node/node_modules/saito-wasm/package.json').version,
  ellipticVersion:require('/opt/saito/node/node_modules/elliptic/package.json').version,
  wasmSha256:crypto.createHash('sha256').update(fs.readFileSync('/opt/saito/node/node_modules/saito-wasm/pkg/node/index_bg.wasm')).digest('hex')
};
assert.equal(reference.saitoWasmVersion,'0.3.2');
assert.equal(reference.ellipticVersion,'6.6.1');
assert.equal(reference.wasmSha256,'f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e');

function signedReference(scalar, body, timestamp, recipients = []) {
  assert(['1','2'].includes(scalar)); // Public unsafe test keys. Never fund.
  const privateKey = '0'.repeat(63) + scalar;
  const author = wasm.generate_public_key(privateKey);
  const point = Buffer.from(base58.decode(author));
  const outputs = [...new Set([author,...recipients])];
  assert(outputs.length <= 255);
  assert(outputs.every(key => ['1','2'].some(value => wasm.generate_public_key('0'.repeat(63)+value) === key)));
  const message = Buffer.from(JSON.stringify(body));
  const transaction = new wasm.WasmTransaction();
  for (const [input,index,key] of [[true,0,author],...outputs.map((key,index) => [false,index,key])]) {
    const slip = new wasm.WasmSlip();
    slip.public_key=key; slip.amount=0n; slip.block_id=0n; slip.tx_ordinal=0n;
    slip.slip_index=index; slip.slip_type=0;
    if (input) transaction.add_from_slip(slip); else transaction.add_to_slip(slip);
  }
  transaction.timestamp=BigInt(timestamp); transaction.txs_replacements=1;
  transaction.type=0; transaction.data=message;
  const time = Buffer.alloc(8); time.writeBigUInt64BE(BigInt(timestamp));
  const signedSlip = Buffer.concat([point,Buffer.alloc(8),Buffer.from([0,0])]);
  const outputSlips = outputs.map((key,index) => Buffer.concat([Buffer.from(base58.decode(key)),Buffer.alloc(8),Buffer.from([index,0])]));
  const preimage = Buffer.concat([time,signedSlip,...outputSlips,Buffer.from([0,0,0,1]),Buffer.alloc(4),message]);
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
  return {sig:signature,created_at:timestamp,field2:author,tx:JSON.stringify(envelope)};
}
module.exports={reference,signedReference};
