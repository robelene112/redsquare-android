// Research-only public Registry reads. Never initializes Saito or sends broadcasts.
const fs = require('node:fs');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {performance} = require('node:perf_hooks');
const moduleRoot = '/opt/saito/node/node_modules/';
const wasmBytes = fs.readFileSync(moduleRoot+'saito-wasm/pkg/node/index_bg.wasm');
const wasmSha256 = crypto.createHash('sha256').update(wasmBytes).digest('hex');
assert.equal(wasmSha256,'f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e');
assert.equal(JSON.parse(fs.readFileSync(moduleRoot+'saito-wasm/package.json')).version,'0.3.2');
const WS = require(moduleRoot+'ws');
const wasm = require(moduleRoot+'saito-wasm/pkg/node');
const b58 = require(moduleRoot+'base-58');
const unsafeKey = '0'.repeat(63)+'1'; // Published unsafe fixture only. Never fund.
const publicKey = wasm.generate_public_key(unsafeKey);
assert.equal(publicKey,'jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR');
const point = Buffer.from(b58.decode(publicKey));
const identifiers = ['NativeReadOnlyProbe20261015@saito','Saitoverse@saito'];
const endpoints = ['wss://saito.davidengelen.nl/wsopen','wss://saito.io/wsopen'];
const wait = ms => new Promise(resolve => setTimeout(resolve,ms));
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
function classify(body) {
  const result={bytes:body.length};
  if(body.length>65536)return {...result,classification:'oversize-not-copied'};
  result.sha256=hash(body);
  try {
    const text=new TextDecoder('utf-8',{fatal:true}).decode(body);
    const value=JSON.parse(text);
    if(Array.isArray(value)) {result.classification='array';result.rowCount=value.length;}
    else if(value && typeof value==='object' && !Array.isArray(value) && Object.keys(value).length===1 && value.err==='no response') {
      result.classification='exact-dispatcher-fallback';result.body={err:'no response'};
    } else result.classification=value===null?'null':typeof value;
  } catch {result.classification='invalid-json-or-utf8';}
  return result;
}
async function inspect(endpoint) {
  assert(endpoints.includes(endpoint));
  const record={endpoint,observedAt:new Date().toISOString(),readOnly:true,publicFixtureIdentity:publicKey,requests:[],services:[]};
  const socket=new WS(endpoint,{maxPayload:16*1024*1024});
  const nonce=crypto.randomBytes(32),challenges=[nonce],observations=new Map();
  let peer=null,closed=false,resolveReady,rejectReady,index=0;
  const ready=new Promise((yes,no)=>{resolveReady=yes;rejectReady=no;});ready.catch(()=>{});
  const handshakeTimer=setTimeout(()=>rejectReady(Error('handshake-timeout')),12000);
  function stop(reason){if(!closed){closed=true;record.disconnectedReason=reason;}rejectReady(Error(reason));}
  function send(frame){
    assert([1,2,9,12,16,20].includes(frame[0])); // No broadcast type4, registration or transfer.
    if(closed)throw Error('connection-closed');socket.send(frame);
  }
  function handshakeReply(challenge,counter){return Buffer.concat([Buffer.from([2]),point,Buffer.from(wasm.sign_buffer(challenge,unsafeKey),'hex'),counter]);}
  socket.on('open',()=>{try{send(Buffer.concat([Buffer.from([1]),nonce]));send(Buffer.from([20]));}catch{stop('open-failed');}});
  socket.on('error',()=>stop('socket-error'));
  socket.on('close',()=>stop('socket-closed'));
  socket.on('message',data=>{try{
    const frame=Buffer.from(data);assert(frame.length>=1 && frame.length<=16*1024*1024);
    const kind=frame[0];
    if(kind===1){assert.equal(frame.length,33);assert(challenges.length<8);const counter=crypto.randomBytes(32);challenges.push(counter);send(handshakeReply(frame.subarray(1),counter));}
    else if(kind===2){
      assert.equal(frame.length,130);const identity=b58.encode(frame.subarray(1,34));
      const matched=challenges.findIndex(value=>wasm.verify_signature(value,frame.subarray(34,98).toString('hex'),identity));
      assert(matched>=0 && (!peer||peer===identity));challenges.splice(matched,1);peer=identity;
      if(frame.subarray(98).some(value=>value))send(handshakeReply(frame.subarray(98),Buffer.alloc(32)));
      resolveReady();
    } else if(kind===7)send(Buffer.from([16]));
    else if(kind===20)send(Buffer.from([9]));
    else if(kind===9 && frame.length<=65537){
      record.services=[...new Set(new TextDecoder('utf-8',{fatal:true}).decode(frame.subarray(1)).split(';').filter(Boolean).map(value=>value.split('|')[0]).filter(value=>value.length<=128))].slice(0,256);
    } else if((kind===13||kind===14)&&peer){
      assert(frame.length>=5);const id=frame.readUInt32BE(1),observation=observations.get(id);
      if(!observation)return;
      const elapsedMs=performance.now()-observation.started;
      if(elapsedMs>=12000){observation.afterDeadline=Math.min(observation.afterDeadline+1,1000);return;}
      if(observation.replies.length>=4){observation.dropped=Math.min(observation.dropped+1,1000);return;}
      observation.replies.push({ordinal:observation.replies.length+1,type:kind,elapsedMs:Number(elapsedMs.toFixed(3)),...classify(frame.subarray(5))});
    }
  }catch{stop('invalid-frame');socket.terminate();}});
  try {
    await ready;clearTimeout(handshakeTimer);record.authenticatedPeer=peer;
    for(const identifier of identifiers){
      const id=++index,started=performance.now();
      const body={request:'registry query',data:{request:'registry namecheck',identifier}};
      assert(Object.keys(body).length===2 && identifiers.includes(identifier));
      const message=Buffer.from(JSON.stringify(body)),frame=Buffer.alloc(5+93+4*59+message.length);
      frame[0]=12;frame.writeUInt32BE(id,1);frame.writeUInt32BE(2,5);frame.writeUInt32BE(2,9);frame.writeUInt32BE(message.length,13);
      frame.writeBigUInt64BE(BigInt(Date.now()),85);frame.writeUInt32BE(1,93);
      for(let n=0;n<4;n++)point.copy(frame,98+59*n);message.copy(frame,98+4*59);
      const observation={id,body,started,replies:[],dropped:0,afterDeadline:0};
      observations.set(id,observation);send(frame);
    }
    while(!closed){const remaining=Math.max(...[...observations.values()].map(value=>value.started+12000-performance.now()));if(remaining<=0)break;await wait(Math.min(remaining,100));}
    for(const value of observations.values())record.requests.push({index:value.id,body:value.body,windowMs:12000,observedMs:Number(Math.min(performance.now()-value.started,12000).toFixed(3)),windowCompleted:!closed,replies:value.replies,dropped:value.dropped,afterDeadline:value.afterDeadline});
    record.observationCompleted=!closed;
  } catch {record.observationCompleted=false;record.failure='handshake-or-observation-failed';}
  finally {clearTimeout(handshakeTimer);socket.terminate();}
  return record;
}
(async()=>{const results=await Promise.all(endpoints.map(inspect));console.log(JSON.stringify({probe:'public-registry-first-late-host-observation',nodeVersion:process.version,wasmSha256,deadlineMs:12000,productionStateModified:false,nativeAppExecution:false,results},null,2));})();
