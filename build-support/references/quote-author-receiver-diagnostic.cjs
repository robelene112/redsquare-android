// Source diagnosis only: synthetic transaction objects, no signature verification.
// Executes unchanged pinned receiver methods against a stream-aware Archive adapter.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const quoteFixture = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const actions = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
const sourceDirectory = process.argv[4];

function transaction(row) {
  const outer = JSON.parse(row.tx);
  const msg = JSON.parse(Buffer.from(outer.m, 'base64').toString('utf8'));
  return {signature: row.sig, timestamp: row.created_at, from: [{publicKey: row.field2}],
    optional: {}, msg, returnMessage() { return this.msg; }, serialize_to_web() { return row.tx; }};
}

async function main() {
  const observations = [];
  for (const pin of ['A', 'B']) {
    const source = fs.readFileSync(path.join(sourceDirectory, `${pin}-node_mods_redsquare_redsquare.js`), 'utf8');
    const sourceSha256 = crypto.createHash('sha256').update(source).digest('hex');
    assert.equal(sourceSha256, {
      A: 'e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8',
      B: '2a83af18010fc4fc044aabec2741ff401c93b4672decdd5d78e1db5f81d4c5b0'
    }[pin]);
    const start = source.indexOf('  async receiveEditTweetTransaction(');
    const end = source.indexOf('\n  async ', source.indexOf('  async receiveDeleteTweetTransaction(', start) + 1);
    assert(start > 0 && end > start);
    for (const [row, stream] of [[actions.original, 'RedSquare'], [quoteFixture.current, 'RedSquareRetweet']]) {
      const receiver = vm.runInNewContext(`new (class {${source.slice(start, end)}\n})()`, {}, {timeout: 2000});
      const target = transaction(row);
      let present = true, updates = 0;
      const commands = [], lookups = [];
      receiver.getTweet = () => null;
      receiver.app = {BROWSER: false, storage: {
        loadTransactions(query, callback, destination) {
          assert.equal(destination, 'localhost');
          lookups.push({stream: query.field1, signature: query.sig});
          return callback(present && query.field1 === stream && query.sig === target.signature ? [target] : []);
        },
        async updateTransaction(tx, metadata, destination) {
          assert.equal(tx, target); assert.equal(destination, 'localhost'); updates++;
        },
        async deleteTransaction(tx, metadata, destination) {
          assert.equal(tx, target); assert.equal(destination, 'localhost'); present = false;
        },
        async saveTransaction(tx, metadata, destination) {
          assert.equal(metadata.field1, 'RedSquare'); assert.equal(destination, 'localhost');
          commands.push(tx.signature);
        }
      }};
      function command(reference) {
        const object = transaction(reference);
        // Adapt the public object fields for this lookup experiment. Bytes are not re-signed.
        object.from[0].publicKey = target.from[0].publicKey;
        object.msg.data.tweet_id = target.signature;
        return object;
      }
      await receiver.receiveEditTweetTransaction(command(actions.edit));
      await receiver.receiveDeleteTweetTransaction(command(actions.deleted));
      assert(commands.includes(actions.deleted.sig));
      if (stream === 'RedSquare') {
        assert.equal(updates, 1); assert.equal(present, false);
      } else {
        assert.equal(updates, 0); assert.equal(present, true);
        assert(lookups.every(query => query.stream === 'RedSquare'));
      }
      observations.push({pin, sourceSha256,
        targetStream: stream, targetRequest: target.msg.request,
        editApplied: updates === 1, originalRemoved: !present,
        deleteCommandArchived: true, lookupStreams: [...new Set(lookups.map(query => query.stream))]});
    }
  }
  console.log(JSON.stringify({result: 'KNOWN_LIMITATION_REPRODUCED', nodeVersion: process.version,
    boundary: 'Unmodified pinned receiver methods with synthetic transaction objects and a stream-aware in-memory Archive adapter. No signature, native-frame, live-node, DOM or deployed-behavior proof.',
    observations}, null, 2));
}
main().catch(() => { process.stderr.write('Quote author receiver diagnosis failed\n'); process.exitCode = 1; });
