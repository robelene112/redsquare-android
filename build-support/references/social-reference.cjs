// Pure pinned WASM/independent Elliptic vectors. No app initialization or network.
const fs = require('node:fs');
const path = require('node:path');
const {reference,signedReference} = require('./signed-reference.cjs');
const root = JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
function action(request,timestamp) {
  const body={module:'RedSquare',request,data:{signature:root.sig}};
  const row=signedReference('2',body,timestamp,[root.field2]);
  const envelope=JSON.parse(row.tx);
  const message=Buffer.from(envelope.m,'base64');
  const network=Buffer.concat([Buffer.from(envelope.t,'base64'),message]);
  network.writeUInt32BE(message.length,8);
  const wasm=require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
  const restored=wasm.WasmTransaction.deserialize(network);
  require('node:assert/strict').equal(restored.signature,row.sig);
  require('node:assert/strict').equal(Buffer.from(restored.data).toString('hex'),message.toString('hex'));
  return {...row,body,timestamp,recipients:[root.field2],networkBase64:network.toString('base64')};
}
console.log(JSON.stringify({synthetic:true,reference,target:root.sig,
  like:action('like tweet',1700000050000),repost:action('retweet',1700000050001)},null,2));
