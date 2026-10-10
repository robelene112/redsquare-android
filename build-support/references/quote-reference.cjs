// Pure pinned WASM/Elliptic reference; network disabled, no Saito initialization.
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {reference,signedReference}=require('./signed-reference.cjs');
const root=JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
const originalBody=JSON.parse(Buffer.from(JSON.parse(root.tx).m,'base64'));
const snapshot={signature:root.sig,publicKey:root.field2,created_at:root.created_at,text:originalBody.data.text,images:[]};
const invalidOriginal=JSON.parse(root.tx);
invalidOriginal.m=Buffer.from(JSON.stringify({...originalBody,data:{text:'Altered original'}})).toString('base64');
function quote(request,data,timestamp) {
 const body={module:'RedSquare',request,data};
 const row=signedReference('2',body,timestamp,[root.field2]);
 const envelope=JSON.parse(row.tx);
 const message=Buffer.from(envelope.m,'base64');
 const network=Buffer.concat([Buffer.from(envelope.t,'base64'),message]);
 network.writeUInt32BE(message.length,8);
 const wasm=require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
 const restored=wasm.WasmTransaction.deserialize(network);
 assert.equal(restored.signature,row.sig);
 assert.equal(Buffer.from(restored.data).toString('hex'),message.toString('hex'));
 return {...row,body,networkBase64:network.toString('base64')};
}
console.log(JSON.stringify({synthetic:true,reference,original:root,
 legacy:quote('create tweet',{text:'Historical quote commentary',signature:root.sig,retweet_tx:root.tx},1700000060000),
 embedded:quote('create tweet',{text:'Embedded quote commentary',signature:root.sig,embedded:snapshot},1700000060001),
 current:quote('retweet',{text:'Current commentary retweet',signature:root.sig},1700000060002),
 dual:quote('create tweet',{text:'Native dual quote commentary',signature:root.sig,retweet_tx:root.tx,embedded:snapshot},1700000060003),
 invalidOriginal:quote('create tweet',{text:'Commentary survives an invalid original',signature:root.sig,retweet_tx:JSON.stringify(invalidOriginal)},1700000060004)
},null,2));
