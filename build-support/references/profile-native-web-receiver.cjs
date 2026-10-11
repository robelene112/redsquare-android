// Unmodified pinned Profile methods, with public in-memory archive/keychain/UI adapters.
// Input is the independently verified actual Android frame report, never wallet options.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const specimen = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const directory = process.argv[3];
const reference = JSON.parse(fs.readFileSync(process.argv[4], 'utf8'));
assert.equal(specimen.result, 'PASS'); assert.equal(specimen.native, true);
assert.equal(reference.synthetic, true);
function transaction(row) {
  const envelope = JSON.parse(row.tx);
  const msg = JSON.parse(Buffer.from(envelope.m, 'base64').toString('utf8'));
  return {signature: row.sig, timestamp: row.created_at, from: [{publicKey: row.field2}],
    returnMessage() { return msg; }, isFrom(key) { return key === row.field2; },
    serialize_to_web() { return row.tx; }};
}
async function main() {
  const observations = [];
  const hashes = {A: '15777808a5e4e88603b87dec7c28af177b418a9a46d0d8278ed569a25fc23d93',
    B: '3839e35cbdf330fae46b5a6d92b71802b801054bbcbc8589aa0718e539d6aad3'};
  for (const pin of ['A', 'B']) {
    const source = fs.readFileSync(path.join(directory, `${pin}-node_mods_profile_profile.js`), 'utf8');
    const sourceSha256 = crypto.createHash('sha256').update(source).digest('hex');
    assert.equal(sourceSha256, hashes[pin]);
    const metadataKeys = /^const PROFILE_INDEX_META_KEYS = .+;$/m.exec(source);
    assert(metadataKeys);
    const methods = ['onConfirmation', 'receiveProfileTransaction', 'saveProfileTransaction',
      'setCachedProfile', 'saveOwnProfileOptions', 'deleteIndexedProfileTransactions',
      'buildProfileFromTransactions', 'isSnapshotMessage', 'sortProfileTransactionsNewestFirst'];
    const chunks = methods.map(name => {
      const match = new RegExp(`^  (?:async )?${name}\\(`, 'm').exec(source);
      assert(match, `Missing pinned ${name}`);
      const end = source.indexOf('\n  }\n', match.index);
      assert(end > match.index);
      return source.slice(match.index, end + 5);
    }).join('\n');
    for (const browser of [false, true]) {
      const events = [], messages = [], stored = [], deleted = [];
      let optionsSaves = 0;
      const receiver = vm.runInNewContext(`${metadataKeys[0]}\nnew (class {${chunks}\n})()`, {
        siteMessage(msg, duration) { messages.push({msg, duration}); },
        console: {debug() {}}
      }, {timeout: 2000});
      const row = specimen.profile, tx = transaction(row), previous = reference.withMedia;
      let key = {publicKey: row.field2, profile: {tx_sig: previous.sig,
        archive_nodes: ['https://archive.example.test']}};
      receiver.publicKey = browser ? row.field2 : reference.foreign.field2;
      receiver.cache = {}; receiver.profile_ready = {};
      receiver.app = {BROWSER: browser, options: {profile: {description: 'Old browser profile'}},
        keychain: {
          isWatched(author) { assert.equal(author, row.field2); return browser; },
          returnKey(author) { assert.equal(author, row.field2); return key; },
          addKey(author, values) { assert.equal(author, row.field2); key = {...key, ...values}; }
        },
        connection: {emit(...args) { events.push(args); }},
        storage: {
          async saveTransaction(actual, metadata, destination) {
            assert.equal(actual, tx); assert.equal(destination, 'localhost');
            assert.deepEqual(JSON.parse(JSON.stringify(metadata)), {field1: 'Profile', preserve: 1});
            stored.push(actual.serialize_to_web());
          },
          async deleteTransaction(sig, metadata, destination) {
            assert.equal(metadata, ''); assert.equal(destination, 'localhost'); deleted.push(sig);
          },
          saveOptions() { optionsSaves++; }
        }
      };
      await receiver.onConfirmation(null, tx, 1);
      assert.equal(stored.length, 0);
      await receiver.onConfirmation(null, tx, 0);
      assert.deepEqual(JSON.parse(JSON.stringify(receiver.cache[row.field2])), row.body.data);
      assert.deepEqual(stored, [row.tx]);
      const reconstructed = receiver.buildProfileFromTransactions([transaction(previous), tx]);
      assert.deepEqual(JSON.parse(JSON.stringify(reconstructed)), row.body.data);
      if (browser) {
        assert.deepEqual(JSON.parse(JSON.stringify(receiver.app.options.profile)), row.body.data);
        assert.equal(optionsSaves, 1);
        assert.deepEqual(deleted, [previous.sig]);
        assert.deepEqual(JSON.parse(JSON.stringify(key.profile)), {tx_sig: row.sig,
          archive_nodes: ['https://archive.example.test']});
        const update = events.find(event => event[0] === 'profile-update-dom');
        assert(update); assert.equal(update[1], row.field2);
        assert.deepEqual(JSON.parse(JSON.stringify(update[2])), row.body.data);
        assert.deepEqual(messages, [{msg: 'Profile updated', duration: 2000}]);
      } else { assert.equal(events.length, 0); assert.equal(optionsSaves, 0); }
      observations.push({pin, browser, sourceSha256,
        actualNativeSnapshotAcceptedAtConfirmationZero: true, completeFieldsPreserved: true,
        archivePreserved: true, newestSnapshotReconstructed: true,
        ownBrowserOptionsAndProfileEventUpdated: browser});
    }
  }
  console.log(JSON.stringify({result: 'PASS', nodeVersion: process.version,
    nativeFrameSha256: specimen.frameSha256,
    boundary: 'Pinned A/B Profile receiving/reconstruction methods with independently verified actual native frame and in-memory Archive/keychain/UI adapters. No live write, deployment, DOM pixels or chain inclusion proof.',
    observations}, null, 2));
}
main().catch(error => { process.stderr.write(`Profile receiver verification failed: ${error.message}\n`); process.exitCode = 1; });
