// Verify a controlled emulator's public zero-payment frame with pinned Saito WASM.
// Run network-none/read-only/env-i. Never initializes Saito or reads wallet state.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {reference}=require('./signed-reference.cjs');
const wasm=require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58=require('/opt/saito/node/node_modules/base-58');
const EC=require('/opt/saito/node/node_modules/elliptic').ec;
const [filename,request]=process.argv.slice(2);
assert(['like tweet','retweet'].includes(request));
const frame=Buffer.from(fs.readFileSync(filename,'utf8').trim(),'base64');
assert.equal(frame[0],4);
const bytes=frame.subarray(1);
const transaction=wasm.WasmTransaction.deserialize(bytes);
const body=JSON.parse(Buffer.from(transaction.data).toString('utf8'));
const root=JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
assert.deepEqual(body,{module:'RedSquare',request,data:{signature:root.sig}});
const inputs=bytes.readUInt32BE(0),outputs=bytes.readUInt32BE(4),length=bytes.readUInt32BE(8);
assert.equal(inputs,1);assert.equal(bytes.readUInt32BE(12),1);
const authorPoint=bytes.subarray(93,126),author=base58.encode(authorPoint);
const recipients=Array.from({length:outputs},(_,index)=>base58.encode(bytes.subarray(93+59*(inputs+index),126+59*(inputs+index))));
assert.deepEqual(recipients,[...new Set([author,root.field2])]);
transaction.generate_hash_for_signature();
const hash=Buffer.from(transaction.get_hash_for_signature()).toString('hex');
const signature=transaction.signature;
assert(new EC('secp256k1').verify(hash,{r:signature.slice(0,64),s:signature.slice(64)},authorPoint.toString('hex'),'hex'));
const hopOffset=93+59*(inputs+outputs)+length;
assert.equal(bytes.length,hopOffset+130);
const hop=bytes.subarray(hopOffset);
assert(hop.subarray(0,33).equals(authorPoint));
const hopSignature=hop.subarray(66).toString('hex');
assert(new EC('secp256k1').verify(wasm.hash(Buffer.concat([Buffer.from(signature,'hex'),hop.subarray(33,66)])),
  {r:hopSignature.slice(0,64),s:hopSignature.slice(64)},authorPoint.toString('hex'),'hex'));
console.log(JSON.stringify({result:'PASS',request,author,signature,target:root.sig,recipients,reference}));
