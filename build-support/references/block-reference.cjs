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
function signHeader(signedHeader) {
 const preimage=Buffer.concat([signedHeader.subarray(4,117),signedHeader.subarray(181,213),signedHeader.subarray(221,285)]);
 assert.equal(preimage.length,209);
 const signature=wasm.sign_buffer(preimage,creatorScalar);
 assert(wasm.verify_signature(preimage,signature,creator));
 assert(new EC('secp256k1').verify(wasm.hash(preimage),{r:signature.slice(0,64),s:signature.slice(64)},Buffer.from(base58.decode(creator)).toString('hex'),'hex'));
 Buffer.from(signature,'hex').copy(signedHeader,117);
 return {signature,hash:wasm.hash(Buffer.concat([signedHeader.subarray(20,52),Buffer.from(wasm.hash(preimage),'hex')]))};
}
const {signature,hash}=signHeader(header);
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
// Let pinned Rust compute an odd root from all entries, including opaque/empty system leaves.
const opaque=new wasm.WasmTransaction();opaque.type=2;opaque.timestamp=1700000001000n;
opaque.txs_replacements=1;opaque.data=Buffer.from([0xff,0x00,0x80,0x41]);
const fee=new wasm.WasmTransaction();fee.type=1;fee.timestamp=1700000010000n;fee.txs_replacements=1;
const opaqueBytes=Buffer.from(opaque.serialize()),feeBytes=Buffer.from(fee.serialize());
assert.equal(opaqueBytes.length,97);assert.equal(feeBytes.length,93);
const oddSeedHeader=Buffer.from(header);oddSeedHeader.writeUInt32BE(3,0);
oddSeedHeader.fill(0,85,181);
const oddBlock=new wasm.WasmBlock();assert.equal(oddBlock.deserialize(Buffer.concat([oddSeedHeader,opaqueBytes,payment,feeBytes])),'');
const oddUnsigned=Buffer.from(oddBlock.serialize()),oddHeader=Buffer.from(oddUnsigned.subarray(0,389));
const oddRoot=oddHeader.subarray(85,117).toString('hex');
assert.equal(oddRoot,'0c055916f67c20a7204ac29723e048068c355cc32cd8b30940f2a361bebcee8f');
const oddLeaves=oddBlock.transactions.map(tx=>Buffer.from(tx.get_hash_for_signature()));
assert.equal(oddRoot,wasm.hash(Buffer.concat([Buffer.from(wasm.hash(Buffer.concat(oddLeaves.slice(0,2))),'hex'),oddLeaves[2]])));
const {hash:oddHash}=signHeader(oddHeader);
const oddPayment=Buffer.from(expectedPayment);
for(let i=0;i<2;i++)oddPayment.writeBigUInt64BE(1n,93+3*59+i*59+49);
const oddBytes=Buffer.concat([oddHeader,opaqueBytes,oddPayment,feeBytes]);
const oddAgain=new wasm.WasmBlock();assert.equal(oddAgain.deserialize(oddBytes),'');
assert(Buffer.from(oddAgain.serialize()).equals(oddBytes));assert.equal(oddBytes.length,1200);
assert.deepEqual(oddAgain.transactions.map(tx=>tx.type),[2,0,1]);
assert.equal(oddAgain.transactions[1].signature,vector.payment.id);
assert.deepEqual(oddAgain.transactions[1].to.map(s=>s.tx_ordinal),[1n,1n]);
assert.equal(oddAgain.hash,oddHash);
assert.equal(oddHash,'6ed747ba2da6068f793a60291131ae086a623576dbd2557c31ad854640945856');
// A complete descendant lets a caller prove ancestry, not just find a named block.
const tipSeedHeader=Buffer.from(header);tipSeedHeader.writeUInt32BE(1,0);
tipSeedHeader.writeBigUInt64BE(2000003n,4);tipSeedHeader.writeBigUInt64BE(1700000020000n,12);
Buffer.from(oddHash,'hex').copy(tipSeedHeader,20);tipSeedHeader.fill(0,85,181);
const tipBlock=new wasm.WasmBlock();assert.equal(tipBlock.deserialize(Buffer.concat([tipSeedHeader,feeBytes])),'');
const tipHeader=Buffer.from(tipBlock.serialize()).subarray(0,389);
const {hash:tipHash}=signHeader(tipHeader);
const tipBytes=Buffer.concat([tipHeader,feeBytes]);
const tipAgain=new wasm.WasmBlock();assert.equal(tipAgain.deserialize(tipBytes),'');
assert(Buffer.from(tipAgain.serialize()).equals(tipBytes));assert.equal(tipBytes.length,482);
assert.equal(tipAgain.previous_block_hash,oddHash);assert.equal(tipAgain.id,2000003n);
assert.equal(tipAgain.hash,tipHash);
assert.equal(tipHash,'bf72deac4b8c7dae48258dd2f4c7e4c3f13ed986e27da7650abdf573027579ba');
process.stdout.write(JSON.stringify({synthetic:true,reference,
 boundary:'Full-block framing, creator/header identity and exact payment bytes only, not consensus admission, current chain membership or finality',
 block:{id:'2000002',timestamp:'1700000010000',previousHash:'ab'.repeat(32),creator,signature,hash,
 merkleRoot:root.toString('hex'),bytes:bytes.length,fullBase64:bytes.toString('base64'),seedBase64:seed.toString('base64')},
 payment:{id:vector.payment.id,candidateBase64:vector.payment.networkBase64,inBlockBase64:expectedPayment.toString('base64'),author:vector.payment.author,
 recipient:vector.payment.recipient,amount:vector.payment.amount,fee:vector.payment.fee,change:vector.payment.change},
 mutations:{changedOriginalInputBase64:changedInputBytes.toString('base64')},
 oddBlock:{hash:oddHash,merkleRoot:oddRoot,leafHashes:oddLeaves.map(b=>b.toString('hex')),
 types:[2,0,1],opaqueHex:'ff008041',fullBase64:oddBytes.toString('base64'),bytes:oddBytes.length,paymentOrdinal:'1'},
 chainTip:{hash:tipHash,id:'2000003',timestamp:'1700000020000',previousHash:oddHash,
 fullBase64:tipBytes.toString('base64'),bytes:tipBytes.length}},null,2)+'\n');
