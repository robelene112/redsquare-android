// Pinned Registry bodies with in-memory database/network/keychain adapters.
// Input must be the independently verified actual unsafe1/2 Android specimen.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const specimen = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const directory = process.argv[3];
const reference = JSON.parse(fs.readFileSync(process.argv[4], 'utf8'));
assert.equal(specimen.result, 'PASS'); assert.equal(specimen.native, true);
assert.equal(specimen.syntheticRegistrar, true); assert.equal(reference.synthetic, true);
const registration = specimen.registration;
assert.equal(registration.author, 'jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR');
assert.equal(registration.registrar, reference.registrar);
assert.equal(registration.registrar, 'pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU');
const pins = {
  A: {commit: '9a4ca7e9442eb5e872168ced361ce741d7d139d7',
    registry: '25b7a20074b8465ab5f2119dafb8136e276fd71ee0d02b6485650213cd92e576',
    keychain: 'caf919428572e7525e86405876519a2c8a20a67a6f3f8ef712a0b93f5ca1565d'},
  B: {commit: '214504ecece76d1cd20a245fe4468eccbfca2951',
    registry: 'b069f0ba1f9f9408102f9e144d71840086cc1262b4a7994b718e72176623351d',
    keychain: 'ad57a2848e77ee7c763a5e157fb7d391ed6e36fbe17663430e2509104867907c'}
};
function source(pin, filename, expected) {
  const text = fs.readFileSync(path.join(directory, `${pin}-${filename}`), 'utf8');
  assert.equal(crypto.createHash('sha256').update(text).digest('hex'), expected);
  return text;
}
function method(text, name) {
  const match = new RegExp(`^  (?:async )?${name}\\(`, 'm').exec(text);
  assert(match, `Missing pinned ${name}`);
  const end = text.indexOf('\n  }\n', match.index);
  assert(end > match.index);
  return text.slice(match.index, end + 5);
}
async function main() {
  const observations = [];
  for (const [pin, metadata] of Object.entries(pins)) {
    const text = source(pin, 'node_mods_registry_registry.js', metadata.registry);
    const chunks = ['onConfirmation', 'addRecord', 'checkIdentifierInDatabase',
      'fetchIdentifiersFromDatabase', 'handlePeerTransaction', 'tryRegisterIdentifier', 'queryKeys']
      .map(name => method(text, name)).join('\n');
    const receiver = vm.runInNewContext(`new (class {${chunks}\n})()`, {
      console: {log() {}, warn() {}, error() {}},
      Date: class { getTime() { return 1700002000000; } }
    }, {timeout: 2000});
    const records = new Map(), messages = [];
    receiver.publicKey = registration.registrar;
    receiver.registry_publickey = registration.registrar; // Explicit local unsafe2 fixture authority.
    receiver.domain = '@saito'; receiver.cached_keys = {}; receiver.peers = [];
    receiver.app = {BROWSER: false,
      crypto: {signMessage(message, privateKey) {
        assert.equal(privateKey, '0'.repeat(63) + '2'); // Published unsafe test scalar only.
        const row = reference.rows[0];
        assert.equal(message, row.identifier + row.publickey + row.bid + row.bsh);
        return wasm.sign_buffer(Buffer.from(message, 'utf8'), privateKey);
      }},
      wallet: {
        async getPrivateKey() { return '0'.repeat(63) + '2'; },
        async createUnsignedTransaction(owner, amount, fee) {
          assert.equal(owner, registration.author); assert.equal(amount, 0n); assert.equal(fee, 0n);
          return {msg: {}, async sign() { this.signed = true; }};
        },
        async createUnsignedTransactionWithDefaultFee(recipient) {
          assert.equal(recipient, registration.registrar);
          return {msg: {}, async sign() { this.signed = true; }};
        }
      },
      network: {async propagateTransaction(tx) { assert.equal(tx.signed, true); messages.push({...tx.msg}); }},
      storage: {
        async runDatabase(sql, params, database) {
          assert.equal(database, 'registry'); assert(sql.includes('INSERT OR IGNORE INTO records'));
          assert.equal(params.$identifier, registration.body.identifier);
          assert.equal(params.$publickey, registration.author);
          if (records.has(params.$identifier)) return {changes: 0};
          records.set(params.$identifier, Object.fromEntries(Object.entries(params).map(([key, value]) => [key.slice(1), value])));
          return {changes: 1};
        },
        async queryDatabase(sql, params, database) {
          assert.equal(database, 'registry');
          if (Array.isArray(params)) {
            assert.equal(sql, 'SELECT * FROM records WHERE identifier = ?');
            assert.deepEqual(Array.from(params), [registration.body.identifier]);
            return records.has(params[0]) ? [records.get(params[0])] : [];
          }
          assert.equal(sql.replace(/\s+/g, ' ').trim(), `SELECT * FROM records WHERE publickey in ("${registration.author}")`);
          return Array.from(records.values()).filter(row => row.publickey === registration.author);
        }
      }
    };
    const tx = {from: [{publicKey: registration.author}],
      to: [{publicKey: registration.registrar}, {publicKey: registration.author}],
      returnMessage() { return registration.body; },
      isTo(key) { return this.to.some(slip => slip.publicKey === key); }};
    const block = {id: 17, hash: 'a'.repeat(64)};
    await receiver.onConfirmation(block, tx, 1);
    assert.equal(records.size, 0); assert.equal(messages.length, 0);
    receiver.publicKey = registration.author;
    await receiver.onConfirmation(block, tx, 0);
    assert.equal(records.size, 0); assert.equal(messages.length, 0);
    receiver.publicKey = registration.registrar;
    await receiver.onConfirmation(block, {...tx, isTo() { return false; }}, 0);
    assert.equal(records.size, 0); assert.equal(messages.length, 0);
    await receiver.onConfirmation(block, tx, 0);
    assert.equal(records.size, 1); assert.equal(messages.length, 1);
    assert.equal(messages[0].title, 'Address Registration Success!');
    const row = records.get(registration.body.identifier);
    for (const [key, value] of Object.entries(reference.rows[0])) assert.equal(row[key], value);
    const preimage = Buffer.from(row.identifier + row.publickey + row.bid + row.bsh, 'utf8');
    assert(wasm.verify_signature(preimage, row.sig, row.signer));
    await receiver.onConfirmation(block, tx, 0);
    assert.equal(records.size, 1); assert.equal(messages[1].title, 'Address Registration Failed!');
    let lookedUp;
    const read = {request: 'registry query', data: {request: 'registry namecheck', identifier: row.identifier}};
    await receiver.handlePeerTransaction(receiver.app, {returnMessage() { return read; }}, {}, rows => { lookedUp = rows; });
    assert.equal(lookedUp.length, 1); assert.equal(lookedUp[0], row);
    let mapping;
    receiver.app.network.sendRequestAsTransaction = async (request, data, callback, peer) => {
      assert.equal(peer, registration.registrar); assert.equal(request, 'registry query');
      assert.equal(data.request, 'registry query'); assert.deepEqual(Array.from(data.keys), [registration.author]);
      await receiver.handlePeerTransaction(receiver.app, {returnMessage() { return {request, data}; }}, {}, callback);
    };
    await receiver.queryKeys({publicKey: registration.registrar}, [registration.author], answer => { mapping = answer; });
    assert.equal(mapping[registration.author], row.identifier);
    await receiver.tryRegisterIdentifier('NativeCase7');
    assert.deepEqual(messages[2], registration.body);
    const keychainText = source(pin, 'node_lib_saito_keychain.ts', metadata.keychain);
    // Only TypeScript interface annotations are removed; the method bodies are unchanged.
    const identifierMethod = method(keychainText, 'returnIdentifierByPublicKey')
      .replace('publicKey: string', 'publicKey').replace('): string {', ') {');
    const usernameMethod = method(keychainText, 'returnUsername')
      .replace('publicKey: string', 'publicKey').replace('): string {', ') {');
    const keychain = vm.runInNewContext(`new (class {${identifierMethod}\n${usernameMethod}\n})()`, {}, {timeout: 2000});
    keychain.returnKey = query => query.publicKey === registration.author ? {identifier: mapping[registration.author]} : null;
    assert.equal(keychain.returnUsername(registration.author), row.identifier);
    observations.push({pin, ...metadata, record: row, display: keychain.returnUsername(registration.author),
      gates: ['nonzero confirmation', 'relay node', 'wrong recipient', 'unique identifier retry'],
      emailBlockHashOverwritten: messages[0].bsh === row.unixtime});
  }
  console.log(JSON.stringify({result: 'PASS', native: true, synthetic: true,
    frameSha256: specimen.frameSha256,
    boundary: 'Actual independently verified Android unsafe1/2 registration passed pinned A/B Registry bodies and keychain display bodies with in-memory adapters. Lookup and browser construction requests are adapter inputs. No live node, actual browser wallet/DOM or chain inclusion proof.',
    observations}, null, 2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
