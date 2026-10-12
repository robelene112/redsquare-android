// Pure full-block evidence check. No Saito/global initialization or private state.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {reference}=require('/references/signed-reference.cjs');
const wasm=require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58=require('/opt/saito/node/node_modules/base-58');
const EC=require('/opt/saito/node/node_modules/elliptic').ec;
const encoded=fs.readFileSync('/evidence/full-block.b64','utf8').trim();
assert(encoded.length<=12*1024*1024);
const bytes=Buffer.from(encoded,'base64');assert.equal(bytes.toString('base64'),encoded);
assert(bytes.length>=389&&bytes.length<=8*1024*1024);
const preimage=Buffer.concat([bytes.subarray(4,117),bytes.subarray(181,213),bytes.subarray(221,285)]);
const creator=base58.encode(bytes.subarray(52,85));
const signature=bytes.subarray(117,181).toString('hex');
assert(wasm.verify_signature(preimage,signature,creator));
assert(new EC('secp256k1').verify(wasm.hash(preimage),{r:signature.slice(0,64),s:signature.slice(64)},bytes.subarray(52,85).toString('hex'),'hex'));
const hash=wasm.hash(Buffer.concat([bytes.subarray(20,52),Buffer.from(wasm.hash(preimage),'hex')]));
const block=new wasm.WasmBlock();assert.equal(block.deserialize(bytes),'');
assert.equal(block.hash,hash);assert.equal(block.id,bytes.readBigUInt64BE(4));assert.equal(block.timestamp,bytes.readBigUInt64BE(12));
assert.equal(block.transactions.length,bytes.readUInt32BE(0));assert(Buffer.from(block.serialize()).equals(bytes));
const zeroRoot=Buffer.from(bytes);zeroRoot.fill(0,85,117);
const recomputed=new wasm.WasmBlock();assert.equal(recomputed.deserialize(zeroRoot),'');
assert(Buffer.from(recomputed.serialize()).subarray(85,117).equals(bytes.subarray(85,117)));
const vector=JSON.parse(fs.readFileSync('/fixtures/native-payment-reference.json','utf8'));
assert.equal(vector.synthetic,true);assert.deepEqual(vector.reference,reference);
const candidate=Buffer.from(vector.payment.networkBase64,'base64');
const matches=block.transactions.filter(tx=>tx.type===0&&tx.signature===vector.payment.id).map(tx=>{
 const wire=Buffer.from(tx.serialize());
 const inputs=wire.readUInt32BE(0),outputs=wire.readUInt32BE(4);
 const normalized=Buffer.from(wire.subarray(0,93+59*(inputs+outputs)+wire.readUInt32BE(8)));
 normalized.writeUInt32BE(0,12);
 for(let index=0;index<outputs;index++)normalized.fill(0,93+59*(inputs+index)+41,93+59*(inputs+index)+57);
 assert(normalized.equals(candidate));return true;
});
process.stdout.write(JSON.stringify({reference,bytes:bytes.length,hash,height:block.id.toString(),timestamp:block.timestamp.toString(),
 previousHash:block.previous_block_hash,creator,merkleRoot:bytes.subarray(85,117).toString('hex'),
 transactionCount:block.transactions.length,types:block.transactions.map(tx=>tx.type),exactExpectedPayment:matches.length===1,
 creatorWasm:true,creatorElliptic:true,rustRecomputedRoot:true,exactFullRoundtrip:true,
 boundary:'Synthetic full-block content and exact reference payment only, not consensus admission, selected-chain membership or finality'},null,2)+'\n');
