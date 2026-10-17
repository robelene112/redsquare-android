// Public unsafe fixtures only. Executes pinned constructors without Saito initialization.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const wasm=require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const {reference,signedReference}=require('./signed-reference.cjs');
const gifMode=process.argv.includes('--check-native-gif-send');
const nativeMode=gifMode||process.argv.includes('--check-native-photo-send');
const nativeInput=nativeMode?fs.readFileSync(gifMode?'/reference/native-group-gif-envelope.json':'/reference/native-group-photo-envelope.json'):null;
const nativeEnvelope=nativeInput?JSON.parse(nativeInput):null;
const nativeBody=nativeEnvelope?JSON.parse(Buffer.from(nativeEnvelope.m,'base64')):null;
const nativeTime=nativeEnvelope?Number(Buffer.from(nativeEnvelope.t,'base64').readBigUInt64BE(80)):null;
const caption=gifMode?'Keep my Chat draft 😀 <&> "quoted"\nA second line':'Photo caption 😀 <&> "quoted"\nA second line';
const gifSource=gifMode?fs.readFileSync('/reference/native-gif-source.txt','utf8').trim():null;
if(nativeMode){
 assert(Number.isSafeInteger(nativeTime)&&nativeTime>=0);
 if(gifMode){
  const url=new URL(gifSource);assert.equal(url.protocol,'http:');assert.equal(url.hostname,'127.0.0.1');
  assert.equal(url.pathname,'/media/giphy.gif');assert.equal(url.search,'?token=owned%2Bonly&keep=1');
  assert.equal(nativeBody.message,`<img class="img-prev" src="${gifSource.replace(/&/g,'&amp;')}">\n<div>Keep my Chat draft 😀 &lt;&amp;&gt; &quot;quoted&quot;\nA second line</div>`);
 }else{ assert(/^<img class="img-prev" src="data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}">\n<div>Photo caption 😀 &lt;&amp;&gt; &quot;quoted&quot;\nA second line<\/div>$/.test(nativeBody.message));
 }

}
const imageMode=process.argv.includes('--image');
const image=imageMode?JSON.parse(fs.readFileSync('/reference/images-reference.json','utf8')).images.red:null;
const key=n=>wasm.generate_public_key('0'.repeat(63)+n),vectors=[],delayedVectors=[];
const expected={'chat-A.js':'35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af','chat-B.js':'28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb'};
(async()=>{let previous;
for(const [pin,file] of [['9a4ca7e9442eb5e872168ced361ce741d7d139d7','chat-A.js'],['214504ecece76d1cd20a245fe4468eccbfca2951','chat-B.js']]){
 const source=fs.readFileSync('/reference/'+file,'utf8');assert.equal(crypto.createHash('sha256').update(source).digest('hex'),expected[file]);
 const start=source.indexOf('  async createChatTransaction('),end=source.indexOf('  /**',start);assert(start>0&&end>start);
 const method=source.slice(start,end).trim();// B adds contact discovery only inside the encrypted-DM branch, never executed for named groups.
 const comparison=method.replace("      if (secret_holder) {\n        this.app.keychain.addKey(secret_holder);\n      }\n\n",'');
 if(previous)assert.equal(comparison,previous);previous=comparison;
 const time=nativeMode?nativeTime:1700000123456;let bodyTime=time;class FixedDate extends Date{constructor(){super(bodyTime);}}
 const Constructor=vm.runInNewContext('(class Reference {'+method+'})',{Date:FixedDate});const instance=new Constructor();
 const scalar=nativeMode?'2':'1';
 const author=key(scalar),member=key(nativeMode?'1':'2'),id=key('3'),name=nativeMode?(gifMode?'Compatible GIF group':'Compatible photo group'):'Native group 😀 <&>',text=nativeMode?nativeBody.message:imageMode?`<img class="img-prev" src="${image}">`:'Hello group 😀 <&>!',mentioned=nativeMode?[]:[member];let signed,propagated=0,relayed=0;
 if(nativeMode)assert.deepEqual(nativeBody,{module:'Chat',request:'chat message',group_id:id,message:text,timestamp:time,mentioned:[],group_name:name});
 const group={id,name,members:nativeMode?[member,author]:[author,member],member_ids:nativeMode?{[member]:'admin',[author]:1}:{[author]:'admin',[member]:1}};
 instance.publicKey=author;instance.returnGroup=()=>group;instance.returnMembers=()=>group.members;
 instance.app={wallet:{getBalance:async()=>0n,createUnsignedTransaction:async(to,amount,fee)=>{assert.equal(to,author);assert.equal(amount,0n);assert.equal(fee,0n);return {recipients:[to],addTo(k){this.recipients.push(k);},async sign(){signed=signedReference(scalar,this.msg,time,this.recipients);},toJson(){return {msg:this.msg};}};}},network:{propagateTransaction:async tx=>{assert.equal(tx.msg.request,'chat message');propagated++;}},connection:{emit:(event,data)=>{assert.equal(event,'relay-send-message');assert.equal(data.request,'chat relay');assert.deepEqual(data.recipient,group.members);relayed++;}}};
 await instance.createChatTransaction(id,text,mentioned);assert.equal(propagated,1);assert.equal(relayed,1);
 vectors.push({pin,sourceSha256:expected[file],groupId:id,author,name,text,mentioned,timestamp:time,envelope:signed.tx,signature:signed.sig});
 if(nativeMode){assert.equal(signed.tx,JSON.stringify(nativeEnvelope));continue;}
 bodyTime=time+3;await instance.createChatTransaction(id,text,mentioned);
 assert.equal(propagated,2);assert.equal(relayed,2);
 delayedVectors.push({pin,groupId:id,author,text,transactionTimestamp:time,bodyTimestamp:bodyTime,envelope:signed.tx,signature:signed.sig});
}
assert.equal(vectors[0].envelope,vectors[1].envelope);console.log(JSON.stringify({reference,scope:'Actual named-group plaintext constructor with zero-payment wallet boundary; not live admission or encrypted private Chat.',...(nativeMode?{nativeEnvelopeSha256:crypto.createHash('sha256').update(nativeInput).digest('hex'),independentWasmSignatureMatches:true,actualPinnedConstructorsMatchNativeEnvelope:true,...(gifMode?{nativeGif:{message:nativeBody.message,caption,source:gifSource},exactOriginalGifUrl:true}:{nativePhoto:{message:nativeBody.message,caption}})}:{}),...(imageMode?{imageFormat:'Complete img.img-prev outerHTML from inspected popup callbackOnUpload',sourceRGB:[220,30,40],unsafeFixtures:'Public fixed scalars only. Never fund.'}:{}),vectors,delayedVectors},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
