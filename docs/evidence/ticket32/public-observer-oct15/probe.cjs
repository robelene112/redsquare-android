'use strict';
// Research only: public reads, fresh throwaway identity, no Saito initialization or broadcasts.
const fs=require('node:fs'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const wasmFile='/deps/saito-wasm/pkg/node/index_bg.wasm';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
assert.equal(sha(fs.readFileSync(wasmFile)),'f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e');
const wasm=require('/deps/saito-wasm/pkg/node'),b58=require('/deps/base-58'),WS=require('/deps/ws');
const endpoints=[['owner','https://saito.davidengelen.nl'],['official','https://saito.io']];
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function probe([backend,origin]) {
  const record={backend,origin,observedAt:new Date().toISOString(),readOnly:true,
    appExecution:false,transactionBroadcasts:0,services:[]};
  let secret,publicKey;
  do {const key=crypto.createECDH('secp256k1');key.generateKeys();secret=key.getPrivateKey().toString('hex').padStart(64,'0');publicKey=wasm.generate_public_key(secret);}while(publicKey.length!==44);
  const point=Buffer.from(b58.decode(publicKey)),nonces=[crypto.randomBytes(32)];
  const socket=new WS(origin.replace('https:','wss:')+'/wsopen',{maxPayload:2*1024*1024});
  let peer=null,closed=false,protocolError=false,chainReply=null,archiveReply=null;
  const sent=[];
  function send(frame){assert([1,2,9,12,16,20,21].includes(frame[0]));assert(!closed);sent.push(frame[0]);socket.send(frame);}
  const answer=(nonce,counter)=>Buffer.concat([Buffer.from([2]),point,Buffer.from(wasm.sign_buffer(nonce,secret),'hex'),counter]);
  socket.on('open',()=>{send(Buffer.concat([Buffer.from([1]),nonces[0]]));send(Buffer.from([20]));});
  socket.on('error',()=>{closed=true;});socket.on('close',()=>{closed=true;});
  socket.on('message',data=>{try {
    const frame=Buffer.from(data);assert(frame.length>0&&frame.length<=2*1024*1024);
    if(frame[0]===1){assert.equal(frame.length,33);assert(nonces.length<8);const n=crypto.randomBytes(32);nonces.push(n);send(answer(frame.subarray(1),n));}
    else if(frame[0]===2){assert.equal(frame.length,130);const key=b58.encode(frame.subarray(1,34));
      const index=nonces.findIndex(n=>wasm.verify_signature(n,frame.subarray(34,98).toString('hex'),key));assert(index>=0&&(!peer||key===peer));
      nonces.splice(index,1);peer=key;if(frame.subarray(98).some(b=>b))send(answer(frame.subarray(98),Buffer.alloc(32)));}
    else if(frame[0]===7)send(Buffer.from([16]));
    else if(frame[0]===20)send(Buffer.from([9]));
    else if(frame[0]===9){assert(frame.length<=65537);record.services=[...new Set(new TextDecoder('utf8',{fatal:true}).decode(frame.subarray(1)).split(';').filter(Boolean).map(s=>s.split('|')[0]))].filter(s=>s.length<=128).slice(0,256);}
    else if(frame[0]===22&&peer){assert(chainReply===null);chainReply=frame.subarray(1);}
    else if((frame[0]===13||frame[0]===14)&&peer&&frame.length>=5&&frame.readUInt32BE(1)===1){
      assert(archiveReply===null);archiveReply={type:frame[0],body:frame.subarray(5)};}
  }catch{protocolError=true;socket.terminate();}});
  async function until(predicate){const end=Date.now()+12000;while(!predicate()&&!closed&&!protocolError&&Date.now()<end)await delay(30);assert(!protocolError,'protocol-validation');assert(predicate(),'read-timeout-or-disconnect');}
  try {
    await until(()=>peer!==null);record.authenticatedPeer=peer;
    const request=Buffer.alloc(111);request[0]=21;request[73]=1;point.copy(request,74);send(request);
    await until(()=>chainReply!==null);
    const body=chainReply;assert(body.length>=196);const count=body.readUInt32BE(192);assert(count<=128&&body.length===196+count*53);
    const refs=[];for(let i=0;i<count;i++){const at=196+i*53;assert(body[at+52]<=1);refs.push({height:body.readBigUInt64BE(at).toString(),hash:body.subarray(at+8,at+40).toString('hex'),timestamp:body.readBigUInt64BE(at+40).toString(),transactionsField:body.readUInt32BE(at+48)});}
    for(let i=1;i<refs.length;i++)assert(BigInt(refs[i].height)===BigInt(refs[i-1].height)+1n);
    record.chain={tipHeight:body.readBigUInt64BE(0).toString(),tipHash:body.subarray(8,40).toString('hex'),
      forkId:body.subarray(40,72).toString('hex'),sharedAncestorHeight:body.readBigUInt64BE(72).toString(),
      referenceCount:count,references:refs,wireBytes:body.length,wireSha256:sha(body),bootstrap:'SPV recent window, relevance fields not used to skip full-block reads'};
    assert(refs.length>0,'no-full-block-reference');
    if(refs.length){
      const ref=refs.at(-1);assert(/^[a-f0-9]{64}$/.test(ref.hash));
      const response=await fetch(origin+'/block/'+ref.hash,{redirect:'error',signal:AbortSignal.timeout(20000)});
      assert(response.status===200,'full-block-http');assert(response.body);const chunks=[];let size=0;
      for await(const chunk of response.body){size+=chunk.length;assert(size<=8*1024*1024,'full-block-size');chunks.push(chunk);}
      const bytes=Buffer.concat(chunks);assert(bytes.length>=389);const height=bytes.readBigUInt64BE(4).toString();assert(height===ref.height);
      const preimage=Buffer.concat([bytes.subarray(4,117),bytes.subarray(181,213),bytes.subarray(221,285)]);
      assert(wasm.verify_signature(preimage,bytes.subarray(117,181).toString('hex'),b58.encode(bytes.subarray(52,85))),'full-block-creator');
      const computed=wasm.hash(Buffer.concat([bytes.subarray(20,52),Buffer.from(wasm.hash(preimage),'hex')]));assert.equal(computed,ref.hash);
      const txCount=bytes.readUInt32BE(0);assert(txCount<=Math.floor((bytes.length-389)/93));let offset=389;const leaves=[];
      for(let i=0;i<txCount;i++){
        assert(offset+93<=bytes.length);const inputs=bytes.readUInt32BE(offset),outputs=bytes.readUInt32BE(offset+4),length=bytes.readUInt32BE(offset+8),hops=bytes.readUInt32BE(offset+12);
        const type=bytes[offset+92],replacements=bytes.readUInt32BE(offset+88);assert(inputs<=255&&outputs<=255&&type<=8&&type!==5);
        const start=offset+93+59*(inputs+outputs),end=start+length+130*hops;assert(end<=bytes.length);
        const slips=[];for(let n=0;n<inputs+outputs;n++){const at=offset+93+59*n;assert(bytes[at+58]<=10);slips.push(bytes.subarray(at,at+41),bytes.subarray(at+57,at+59));}
        const typeBytes=Buffer.alloc(4);typeBytes.writeUInt32BE(type);
        const leaf=Buffer.from(wasm.hash(Buffer.concat([bytes.subarray(offset+80,offset+88),...slips,bytes.subarray(offset+88,offset+92),typeBytes,bytes.subarray(start,start+length)])),'hex');
        const repeats=Math.max(1,replacements);assert(leaves.length+repeats<=65536);for(let n=0;n<repeats;n++)leaves.push(leaf);offset=end;
      }
      assert(offset===bytes.length);let level=leaves;while(level.length>1){const next=[];for(let i=0;i<level.length;i+=2)next.push(i+1<level.length?Buffer.from(wasm.hash(Buffer.concat([level[i],level[i+1]])),'hex'):level[i]);level=next;}
      assert((level[0]||Buffer.alloc(32)).equals(bytes.subarray(85,117)),'full-block-merkle');
      record.fullBlock={httpStatus:response.status,bytes:size,sha256:sha(bytes),height,hash:computed,transactions:txCount,
        creatorSignatureValid:true,merkleRootMatches:true,allTransactionsNonSpv:true,exactConsumption:true,consensusAdmissionProved:false};
    }
    // A known absent signature checks route/schema acceptance only, not indexed event availability.
    const message=Buffer.from(JSON.stringify({request:'archive',data:{request:'load',field1:'RedSquare',sig:'0'.repeat(128),flagged_ne:1,limit:1}}));
    const frame=Buffer.alloc(5+93+4*59+message.length);frame[0]=12;frame.writeUInt32BE(1,1);frame.writeUInt32BE(2,5);frame.writeUInt32BE(2,9);frame.writeUInt32BE(message.length,13);frame.writeBigUInt64BE(BigInt(Date.now()),85);frame.writeUInt32BE(1,93);
    for(let i=0;i<4;i++)point.copy(frame,98+59*i);message.copy(frame,334);send(frame);
    await until(()=>archiveReply!==null);assert(archiveReply.type===13&&archiveReply.body.length<=65536);const rows=JSON.parse(new TextDecoder('utf8',{fatal:true}).decode(archiveReply.body));assert(Array.isArray(rows)&&rows.length===0);
    record.exactArchiveAbsentProbe={accepted:true,rows:0,bytes:archiveReply.body.length,indexedCandidateAvailabilityProved:false};
    record.status='read-capabilities-observed';
  }catch(error){record.status='incomplete';record.failureStage=record.fullBlock?'archive':record.chain?'full-block':'handshake-or-chain';record.failureClass=error?.name==='AssertionError'?'validation-or-timeout':error?.name==='TimeoutError'?'network-timeout':'read-error';}
  finally{socket.terminate();secret=undefined;record.sentMessageTypes=[...new Set(sent)].sort((a,b)=>a-b);}
  return record;
}
(async()=>{const results=await Promise.all(endpoints.map(probe));process.stdout.write(JSON.stringify({probe:'public-social-observer-capability',nodeVersion:process.version,productionStateModified:false,results},null,2)+'\n');})();
