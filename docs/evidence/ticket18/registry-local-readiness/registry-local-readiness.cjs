// Read-only authenticated public-peer requests. Never sends broadcast frame4.
const fs=require('node:fs'),crypto=require('node:crypto');
const WS=require('/opt/saito/node/node_modules/ws');
const wasm=require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const b58=require('/opt/saito/node/node_modules/base-58');
const key='0'.repeat(63)+'1',publicKey=wasm.generate_public_key(key),point=Buffer.from(b58.decode(publicKey));
const registrar='sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK';
async function inspect(endpoint){
 const socket=new WS(endpoint),nonce=crypto.randomBytes(32),challenges=[nonce],pending=new Map();let peer=null,index=0;
 let resolveReady,rejectReady;const ready=new Promise((yes,no)=>{resolveReady=yes;rejectReady=no});ready.catch(()=>{});
 const timer=setTimeout(()=>rejectReady(Error('handshake timeout')),12000);
 const reply=(n,c)=>Buffer.concat([Buffer.from([2]),point,Buffer.from(wasm.sign_buffer(n,key),'hex'),c]);
 socket.on('open',()=>{socket.send(Buffer.concat([Buffer.from([1]),nonce]));socket.send(Buffer.from([20]));});
 socket.on('error',error=>{rejectReady(error);for(const r of pending.values())r.reject(error);});
 socket.on('message',data=>{try{
  const f=Buffer.from(data);if(f.length>16*1024*1024)throw Error('oversize');
  if(f[0]===1){if(f.length!==33)throw Error('bad challenge');const c=crypto.randomBytes(32);challenges.push(c);socket.send(reply(f.subarray(1),c));}
  if(f[0]===2){if(f.length!==130)throw Error('bad handshake');const p=b58.encode(f.subarray(1,34)),sig=f.subarray(34,98).toString('hex');const n=challenges.findIndex(c=>wasm.verify_signature(c,sig,p));if(n<0||peer&&peer!==p)throw Error('unauthenticated');challenges.splice(n,1);peer=p;if(f.subarray(98).some(v=>v))socket.send(reply(f.subarray(98),Buffer.alloc(32)));resolveReady();}
  if(f[0]===7)socket.send(Buffer.from([16]));if(f[0]===20)socket.send(Buffer.from([9]));
  if([13,14].includes(f[0])&&peer){const id=f.readUInt32BE(1),r=pending.get(id);if(!r)return;pending.delete(id);clearTimeout(r.timer);f[0]===14?r.reject(Error('peer request rejected')):r.resolve(JSON.parse(f.subarray(5).toString()));}
 }catch(error){rejectReady(error);for(const r of pending.values())r.reject(error);socket.terminate();}});
 const result={endpoint,observedAt:new Date().toISOString(),readOnly:true};
 async function request(body){const id=++index,message=Buffer.from(JSON.stringify(body)),frame=Buffer.alloc(5+93+4*59+message.length);frame[0]=12;frame.writeUInt32BE(id,1);frame.writeUInt32BE(2,5);frame.writeUInt32BE(2,9);frame.writeUInt32BE(message.length,13);frame.writeBigUInt64BE(BigInt(Date.now()),85);frame.writeUInt32BE(1,93);for(let n=0;n<4;n++)point.copy(frame,98+59*n);message.copy(frame,98+4*59);return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(Error('lookup timeout'));},12000);pending.set(id,{resolve,reject,timer});socket.send(frame);});}
 try{
  await ready;result.authenticatedPeer=peer;
  try{result.namecheck=await request({request:'registry query',data:{request:'registry namecheck',identifier:'NativeReadOnlyProbe20261011@saito'}});}catch(error){result.namecheckError=error.message;}
  try{
   const feed=await request({request:'archive',data:{request:'load',field1:'RedSquare',field4:'',flagged_ne:1,limit:5,created_earlier_than:Date.now()}});result.archiveRows=Array.isArray(feed)?feed.length:null;const keys=[...new Set([registrar,publicKey,...(Array.isArray(feed)?feed.map(r=>r.field2).filter(k=>typeof k==='string'):[])])];result.keyMap=await request({request:'registry query',data:{request:'registry query',keys}});
   const name=Object.values(result.keyMap).find(n=>typeof n==='string'&&/^[A-Za-z0-9]+@saito$/.test(n));
   if(name){
    result.knownName=name;
    result.localRecord=await request({request:'rawSQL',data:{module:'Registry',dbname:'registry',sql:`SELECT identifier, publickey, bid, bsh, sig, signer, lc FROM records WHERE identifier = "${name}" LIMIT 1`}});
    result.localValidProofs=Array.isArray(result.localRecord?.rows)?result.localRecord.rows.filter(r=>r.identifier===name&&r.signer===registrar&&wasm.verify_signature(Buffer.from(r.identifier+r.publickey+String(r.bid)+r.bsh),r.sig,registrar)).length:0;
    result.proofRows=await request({request:'registry query',data:{request:'registry namecheck',identifier:name}});
    result.validProofs=Array.isArray(result.proofRows)?result.proofRows.filter(r=>r.signer===registrar&&wasm.verify_signature(Buffer.from(r.identifier+r.publickey+String(r.bid)+r.bsh),r.sig,registrar)).length:0;
   }
  }catch(error){result.keyMapError=error.message;}
 }catch(error){result.connectionError=error.message;}finally{clearTimeout(timer);for(const r of pending.values())clearTimeout(r.timer);socket.terminate();}
 return result;
}
(async()=>{const result=await Promise.all(['wss://saito.davidengelen.nl/wsopen','wss://saito.io/wsopen'].map(inspect));console.log(JSON.stringify(result,null,2));})();
