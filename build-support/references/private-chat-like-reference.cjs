// Public unsafe fixture research only. Run network-none/read-only/env-i; no Saito app initialization.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const root = '/opt/saito/node/node_modules/';
const ts = require(root + 'typescript');
const wasm = require(root + 'saito-wasm/pkg/node');
const JSONBig = require(root + 'json-bigint');
assert.equal(require(root + 'saito-js/package.json').version,'0.3.2');
assert.equal(require(root + 'typescript/package.json').version,'5.4.5');
assert.equal(require(root + 'json-bigint/package.json').version,'1.0.0');
const Slip = require(root + 'saito-js/lib/slip').default;
const {reference, signedReference} = require('./signed-reference.cjs');
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const source = fs.readFileSync('/reference/chat.js', 'utf8');
assert.equal(hash(source), '35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af');
function method(text, className, name, globals = {}) {
  const ast = ts.createSourceFile('reference.js', text, ts.ScriptTarget.ES2022, true);
  const cls = ast.statements.find(node => ts.isClassDeclaration(node) && node.name.text === className);
  const member = cls.members.find(node => ts.isMethodDeclaration(node) && node.name.getText(ast) === name);
  assert(member, name);
  return vm.runInNewContext(`({${member.getText(ast)}}).${name}`, globals);
}
const create = method(source, 'Chat', 'createChatLikeTransaction');
const receive = method(source, 'Chat', 'receiveChatLikeTransaction', {console});
const transactionSource = fs.readFileSync(root + 'saito-js/lib/transaction.js', 'utf8');
const toJson = method(transactionSource, 'Transaction', 'toJson', {Buffer, Uint8Array});
const packData = method(transactionSource, 'Transaction', 'packData', {Buffer, Uint8Array});
const keys = Object.fromEntries(['1','2'].map(value => [value, wasm.generate_public_key('0'.repeat(63) + value)]));
const original = JSON.parse(fs.readFileSync('/reference/chat-reference.json')).vectors[0];
assert.equal(original.sender, keys['1']);
const checkingNative = process.argv[2] === '--check-native-like';
const nativeBytes = checkingNative ? fs.readFileSync('/reference/native-sent-like-envelope.json') : null;
const nativeEnvelope = nativeBytes ? JSON.parse(nativeBytes) : null;
let timestamps = [1700000080100, 1700000080101];
if (nativeEnvelope) {
  const detached = Buffer.from(nativeEnvelope.t,'base64');
  const payload = Buffer.from(nativeEnvelope.m,'base64');
  assert.equal(detached.readUInt32BE(8),0);
  const offset = 93 + 59 * (detached.readUInt32BE(0) + detached.readUInt32BE(4));
  const network = Buffer.concat([detached.subarray(0,offset),payload,detached.subarray(offset)]);
  network.writeUInt32BE(payload.length,8);
  const tx = wasm.WasmTransaction.deserialize(network);
  assert.deepEqual(tx.from.map(slip => slip.public_key),[keys['2']]);
  assert.deepEqual(tx.to.map(slip => slip.public_key),[keys['2'],keys['1']]);
  const timestamp = Number(tx.timestamp);
  assert(Number.isSafeInteger(timestamp) && timestamp >= 0);
  timestamps = [timestamp,timestamp+1];
}
const vectors = [];
(async () => {
  for (const timestamp of timestamps) {
    const recipients = [], propagated = [], relays = [];
    const candidate = {
      addTo: key => recipients.push(key),
      async sign() {
        const signed = signedReference('2', this.msg, timestamp, recipients);
        this.envelope = signed.tx;
        const envelope = JSON.parse(signed.tx);
        const network = Buffer.concat([Buffer.from(envelope.t,'base64'),Buffer.from(envelope.m,'base64')]);
        network.writeUInt32BE(Buffer.from(envelope.m,'base64').length,8);
        this.networkBase64 = network.toString('base64');
        const tx = wasm.WasmTransaction.deserialize(network);
        assert.equal(tx.signature,signed.sig);
        this.data = new Uint8Array(tx.data);
        this.from = tx.from.map(slip => new Slip(slip));
        this.to = tx.to.map(slip => new Slip(slip));
        this.type = tx.type; this.timestamp = Number(tx.timestamp); this.signature = tx.signature;
        this.txs_replacements = tx.txs_replacements; this.total_fees = tx.total_fees; this.routing_path = [];
      },
      packData,
      toJson() { return toJson.call(this); },
      returnMessage() { return this.msg; },
    };
    const group = {id:original.groupId,members:[keys['1'],keys['2']]};
    const adapter = {publicKey:keys['2'],app:{
      wallet:{createUnsignedTransactionWithDefaultFee:async () => candidate},
      network:{propagateTransaction:async tx => propagated.push(tx)},
      connection:{emit:(name,data) => relays.push({name,data})},
    }};
    assert.equal(await create.call(adapter,group,original.signature,keys['1']),candidate);
    assert.deepEqual(JSON.parse(JSON.stringify(candidate.msg)), {
      module:'Chat',request:'chat like',group_id:group.id,sender:keys['2'],
      signature:original.signature,mentioned:keys['1'],
    });
    assert.deepEqual([...new Set(recipients)].sort(),[keys['1'],keys['2']].sort());
    assert.deepEqual(propagated,[candidate]);
    assert.equal(relays.length,1); assert.equal(relays[0].name,'relay-send-message');
    assert.equal(relays[0].data.request,'chat relay');
    assert.deepEqual(Array.from(relays[0].data.recipient),group.members);
    assert.equal(relays[0].data.data.signature,candidate.signature);
    assert.equal(relays[0].data.data.buffer,JSON.parse(candidate.envelope).m);
    vectors.push({timestamp,body:JSON.parse(JSON.stringify(candidate.msg)),signature:candidate.signature,
      archiveEnvelope:candidate.envelope,networkBase64:candidate.networkBase64,signedInner:candidate.toJson()});
  }
  if (nativeEnvelope) {
    assert.deepEqual(JSON.parse(vectors[0].archiveEnvelope),nativeEnvelope);
    assert.deepEqual(JSON.parse(Buffer.from(nativeEnvelope.m,'base64').toString('utf8')),vectors[0].body);
  }
  const target = {signature:original.signature,likes:0,liked_by:{}};
  const group = {id:original.groupId,txs:[target]};
  const events = [];
  const adapter = {returnGroup:id => id === group.id ? group : null,
    app:{connection:{emit:(name,value) => events.push({name,group:value.id})}}};
  await receive.call(adapter,{returnMessage:() => vectors[0].body});
  assert.equal(target.likes,1); assert.equal(target.liked_by[keys['2']],true);
  await receive.call(adapter,{returnMessage:() => vectors[1].body});
  assert.equal(target.likes,0); assert.equal(target.liked_by[keys['2']],false);
  assert.equal(events.length,2); assert(events.every(event => event.name === 'chat-popup-render-request'));
  console.log(JSONBig.stringify({reference,chatSourceSha256:hash(source),originalSignature:original.signature,
    ...(nativeEnvelope ? {nativeEnvelopeSha256:hash(nativeBytes),independentWasmOriginalMatches:true,
      existingConstructorMatchesNativeLike:true,existingReceiverAcceptsNativeLike:true} : {}),
    groupId:original.groupId,author:keys['1'],reactor:keys['2'],vectors,
    existingReceiverAddsAndRemovesLike:true,
    boundary:'actual pinned Chat create/receive and Transaction toJson/packData; wallet fee/signing and outbound network adapters; independent WASM/elliptic signing; no constructor, DOM, app initialization or real delivery; dispatch deduplication not executed'},null,2));
})().catch(error => {console.error(error);process.exitCode=1;});
