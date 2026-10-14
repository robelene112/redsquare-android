// Read-only independent public unsafe scalar1/2/3/4 reference. Never fund.
const fs=require('node:fs');const assert=require('node:assert/strict');const vm=require('node:vm');const crypto=require('node:crypto');
const wasm=require('/reference/dependencies/node_modules/saito-wasm/pkg/node');
const {reference,signedReference}=require('./signed-reference.cjs');
const key=n=>wasm.generate_public_key('0'.repeat(63)+n);
const base58=require('/reference/dependencies/node_modules/base-58');
const root=__dirname; const vectors=[];const invitations=[];
const cryptoSource=fs.readFileSync(root+'/crypto.ts','utf8');
const cryptoSourceSha256=crypto.createHash('sha256').update(cryptoSource).digest('hex');
assert.equal(cryptoSourceSha256,'61416839726c1877875062273fd704b37002f8f3a5d299e6c0883f5d24685683');
const cs=cryptoSource.indexOf('  stringToBase64('),ce=cryptoSource.indexOf('  base64ToString(',cs);
assert(cs>0&&ce>cs);
const codecMethod=cryptoSource.slice(cs,ce).trim().replace('str: string','str').replace('): string',')');
const Codec=vm.runInNewContext('(class Codec {'+codecMethod+'})',{Buffer});
const codec=new Codec();
async function run(pin,file){
 const source=fs.readFileSync(root+'/'+file,'utf8');
 const expected={'chat-A.js':'35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af','chat-B.js':'28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb'};
 assert.equal(crypto.createHash('sha256').update(source).digest('hex'),expected[file]);
 const begin=source.indexOf('  async sendCreateGroupTransaction(');const end=source.indexOf('  async receiveCreateGroupTransaction(',begin);
 assert(begin>0&&end>begin);const method=source.slice(begin,end).trim();
 const lb=source.indexOf('  generateChatGroupLink('),le=source.indexOf('  webServer(',lb);assert(lb>0&&le>lb);
 const linkMethod=source.slice(lb,le).trim();let copied;
 const value=vm.runInNewContext('(class Reference {'+method+linkMethod+'})',{console:{log(){}},window:{location:{origin:'https://saito.io'}},navigator:{clipboard:{writeText:link=>{copied=link;}}},siteMessage(){}});
 const wallet=key('1'),recipient=key('2'),group=key('3'),time=1700000123456;
 const name='Native group 😀 <&>';let signed,addedKey,broadcasts=0,relays=0;
 const app={crypto:{generateKeys:()=> '0'.repeat(63)+'3',generatePublicKey:pk=>wasm.generate_public_key(pk)},
 keychain:{addKey:(id,metadata)=>{assert.equal(id,group);assert.equal(metadata.privateKey,'0'.repeat(63)+'3');addedKey=true;}},
 wallet:{createUnsignedTransaction:async(to,amount,fee)=>{assert.equal(to,wallet);assert.equal(amount,0n);assert.equal(fee,0n);return {recipients:[to],addTo(k){this.recipients.push(k);},async sign(){signed=signedReference('1',this.msg,time,this.recipients);}};}},
 network:{propagateTransaction:async tx=>{assert(tx.msg.request==='chat group');broadcasts++;}},
 connection:{emit:(name,tx)=>{assert.equal(name,'relay-transaction');assert(tx.msg.request==='chat group');relays++;}}};
 const instance=new value();instance.app=app;instance.publicKey=wallet;
 await instance.sendCreateGroupTransaction(name,[wallet,recipient]);
 instance.app.crypto.stringToBase64=str=>codec.stringToBase64(str);
 const linkGroup={id:group,name,members:[wallet,recipient],member_ids:{[wallet]:'admin',[recipient]:1}};
 for(const invitationName of [name,name+'😀😀😀']){linkGroup.name=invitationName;for(const sender of [wallet,recipient]){instance.publicKey=sender;instance.generateChatGroupLink(linkGroup);assert(copied.startsWith('https://saito.io/chat?chat_id='));invitations.push({pin,link:copied,id:group,name:invitationName,sender,admin:wallet});}} 
 assert(addedKey&&broadcasts===1&&relays===1);assert(signed);
 vectors.push({pin,sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),name,groupId:group,admin:wallet,invitees:[recipient],timestamp:time,envelope:signed.tx,signature:signed.sig});
 return method+linkMethod;
}
(async()=>{const a=await run('9a4ca7e9442eb5e872168ced361ce741d7d139d7','chat-A.js');const b=await run('214504ecece76d1cd20a245fe4468eccbfca2951','chat-B.js');assert.equal(a,b);assert.equal(vectors[0].envelope,vectors[1].envelope);const invalidRecipient=base58.encode(Buffer.alloc(33));const malformed=signedReference('1',{module:'Chat',request:'chat group',name:'Signed malformed recipient',admin:key('1'),id:key('3')},1700000123456,[invalidRecipient]);console.log(JSON.stringify({reference,cryptoSourceSha256,invitations,malformedRecipients:[{invalidRecipient,envelope:malformed.tx,signature:malformed.sig}],purpose:'Pure pinned web constructor and WASM/Elliptic signature vector; no app initialization or live writes. Public unsafe identities only.',vectors},null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
