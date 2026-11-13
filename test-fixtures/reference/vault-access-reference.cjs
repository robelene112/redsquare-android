// Official contract bodies and actual WASM script hashing, with public unsafe identities only.
const fs = require('fs'), path = require('path'), crypto = require('crypto');
if (process.argv.length !== 5) throw Error('Usage: node vault-access-reference.cjs reference-runtime pinned-source-root output');
const runtime = path.resolve(process.argv[2]), root = path.resolve(process.argv[3]);
const hashes = {
  'node/mods/vault/lib/contracts/custom.js': 'e16a289d21a55e0c82a150a00319603bdd2298a9dae086316e71c833a1d95a82',
  'node/mods/vault/lib/contracts/rental.js': 'c0385317ee08e703bd17b9a9655c142bf1179f3336cd98485a95b792abc3b8e8',
  'node/mods/vault/lib/contracts/loan.js': '870877c0cb6b5926023a9e3e624ebee35cbdb12c82bec9a3eb4fb19aff328156'
};
for (const [name, expected] of Object.entries(hashes)) {
  if (crypto.createHash('sha256').update(fs.readFileSync(path.join(root, name))).digest('hex') !== expected) throw Error('Wrong pinned contract: ' + name);
}
if (require(path.join(runtime, 'node_modules/saito-wasm/package.json')).version !== '0.3.15') throw Error('Wrong reference runtime');
const wasm = require(path.join(runtime, 'node_modules/saito-wasm/pkg/node'));
const creator = wasm.generate_public_key('0'.repeat(63) + '2');
const renter = wasm.generate_public_key('0'.repeat(63) + '1');
const rental = require(path.join(root, 'node/mods/vault/lib/contracts/rental.js')).build({creator_publickey: creator});
const loan = require(path.join(root, 'node/mods/vault/lib/contracts/loan.js')).instantiate({creator_publickey: creator, renter_publickey: renter, expires_at: 1700001000000});
const custom = require(path.join(root, 'node/mods/vault/lib/contracts/custom.js')).script;
const scripts = [
  ['official-custom', custom], ['official-rental-master', rental], ['official-instantiated-loan', loan],
  ['unicode-nested-witness', {op: 'AND', args: [
    {op: 'CHECKHASH', hash: wasm.hash(Buffer.from('hello')), witness: {input: 'hello'}},
    {op: 'CHECKFIELD', field: 'db.owner', operator: '==', value: 'café \u2028 <none> 🎉'}
  ], details: {'\uffff': 2, '🎉': 1, witness: {nested: 'excluded from commitment'}}}],
  ['different-witness-same-contract', {...custom, witness: {input: 'a different supplied witness'}}]
].map(([name, script]) => ({name, source: JSON.stringify(script), hash: wasm.get_script_hash(JSON.stringify(script))}));
// Keep the editor's original JSON spelling. JSON.stringify would erase the Rust
// distinction between integer and floating numbers before it reaches the oracle.
const numbers = ['1', '1.0', '1e2', '100.0', '1e7', '1e16', '0.000001', '1e-7', '-0', '-0.0',
  '0e0', '1e23', '1.2345678901234567', '18446744073709551615', '18446744073709551616', '-9223372036854775809'];
const numericScripts = numbers.map(value => {
  const source = '{"op":"CHECKFIELD","field":"context.value","operator":"==","value":' + value + '}';
  return {name: value, source, hash: wasm.get_script_hash(source)};
});
const witnessCases = [
  [custom, [{input: 'hello'}]],
  [{op: 'AND', args: [{...custom, witness: {input: 'keep existing'}}, {...custom}, {op: 'CHECKSENDER', publickey: creator}]},
    [{input: 'next slot'}, {proof: 'sender slot'}]],
  [{op: 'NOT', args: [{...custom, witness: {}}, {...custom}]}, [{input: 'first child only'}, {input: 'unused'}]],
  [{op: 'OR', args: [{...custom}, {op: 'FUTURE_OPCODE', args: [{...custom}]}]}, [null, {input: 'outer unknown rule'}, {input: 'unused'}]]
].map(([script, witnesses]) => {
  const source = JSON.stringify(script), inputs = JSON.stringify(witnesses);
  return {source, inputs, merged: wasm.merge_witness(source, inputs), hash: wasm.get_script_hash(source)};
});
const output = {authority: {source: '0db0062d873369dc013046ed77c97410b5683b64', saitoWasm: '0.3.15', sourceHashes: hashes,
  qualification: 'Actual WASM script commitments/witness merging and pinned contract builders; no live provider or consensus authorization.'}, creator, renter, scripts, numericScripts, witnessCases};
fs.writeFileSync(path.resolve(process.argv[4]), JSON.stringify(output, null, 2) + '\n');
console.log('Independent Vault access commitments generated:', scripts.length);
