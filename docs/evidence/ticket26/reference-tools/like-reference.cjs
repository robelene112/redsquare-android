// Public unsafe fixtures only. Actual pinned named-group constructors/receivers, no app initialization.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const wasm=require('/reference/dependencies/node_modules/saito-wasm/pkg/node');
const {reference,signedReference}=require('./signed-reference.cjs');
const key=n=>wasm.generate_public_key('0'.repeat(63)+n),vectors=[];
const expected={'chat-A.js':'35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af','chat-B.js':'28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb'};
const original=JSON.parse(fs.readFileSync('/reference/message-reference.json')).vectors[0];
(async()=>{
 for(const file of Object.keys(expected)){
  const source=fs.readFileSync(__dirname+'/'+file,'utf8');
  assert.equal(crypto.createHash('sha256').update(source).digest('hex'),expected[file]);
  const start=source.indexOf('  async createChatLikeTransaction('),end=source.indexOf('  /**',start);
  assert(start>0&&end>start);
  const rstart=source.indexOf('  async receiveChatLikeTransaction('),rend=source.indexOf('  ///////////////////',rstart);
  assert(rstart>0&&rend>rstart);
  const Constructor=vm.runInNewContext('(class Reference {'+source.slice(start,end)+source.slice(rstart,rend)+'})',{console:{log(){},warn(){},error(){}}});
  const instance=new Constructor(),author=key('1'),reactor=key('2'),id=key('3');
  const target={signature:original.signature,likes:0,liked_by:{}};
  const group={id,members:[author,reactor],member_ids:{[author]:'admin',[reactor]:1},txs:[target]};
  instance.publicKey=reactor;instance.returnGroup=requested=>requested===id?group:null;
  for(const [index,timestamp] of [1700000123500,1700000123501].entries()){
   let signed,propagated=0,relayed=0,rendered=0;
   instance.app={wallet:{createUnsignedTransactionWithDefaultFee:async()=>({recipients:[reactor],addTo(k){this.recipients.push(k)},async sign(){signed=signedReference('2',this.msg,timestamp,this.recipients)},toJson(){return {msg:this.msg}}})},network:{propagateTransaction:async tx=>{assert.equal(tx.msg.request,'chat like');propagated++}},connection:{emit:(event,data)=>{if(event==='relay-send-message'){assert.deepEqual(data.recipient,group.members);assert.equal(data.request,'chat relay');relayed++}else{assert.equal(event,'chat-popup-render-request');assert.equal(data,group);rendered++}}}};
   const transaction=await instance.createChatLikeTransaction(group,original.signature,author);
   assert.deepEqual(JSON.parse(JSON.stringify(transaction.msg)),{module:'Chat',request:'chat like',group_id:id,sender:reactor,signature:original.signature,mentioned:author});
   assert.deepEqual(transaction.recipients,[reactor,author,id]);
   assert.equal(propagated,1);assert.equal(relayed,1);
   await instance.receiveChatLikeTransaction({returnMessage:()=>transaction.msg});
   assert.equal(target.likes,index===0?1:0);assert.equal(target.liked_by[reactor],index===0);assert.equal(rendered,1);
   vectors.push({source:file,sourceSha256:expected[file],timestamp,groupId:id,target:original.signature,targetAuthor:author,reactor,envelope:signed.tx,signature:signed.sig,likes:index===0?1:0});
  }
 }
 assert.equal(vectors[0].envelope,vectors[2].envelope);assert.equal(vectors[1].envelope,vectors[3].envelope);
 console.log(JSON.stringify({reference,boundary:'Actual pinned named-group like constructor and receiver; deterministic default-fee wallet/network adapters; independent WASM/elliptic signing, no DOM/app initialization/live delivery or dispatch deduplication.',vectors},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
