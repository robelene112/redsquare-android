// Reference-only registrar proof. Public unsafe scalar1/2, never fund either.
// Run network-none/read-only/env-i. No Saito application initialization.
const assert = require('node:assert/strict');
const { reference } = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const EC = require('/opt/saito/node/node_modules/elliptic').ec;
const owner = wasm.generate_public_key('0'.repeat(63) + '1');
const registrar = wasm.generate_public_key('0'.repeat(63) + '2');
const row = {
  identifier: 'NativeCase7@saito', publickey: owner, bid: 17,
  bsh: 'a'.repeat(64), signer: registrar, lc: 1
};
// Exact A/B Registry.onConfirmation signMessage preimage; crypto.ts signs UTF8.
const message = Buffer.from(row.identifier + row.publickey + row.bid + row.bsh, 'utf8');
row.sig = wasm.sign_buffer(message, '0'.repeat(63) + '2');
assert(wasm.verify_signature(message, row.sig, registrar));
assert(new EC('secp256k1').verify(wasm.hash(message),
  {r: row.sig.slice(0, 64), s: row.sig.slice(64)},
  Buffer.from(base58.decode(registrar)).toString('hex'), 'hex'));

// Browser output order: registrar first, wallet change second. Zero fee/payment.
// Pure WasmTransaction serialization, not an initialized browser wallet or chain.
const recipient = 'sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK';
const body = {module: 'Registry', request: 'register', identifier: 'NativeCase7@saito'};
const timestamp = 1700002000000;
const bytes = Buffer.from(JSON.stringify(body));
const tx = new wasm.WasmTransaction();
for (const [input, key, index] of [[true, owner, 0], [false, recipient, 0], [false, owner, 1]]) {
  const slip = new wasm.WasmSlip();
  slip.public_key = key; slip.amount = 0n; slip.block_id = 0n; slip.tx_ordinal = 0n;
  slip.slip_index = index; slip.slip_type = 0;
  if (input) tx.add_from_slip(slip); else tx.add_to_slip(slip);
}
tx.timestamp = BigInt(timestamp); tx.txs_replacements = 1; tx.type = 0; tx.data = bytes;
const time = Buffer.alloc(8); time.writeBigUInt64BE(BigInt(timestamp));
const slipBytes = (key, index) => Buffer.concat([Buffer.from(base58.decode(key)), Buffer.alloc(8), Buffer.from([index, 0])]);
const preimage = Buffer.concat([time, slipBytes(owner, 0), slipBytes(recipient, 0),
  slipBytes(owner, 1), Buffer.from([0, 0, 0, 1]), Buffer.alloc(4), bytes]);
tx.generate_hash_for_signature();
assert.equal(Buffer.from(tx.get_hash_for_signature()).toString('hex'), wasm.hash(preimage));
tx.signature = wasm.sign_buffer(preimage, '0'.repeat(63) + '1');
assert(wasm.verify_signature(preimage, tx.signature, owner));
assert(new EC('secp256k1').verify(wasm.hash(preimage),
  {r: tx.signature.slice(0, 64), s: tx.signature.slice(64)},
  Buffer.from(base58.decode(owner)).toString('hex'), 'hex'));
const registration = {recipient, body, timestamp, id: tx.signature,
  networkBase64: Buffer.from(tx.serialize()).toString('base64')};
tx.data = Buffer.alloc(0);
registration.envelope = JSON.stringify({t: Buffer.from(tx.serialize()).toString('base64'),
  m: bytes.toString('base64'), opt: Buffer.from('{}').toString('base64')});
process.stdout.write(JSON.stringify({synthetic: true, reference, registrar, rows: [row], registration}, null, 2) + '\n');
