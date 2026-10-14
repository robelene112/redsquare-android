// Public unsafe fixtures only. Executes pinned constructors without Saito initialization.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const wasm=require('/reference/dependencies/node_modules/saito-wasm/pkg/node');
const {reference,signedReference}=require('./signed-reference.cjs');
const key=n=>wasm.generate_public_key('0'.repeat(63)+n),vectors=[];
const expected={'chat-A.js':'35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af','chat-B.js':'28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb'};
(async()=>{let previous;
for(const [pin,file] of [['9a4ca7e9442eb5e872168ced361ce741d7d139d7','chat-A.js'],['214504ecece76d1cd20a245fe4468eccbfca2951','chat-B.js']]){
 const source=fs.readFileSync(__dirname+'/'+file,'utf8');assert.equal(crypto.createHash('sha256').update(source).digest('hex'),expected[file]);
 const start=source.indexOf('  async createChatTransaction('),end=source.indexOf('  /**',start);assert(start>0&&end>start);
 const method=source.slice(start,end).trim();// B adds contact discovery only inside the encrypted-DM branch, never executed for named groups.
 const comparison=method.replace("      if (secret_holder) {\n        this.app.keychain.addKey(secret_holder);\n      }\n\n",'');
 if(previous)assert.equal(comparison,previous);previous=comparison;
 const time=1700000123456;class FixedDate extends Date{constructor(){super(time);}}
 const Constructor=vm.runInNewContext('(class Reference {'+method+'})',{Date:FixedDate});const instance=new Constructor();
 const author=key('1'),member=key('2'),id=key('3'),name='Native group 😀 <&>',text='Hello group 😀 <&>!',mentioned=[member];let signed,propagated=0,relayed=0;
 const group={id,name,members:[author,member],member_ids:{[author]:'admin',[member]:1}};
 instance.publicKey=author;instance.returnGroup=()=>group;instance.returnMembers=()=>group.members;
 instance.app={wallet:{getBalance:async()=>0n,createUnsignedTransaction:async(to,amount,fee)=>{assert.equal(to,author);assert.equal(amount,0n);assert.equal(fee,0n);return {recipients:[to],addTo(k){this.recipients.push(k);},async sign(){signed=signedReference('1',this.msg,time,this.recipients);},toJson(){return {msg:this.msg};}};}},network:{propagateTransaction:async tx=>{assert.equal(tx.msg.request,'chat message');propagated++;}},connection:{emit:(event,data)=>{assert.equal(event,'relay-send-message');assert.equal(data.request,'chat relay');assert.deepEqual(data.recipient,group.members);relayed++;}}};
 await instance.createChatTransaction(id,text,mentioned);assert.equal(propagated,1);assert.equal(relayed,1);
 vectors.push({pin,sourceSha256:expected[file],groupId:id,author,name,text,mentioned,timestamp:time,envelope:signed.tx,signature:signed.sig});
}
assert.equal(vectors[0].envelope,vectors[1].envelope);console.log(JSON.stringify({reference,scope:'Actual named-group plaintext constructor with zero-payment wallet boundary; not live admission or encrypted private Chat.',vectors},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
