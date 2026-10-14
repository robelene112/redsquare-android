// Public unsafe fixtures only; no Saito initialization, network or persisted wallet.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const wasm=require('/reference/dependencies/node_modules/saito-wasm/pkg/node');
const {reference,signedReference}=require('./signed-reference.cjs');
const key=n=>wasm.generate_public_key('0'.repeat(63)+n);const vectors=[];
const expected={'chat-A.js':'35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af','chat-B.js':'28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb'};
(async()=>{let previous;
for(const [pin,file] of [['9a4ca7e9442eb5e872168ced361ce741d7d139d7','chat-A.js'],['214504ecece76d1cd20a245fe4468eccbfca2951','chat-B.js']]){
 const source=fs.readFileSync(__dirname+'/'+file,'utf8');assert.equal(crypto.createHash('sha256').update(source).digest('hex'),expected[file]);
 const begin=source.indexOf('  async sendRemoveMemberTransaction('),end=source.indexOf('  async receiveRemoveMemberTransaction(',begin);assert(begin>0&&end>begin);const method=source.slice(begin,end).trim();if(previous)assert.equal(method,previous);previous=method;
 const Constructor=vm.runInNewContext('(class Reference {'+method+'})',{});const instance=new Constructor();
 const groupId=key('3'),author=key('4'),admin=key('1'),inviter=key('2'),time=1700000123456;let signed,propagated=0,relayed=0;
 const group={id:groupId,admin,members:[admin,inviter,author]};
 instance.app={wallet:{createUnsignedTransactionWithDefaultFee:async to=>{assert.equal(to,groupId);return {recipients:[to,author],addTo(k){assert(k);this.recipients.push(k);},async sign(){signed=signedReference('4',this.msg,time,this.recipients,true);}};}},network:{propagateTransaction:async tx=>{assert.equal(tx.msg.request,'chat remove');propagated++;}},connection:{emit:(event,message)=>{assert.equal(event,'relay-send-message');assert.equal(message.request,'chat relay');assert.deepEqual(message.recipient,group.members);relayed++;}}};
 // Source reads group.admin although normal creation stores role maps; fixture supplies the derived known admin.
 const txHolder=instance.app.wallet.createUnsignedTransactionWithDefaultFee;
 instance.app.wallet.createUnsignedTransactionWithDefaultFee=async to=>{const tx=await txHolder(to);tx.toJson=()=>({msg:tx.msg});return tx;};
 await instance.sendRemoveMemberTransaction(group,author);assert.equal(propagated,1);assert.equal(relayed,1);
 vectors.push({pin,sourceSha256:expected[file],groupId,author,admin,inviter,timestamp:time,envelope:signed.tx,signature:signed.sig});
}
assert.equal(vectors[0].envelope,vectors[1].envelope);console.log(JSON.stringify({reference,scope:'Actual web self-removal constructor with zero-payment recipient-first wallet fixture; WASM/Elliptic signed bytes, not live admission.',vectors},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
