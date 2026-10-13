// Read-only approved-public-peer probe. Fresh throwaway identities; never exports a private key.
const crypto = require('node:crypto');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const fs = require('node:fs');
const assert = require('node:assert/strict');
assert.equal(crypto.createHash('sha256').update(fs.readFileSync('/opt/saito/node/node_modules/saito-wasm/pkg/node/index_bg.wasm')).digest('hex'),
  'f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e');
const approved = [['owner','wss://saito.davidengelen.nl/wsopen'], ['official','wss://saito.io/wsopen']];
async function probe([backend, endpoint]) {
  let privateKey, publicKey;
  do {
    const ecdh = crypto.createECDH('secp256k1'); ecdh.generateKeys();
    privateKey = ecdh.getPrivateKey().toString('hex').padStart(64, '0');
    publicKey = wasm.generate_public_key(privateKey);
  } while (publicKey.length !== 44);
  const challenge = crypto.randomBytes(32), challenges = [challenge];
  const group = wasm.hash(Buffer.from([publicKey, wasm.generate_public_key('0'.repeat(63)+'1')].sort().join('_'), 'utf8'));
  return new Promise(resolve => {
    const socket = new WebSocket(endpoint); socket.binaryType = 'arraybuffer';
    let authenticated = false, peerKey = null, services = null, requested = false, finished = false;
    const finish = result => {
      if (finished) return; finished = true; clearTimeout(timer);
      socket.close();
      resolve({backend, endpoint, observedAt: Date.now(), freshThrowawayIdentity: true,
        authenticated, peerKey, services, ...result});
    };
    const timer = setTimeout(() => finish({error: 'bounded public read timed out'}), 25000);
    function sendRead() {
      if (!authenticated || !services || requested) return;
      requested = true;
      const body = Buffer.from(JSON.stringify({request:'archive',data:{request:'load',field3:group,
        limit:50,created_earlier_than:Date.now()}}));
      const frame = Buffer.alloc(5+93+4*59+body.length);
      frame[0]=12; frame.writeUInt32BE(1,1); frame.writeUInt32BE(2,5); frame.writeUInt32BE(2,9);
      frame.writeUInt32BE(body.length,13); frame.writeBigUInt64BE(BigInt(Date.now()),85); frame.writeUInt32BE(1,93);
      const point = Buffer.from(base58.decode(publicKey));
      for (let index=0;index<4;index++) point.copy(frame,98+59*index);
      body.copy(frame,334); socket.send(frame);
    }
    socket.onopen = () => {socket.send(Buffer.concat([Buffer.from([1]),challenge]));socket.send(Buffer.from([20]));};
    socket.onerror = () => finish({error:'public WebSocket error'});
    socket.onclose = () => finish({error:'public peer disconnected before read'});
    socket.onmessage = event => {
      try {
        const frame = Buffer.from(event.data); assert(frame.length > 0 && frame.length <= 2*1024*1024);
        if (frame[0]===1) {
          assert.equal(frame.length,33); const counter=crypto.randomBytes(32);challenges.push(counter);
          socket.send(Buffer.concat([Buffer.from([2]),Buffer.from(base58.decode(publicKey)),
            Buffer.from(wasm.sign_buffer(frame.subarray(1),privateKey),'hex'),counter]));
        } else if(frame[0]===2) {
          assert.equal(frame.length,130);
          const key=base58.encode(frame.subarray(1,34)), signature=frame.subarray(34,98).toString('hex');
          const index=challenges.findIndex(nonce=>wasm.verify_signature(nonce,signature,key));assert(index>=0);
          challenges.splice(index,1);assert(peerKey===null||peerKey===key);peerKey=key;
          const counter=frame.subarray(98);
          if(counter.some(byte=>byte!==0)) socket.send(Buffer.concat([Buffer.from([2]),Buffer.from(base58.decode(publicKey)),
            Buffer.from(wasm.sign_buffer(counter,privateKey),'hex'),Buffer.alloc(32)]));
          authenticated=true;sendRead();
        } else if(frame[0]===9) {
          const records=new TextDecoder('utf8',{fatal:true}).decode(frame.subarray(1)).split(';').filter(Boolean);
          assert(records.length<=256);services=records.map(record=>{const fields=record.split('|');assert.equal(fields.length,3);return fields[0];});sendRead();
        } else if(frame[0]===7) socket.send(Buffer.from([16]));
        else if(frame[0]===20) socket.send(Buffer.from([9]));
        else if((frame[0]===13||frame[0]===14)&&frame.length>=5&&frame.readUInt32BE(1)===1) {
          const text=new TextDecoder('utf8',{fatal:true}).decode(frame.subarray(5));
          if(frame[0]===14) return finish({error:'Archive returned application error',responseBytes:frame.length-5});
          const rows=JSON.parse(text);assert(Array.isArray(rows)&&rows.length<=50);
          finish({archiveLoadAccepted:true,rows:rows.length,hasArchive:services.includes('archive'),hasRelay:services.includes('relay')});
        }
      } catch {finish({error:'public protocol validation failed'});}
    };
  });
}
Promise.all(approved.map(probe)).then(results=>process.stdout.write(JSON.stringify(results,null,2)+'\n'));
