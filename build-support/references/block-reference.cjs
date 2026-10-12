// Independent synthetic full-block preparation. No global Saito initialization.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {reference} = require('/references/signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const vector = JSON.parse(fs.readFileSync('/fixtures/native-payment-reference.json','utf8'));
assert.equal(vector.synthetic,true);assert.deepEqual(vector.reference,reference);
const payment = Buffer.from(vector.payment.networkBase64,'base64');
assert.equal(payment.length,621);
assert.equal(wasm.hash(payment),'cc3e274ec4089ac08145a42e215e28287122fc028b641ef3ee17e9723d7d5717');
const transaction = wasm.WasmTransaction.deserialize(payment);
transaction.generate_hash_for_signature();
const root = Buffer.from(transaction.get_hash_for_signature());
assert.equal(root.toString('hex'),'5844cda6d091a7734ffdba6c8617c6b2d8e9a5c5cbe68aade34550462cf65648');
const creatorScalar = '0'.repeat(63)+'3'; // Published unsafe identity. Never fund.
const creator = wasm.generate_public_key(creatorScalar);
const header = Buffer.alloc(389);
header.writeUInt32BE(1,0);header.writeBigUInt64BE(2000002n,4);
header.writeBigUInt64BE(1700000010000n,12);
Buffer.alloc(32,0xab).copy(header,20);
Buffer.from(base58.decode(creator)).copy(header,52);root.copy(header,85);
const preimage = Buffer.concat([header.subarray(4,117),header.subarray(181,213),header.subarray(221,285)]);
assert.equal(preimage.length,209);
const signature = wasm.sign_buffer(preimage,creatorScalar);
assert(wasm.verify_signature(preimage,signature,creator));
assert(new EC('secp256k1').verify(wasm.hash(preimage),{r:signature.slice(0,64),s:signature.slice(64)},Buffer.from(base58.decode(creator)).toString('hex'),'hex'));
Buffer.from(signature,'hex').copy(header,117);
const hash = wasm.hash(Buffer.concat([header.subarray(20,52),Buffer.from(wasm.hash(preimage),'hex')]));
const seed = Buffer.concat([header,payment]);
const block = new wasm.WasmBlock();assert.equal(block.deserialize(seed),'');
assert.equal(block.hash,hash);assert.equal(block.id,2000002n);assert.equal(block.timestamp,1700000010000n);
assert.equal(block.creator,creator);assert.equal(block.previous_block_hash,'ab'.repeat(32));
assert.equal(block.transactions.length,1);assert(block.has_keylist_txs([vector.payment.author]));
const embedded = block.transactions[0];assert.equal(embedded.signature,vector.payment.id);
assert.deepEqual(embedded.from.slice(1).map(s=>[s.block_id.toString(),s.tx_ordinal.toString(),s.slip_index,s.amount.toString(),s.slip_type]),
 [['2000000','7',2,'500000000',0],['2000001','9',0,'250000000',7]]);
assert.deepEqual(embedded.to.map(s=>[s.public_key,s.block_id.toString(),s.tx_ordinal.toString(),s.slip_index,s.amount.toString(),s.slip_type]),
 [[vector.payment.recipient,'2000002','0',0,'325000000',0],[vector.payment.author,'2000002','0',1,'415000000',0]]);
const bytes = Buffer.from(block.serialize());assert.equal(bytes.length,1010);
assert(bytes.subarray(0,389).equals(header));
const expectedPayment = Buffer.from(payment);
for(let i=0;i<2;i++){const offset=93+3*59+i*59;expectedPayment.writeBigUInt64BE(2000002n,offset+41);expectedPayment.writeBigUInt64BE(0n,offset+49);}
assert(bytes.equals(Buffer.concat([header,expectedPayment])));
const again = new wasm.WasmBlock();assert.equal(again.deserialize(bytes),'');assert.equal(again.hash,hash);
assert(Buffer.from(again.serialize()).equals(bytes));
const lite=block.generate_lite_block([vector.payment.author]);assert.equal(lite.hash,hash);
assert(Buffer.from(lite.serialize()).equals(bytes)); // All transactions relevant in this first fixture.
// Original input coordinates are absent from both creator/transaction preimages.
const changedInputBytes=Buffer.from(bytes);
changedInputBytes.writeBigUInt64BE(2000001n,389+93+59+41);
const changedBlock=new wasm.WasmBlock();assert.equal(changedBlock.deserialize(changedInputBytes),'');
assert.equal(changedBlock.hash,hash);
const changedTransaction=changedBlock.transactions[0];changedTransaction.generate_hash_for_signature();
assert.equal(changedTransaction.signature,vector.payment.id);
assert.equal(Buffer.from(changedTransaction.get_hash_for_signature()).toString('hex'),root.toString('hex'));
assert.equal(changedTransaction.from[1].block_id,2000001n);
assert(Buffer.from(changedBlock.serialize()).equals(changedInputBytes));
process.stdout.write(JSON.stringify({synthetic:true,reference,
 boundary:'Full-block framing, creator/header identity and exact payment bytes only, not consensus admission, current chain membership or finality',
 block:{id:'2000002',timestamp:'1700000010000',previousHash:'ab'.repeat(32),creator,signature,hash,
 merkleRoot:root.toString('hex'),bytes:bytes.length,fullBase64:bytes.toString('base64'),seedBase64:seed.toString('base64')},
 payment:{id:vector.payment.id,candidateBase64:vector.payment.networkBase64,inBlockBase64:expectedPayment.toString('base64'),author:vector.payment.author,
 recipient:vector.payment.recipient,amount:vector.payment.amount,fee:vector.payment.fee,change:vector.payment.change},
 mutations:{changedOriginalInputBase64:changedInputBytes.toString('base64')}},null,2)+'\n');
