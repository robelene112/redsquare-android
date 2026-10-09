// Public unsafe scalar1/2 fixtures only. Network-none/read-only; never initialize an app.
const assert = require('node:assert/strict');
const {reference,signedReference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const body = {module:'RedSquare',request:'create tweet',data:{text:'Independent native root 😀 <&>\nline two'}};
const timestamp = 1700000040000;
const row = signedReference('1',body,timestamp);
const envelope = JSON.parse(row.tx);
const detached = Buffer.from(envelope.t,'base64');
const message = Buffer.from(envelope.m,'base64');
const network = Buffer.concat([detached,message]);
network.writeUInt32BE(message.length,8);
const restored = wasm.WasmTransaction.deserialize(network);
assert.equal(restored.signature,row.sig);
assert.equal(Buffer.from(restored.data).toString('hex'),message.toString('hex'));
const destination = wasm.generate_public_key('0'.repeat(63)+'2');
const recipient = Buffer.from(base58.decode(destination));
const preimage = Buffer.concat([Buffer.from(row.sig,'hex'),recipient]);
const hopSignature = wasm.sign_buffer(preimage,'0'.repeat(63)+'1');
assert(wasm.verify_signature(preimage,hopSignature,row.field2));
assert(new EC('secp256k1').verify(wasm.hash(preimage),
  {r:hopSignature.slice(0,64),s:hopSignature.slice(64)},Buffer.from(base58.decode(row.field2)).toString('hex'),'hex'));
const routed = Buffer.from(network);
routed.writeUInt32BE(1,12);
const hop = Buffer.concat([Buffer.from(base58.decode(row.field2)),recipient,Buffer.from(hopSignature,'hex')]);
const broadcast = Buffer.concat([Buffer.from([4]),routed,hop]);
const routedRoundTrip = wasm.WasmTransaction.deserialize(broadcast.subarray(1));
assert.equal(routedRoundTrip.signature,row.sig);
assert.equal(Buffer.from(routedRoundTrip.data).toString('hex'),message.toString('hex'));
console.log(JSON.stringify({synthetic:true,reference,body,timestamp,author:row.field2,destination,
  id:row.sig,networkBase64:network.toString('base64'),envelope:row.tx,broadcastBase64:broadcast.toString('base64')},null,2));
