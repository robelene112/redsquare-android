// Public unsafe fixtures only; actual pinned constructors, no app initialization.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const wasm=require('/reference/dependencies/node_modules/saito-wasm/pkg/node');
const {reference,signedReference}=require('./signed-reference.cjs');
const key=n=>wasm.generate_public_key('0'.repeat(63)+n);
const sources=[['9a4ca7e9442eb5e872168ced361ce741d7d139d7','chat-A.js','35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af'],['214504ecece76d1cd20a245fe4468eccbfca2951','chat-B.js','28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb']];
async function run(operation){
 const definition=operation==='promote' ? {send:'sendUpdateGroupTransaction',receive:'receiveUpdateGroupTransaction',request:'chat update'} : {send:'sendRemoveMemberTransaction',receive:'receiveRemoveMemberTransaction',request:'chat remove'};
 const vectors=[];let previous;
 for(const [pin,file,sourceSha256] of sources){
  const source=fs.readFileSync(__dirname+'/'+file,'utf8');assert.equal(crypto.createHash('sha256').update(source).digest('hex'),sourceSha256);
  const start=source.indexOf('  async '+definition.send+'('),end=source.indexOf('  async '+definition.receive+'(',start);assert(start>0&&end>start);
  const method=source.slice(start,end).trim();if(previous)assert.equal(method,previous);previous=method;
  const Constructor=vm.runInNewContext('(class Reference {'+method+'})',{}),instance=new Constructor();
  const groupId=key('3'),admin=key('1'),member=key('2'),timestamp=1700000123457;
  const group={id:groupId,name:'Native group 😀 <&>',members:[admin,member],member_ids:{[admin]:'admin',[member]:operation==='promote'?'admin':1}};
  let signed,propagated=0,relayed=0;
  instance.app={wallet:{createUnsignedTransactionWithDefaultFee:async recipient=>{
   assert.equal(recipient,groupId);
   return {recipients:[recipient,admin],async sign(){signed=signedReference('1',this.msg,timestamp,this.recipients,true);},toJson(){return {msg:this.msg};}};
  }},network:{propagateTransaction:async tx=>{assert.equal(tx.msg.request,definition.request);assert.equal(tx.msg.chat_history,undefined);propagated++;}},connection:{emit:(event,message)=>{
   assert.equal(event,'relay-send-message');assert.equal(message.request,'chat relay');assert.deepEqual(message.recipient,[admin,member]);relayed++;
  }}};
  await instance[definition.send](group,operation==='remove'?member:undefined);assert.equal(propagated,1);assert.equal(relayed,1);
  const receiverEnd=source.indexOf(operation==='promote'?'  async sendRemoveMemberTransaction(':'  async createChatTransaction(',end);assert(receiverEnd>end);
  const Receiver=vm.runInNewContext('(class Receiver {'+source.slice(end,receiverEnd).trim()+'})',{console:{log(){},warn(){}}});
  const receiver=new Receiver();const observed={id:groupId,name:group.name,members:[admin,member],member_ids:{[admin]:'admin',[member]:1}};
  receiver.publicKey=admin;receiver.returnGroup=id=>{assert.equal(id,groupId);return observed;};
  receiver.addTransactionToGroup=()=>{};
  receiver.app={browser:{escapeHTML:value=>value},keychain:{returnUsername:value=>value},connection:{emit(){}}};
  const body=JSON.parse(Buffer.from(JSON.parse(signed.tx).m,'base64').toString());
  const tx={msg:body,from:[{publicKey:admin}],returnMessage:()=>body,isTo:value=>value===groupId,isFrom:value=>value===admin};
  await receiver[definition.receive](tx);
  assert.equal(observed.member_ids[member],operation==='promote'?'admin':-1);
  assert.equal(observed.members.includes(member),operation==='promote');
  vectors.push({pin,sourceSha256,groupId,admin,member,timestamp,envelope:signed.tx,signature:signed.sig,receiverRole:observed.member_ids[member]});
 }
 assert.equal(vectors[0].envelope,vectors[1].envelope);
 return vectors;
}
(async()=>console.log(JSON.stringify({reference,scope:'Actual pinned admin constructors and corresponding receive methods over public fixture roles; zero-payment group-first wallet, WASM/Elliptic signing. Display/storage/dispatch are adapters. Not live admission or a complete web session.',vectors:await run('promote'),removalVectors:await run('remove')},null,2)))().catch(error=>{console.error(error);process.exitCode=1;});
