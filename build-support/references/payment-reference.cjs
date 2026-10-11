// Pure pinned WASM/Elliptic funded bytes. Public unsafe scalars1/2/3, never fund.
// Run network-none/read-only/env-i. Never initializes any Saito wallet/application.
const assert = require('node:assert/strict');
const {reference} = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const privateKey = '0'.repeat(63) + '1';
const author = wasm.generate_public_key(privateKey);
const recipient = wasm.generate_public_key('0'.repeat(63) + '2');
const destination = wasm.generate_public_key('0'.repeat(63) + '3');
const inputs = [
  {amount:'500000000', block:'2000000', ordinal:'7', index:2, type:0},
  {amount:'250000000', block:'2000001', ordinal:'9', index:0, type:7}
];
const body = {module:'Saito', request:'crypto payment', amount:'3.25', from:author,
  to:recipient, hash:'synthetic-transfer-01', memo:'Native transfer reference'};
const message = Buffer.from(JSON.stringify(body));
assert.equal(message.length,233);
const outputs = [{owner:recipient,amount:'325000000',index:0,type:0},
  {owner:author,amount:'415000000',index:1,type:0}];
const tx = new wasm.WasmTransaction();
const signatureSlips = [];
for (const [input, row] of [
  [true,{owner:author,amount:'0',block:'0',ordinal:'0',index:0,type:0}],
  ...inputs.map(row => [true,{owner:author,...row}]),
  ...outputs.map(row => [false,{block:'0',ordinal:'0',...row}])
]) {
  const slip = new wasm.WasmSlip();
  slip.public_key=row.owner; slip.amount=BigInt(row.amount);
  slip.block_id=BigInt(row.block); slip.tx_ordinal=BigInt(row.ordinal);
  slip.slip_index=row.index; slip.slip_type=row.type;
  if(input) tx.add_from_slip(slip); else tx.add_to_slip(slip);
  const amount=Buffer.alloc(8);amount.writeBigUInt64BE(BigInt(row.amount));
  signatureSlips.push(Buffer.concat([Buffer.from(base58.decode(row.owner)),amount,Buffer.from([row.index,row.type])]));
}
const timestamp=1700000000000;
tx.timestamp=BigInt(timestamp);tx.txs_replacements=1;tx.type=0;tx.data=message;
const time=Buffer.alloc(8);time.writeBigUInt64BE(BigInt(timestamp));
const preimage=Buffer.concat([time,...signatureSlips,Buffer.from([0,0,0,1]),Buffer.alloc(4),message]);
tx.generate_hash_for_signature();
assert.equal(Buffer.from(tx.get_hash_for_signature()).toString('hex'),wasm.hash(preimage));
assert.equal(wasm.hash(preimage),'5844cda6d091a7734ffdba6c8617c6b2d8e9a5c5cbe68aade34550462cf65648');
tx.signature=wasm.sign_buffer(preimage,privateKey);
assert.equal(tx.signature,'ca7881660dd0370fe554b8d041c31d196669a9db3202a604eb50618eccf39a2d09b45ee53f48a0136e93e83ea2db0c3dda53dce869d9f0c80e0cac4e13da4426');
assert(wasm.verify_signature(preimage,tx.signature,author));
assert(new EC('secp256k1').verify(wasm.hash(preimage),
  {r:tx.signature.slice(0,64),s:tx.signature.slice(64)},Buffer.from(base58.decode(author)).toString('hex'),'hex'));
const network=Buffer.from(tx.serialize());
assert.equal(network.length,621);
assert.equal(wasm.hash(network),'cc3e274ec4089ac08145a42e215e28287122fc028b641ef3ee17e9723d7d5717');
assert(Buffer.from(wasm.WasmTransaction.deserialize(network).serialize()).equals(network));
const hopInput=Buffer.concat([Buffer.from(tx.signature,'hex'),Buffer.from(base58.decode(destination))]);
const hopSignature=wasm.sign_buffer(hopInput,privateKey);
assert(wasm.verify_signature(hopInput,hopSignature,author));
const routed=Buffer.from(network);routed.writeUInt32BE(1,12);
const broadcast=Buffer.concat([Buffer.from([4]),routed,Buffer.from(base58.decode(author)),
  Buffer.from(base58.decode(destination)),Buffer.from(hopSignature,'hex')]);
assert.equal(broadcast.length,752);
assert.equal(wasm.hash(broadcast),'23b58fd19930513d56035ab99bf4926fba58b58774f3c379e53882cf352c7ecf');
tx.data=Buffer.alloc(0);
const envelope=JSON.stringify({t:Buffer.from(tx.serialize()).toString('base64'),m:message.toString('base64'),
  opt:Buffer.from('{}').toString('base64')});
process.stdout.write(JSON.stringify({synthetic:true,reference,
  boundary:'pure serialization/signature compatibility, not consensus admission or real UTXOs',
  payment:{author,recipient,destination,timestamp,inputs,body,amount:'325000000',fee:'10000000',change:'415000000',
    id:tx.signature,networkBase64:network.toString('base64'),broadcastBase64:broadcast.toString('base64'),envelope}},null,2)+'\n');
