// Verify a public controlled native frame with pure pinned WASM/Elliptic.
// Network-none, read-only, env-i. No application/wallet initialization or signing.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const encoded = fs.readFileSync('/evidence/authenticated-frame.b64','utf8');
assert(encoded.length < 1024 * 1024);
const frame = Buffer.from(encoded,'base64');
assert.equal(frame.toString('base64'),encoded);
assert.equal(frame[0],4);
const wire = frame.subarray(1);
const inputs=wire.readUInt32BE(0), outputs=wire.readUInt32BE(4), length=wire.readUInt32BE(8);
assert.equal(inputs,3);assert.equal(outputs,2);assert.equal(wire.readUInt32BE(12),1);
assert.equal(wire.readUInt32BE(88),1);assert.equal(wire[92],0);
assert.equal(wire.length,93+59*(inputs+outputs)+length+130);
const rows = Array.from({length:inputs+outputs},(_,index) => wire.subarray(93+59*index,152+59*index));
const author=base58.encode(rows[0].subarray(0,33));
assert(rows[0].subarray(33).equals(Buffer.alloc(26)));
const amounts=rows.map(row => row.readBigUInt64BE(33));
assert.deepEqual(amounts,[0n,500000000n,250000000n,625000000n,115000000n]);
assert.equal(amounts.slice(0,inputs).reduce((a,b)=>a+b,0n)-amounts.slice(inputs).reduce((a,b)=>a+b,0n),10000000n);
assert.deepEqual(rows.slice(1,3).map(row=>[row.readBigUInt64BE(41).toString(),row.readBigUInt64BE(49).toString(),row[57],row[58]]),
  [['1001','7',2,0],['1002','9',0,7]]);
assert(rows.slice(1,3).every(row=>row.subarray(0,33).equals(rows[0].subarray(0,33))));
const recipient='pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU';
assert.equal(base58.encode(rows[3].subarray(0,33)),recipient);
assert.equal(base58.encode(rows[4].subarray(0,33)),author);
assert(rows.slice(3).every((row,index)=>row.subarray(41,57).equals(Buffer.alloc(16)) && row[57]===index && row[58]===0));
const message=wire.subarray(93+59*rows.length,93+59*rows.length+length);
const body=JSON.parse(message.toString('utf8'));
assert.deepEqual(Object.keys(body),['module','request','amount','from','to','hash','memo']);
assert.equal(body.module,'Saito');assert.equal(body.request,'crypto payment');assert.equal(body.amount,'6.25');
assert.equal(body.from,author);assert.equal(body.to,recipient);assert.equal(body.memo,'Native transfer test');
assert.match(body.hash,/^[a-z0-9-]{36}$/);
assert(Buffer.from(JSON.stringify(body)).equals(message));
const preimage=Buffer.concat([wire.subarray(80,88),...rows.map(row=>Buffer.concat([row.subarray(0,41),row.subarray(57,59)])),
  wire.subarray(88,92),Buffer.alloc(4),message]);
const signature=wire.subarray(16,80).toString('hex');
assert(wasm.verify_signature(preimage,signature,author));
const ec=new EC('secp256k1');
assert(ec.verify(wasm.hash(preimage),{r:signature.slice(0,64),s:signature.slice(64)},rows[0].subarray(0,33).toString('hex'),'hex'));
const restored=wasm.WasmTransaction.deserialize(wire);
restored.generate_hash_for_signature();
assert.equal(Buffer.from(restored.get_hash_for_signature()).toString('hex'),wasm.hash(preimage));
assert(Buffer.from(restored.serialize()).equals(wire));
const hop=wire.subarray(wire.length-130);
assert(hop.subarray(0,33).equals(rows[0].subarray(0,33)));
const peer='jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR'; // Controlled peer's published unsafe identity.
assert.equal(base58.encode(hop.subarray(33,66)),peer);
const hopInput=Buffer.concat([wire.subarray(16,80),hop.subarray(33,66)]),hopSignature=hop.subarray(66).toString('hex');
assert(wasm.verify_signature(hopInput,hopSignature,author));
assert(ec.verify(wasm.hash(hopInput),{r:hopSignature.slice(0,64),s:hopSignature.slice(64)},rows[0].subarray(0,33).toString('hex'),'hex'));
process.stdout.write(JSON.stringify({synthetic:true,reference,boundary:'Actual controlled native frame: serialization/signature/hop compatibility, not consensus admission or real UTXOs',
  author,recipient,amount:'625000000',fee:'10000000',change:'115000000',signature,frameBytes:frame.length,
  frameHash:wasm.hash(frame),authorPreimageHash:wasm.hash(preimage),checks:['literal slips and metadata','WASM and Elliptic author signature','WASM deserialize/reserialize','WASM and Elliptic routing hop']},null,2)+'\n');
