// Unmodified pinned receiving methods, with an in-memory Archive boundary adapter.
// Input transactions must have been independently verified before this presentation adapter.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const fixture = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const directory = process.argv[3];
if (fixture.native) assert.equal(fixture.result, 'PASS');
function transaction(row) {
  const outer = JSON.parse(row.tx);
  const msg = JSON.parse(Buffer.from(outer.m, 'base64').toString('utf8'));
  return {signature: row.sig, timestamp: row.created_at, from: [{publicKey: row.field2}],
    optional: {}, msg, returnMessage() { return this.msg; }, serialize_to_web() { return row.tx; }};
}
async function main() {
  const observations = [];
  for (const pin of ['A', 'B']) {
    const filename = path.join(directory, `${pin}-node_mods_redsquare_redsquare.js`);
    const source = fs.readFileSync(filename, 'utf8');
    const start = source.indexOf('  async receiveEditTweetTransaction(');
    const end = source.indexOf('\n  async ', source.indexOf('  async receiveDeleteTweetTransaction(', start) + 1);
    assert(start > 0 && end > start);
    const receiver = vm.runInNewContext(`new (class {${source.slice(start, end)}\n})()`, {}, {timeout: 2000});
    const root = transaction(fixture.original);
    let present = true, updates = 0;
    const commands = [];
    receiver.getTweet = () => null; // Exercise the server Archive path, not the untrusted DOM path.
    receiver.app = {BROWSER: false, storage: {
      loadTransactions(query, callback, destination) {
        assert.equal(destination, 'localhost');
        assert.equal(query.field1, 'RedSquare');
        return callback(present && query.sig === root.signature ? [root] : []);
      },
      async updateTransaction(tx, metadata, destination) {
        assert.equal(tx, root); assert.equal(destination, 'localhost');
        assert.equal(metadata.updated_at, root.optional.edit_ts); updates++;
      },
      async deleteTransaction(tx, metadata, destination) {
        assert.equal(tx, root); assert.equal(destination, 'localhost'); present = false;
      },
      async saveTransaction(tx, metadata, destination) {
        assert.equal(metadata.field1, 'RedSquare'); assert.equal(destination, 'localhost');
        commands.push(tx.signature);
      }
    }};
    await receiver.receiveEditTweetTransaction(transaction(fixture.edit));
    assert.equal(root.optional.update_tx, fixture.edit.tx);
    assert.equal(root.signature, fixture.original.sig);
    if (fixture.native) {
      await receiver.receiveEditTweetTransaction(transaction(fixture.edit));
      assert.equal(updates, 1);
      if (fixture.deleted) {
        await receiver.receiveDeleteTweetTransaction(transaction(fixture.deleted));
        assert.equal(present, false);
        assert(commands.includes(fixture.deleted.sig));
      }
      observations.push({pin, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
        actualNativeEditApplied: true, duplicateEditIdempotent: true, originalIdentityPreserved: true,
        actualNativeDeleteRemovesOriginal: fixture.deleted ? true : undefined});
      continue;
    }
    await receiver.receiveEditTweetTransaction(transaction(fixture.newer));
    await receiver.receiveEditTweetTransaction(transaction(fixture.edit));
    await receiver.receiveEditTweetTransaction(transaction(fixture.unrelated));
    await receiver.receiveEditTweetTransaction(transaction(fixture.wrongTarget));
    assert.equal(root.optional.update_tx, fixture.newer.tx);
    assert.equal(updates, 2);
    await receiver.receiveDeleteTweetTransaction(transaction(fixture.unrelatedDelete));
    assert.equal(present, true);
    assert(commands.includes(fixture.unrelatedDelete.sig));
    await receiver.receiveDeleteTweetTransaction(transaction(fixture.deleted));
    assert.equal(present, false);
    assert(commands.includes(fixture.deleted.sig));
    observations.push({pin, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
      authorizedEditPreservesOriginal: true, newestEditWins: true, unrelatedEditRejected: true,
      unrelatedDeleteKeepsOriginalButIsArchived: true, authorizedDeleteRemovesOriginal: true});
  }
  console.log(JSON.stringify({result: 'PASS', nodeVersion: process.version,
    boundary: fixture.native ? 'Pinned server edit receiver with independently verified actual Android frames and in-memory Archive adapter. No deployment, DOM or chain inclusion proof.' :
      'Pinned server receiving methods with independently verified reference vectors and in-memory Archive adapter. No deployment, DOM or native-write proof.', observations}, null, 2));
}
main().catch(() => { process.stderr.write('Author receiver verification failed\n'); process.exitCode = 1; });
