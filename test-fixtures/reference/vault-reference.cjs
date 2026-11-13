// Independent public fixture generator. Requires the hash-checked official default contract and npm saito-wasm 0.3.15.
const fs = require('fs');
const crypto = require('crypto');
const path = require('path');
if (process.argv.length !== 5) throw Error('Usage: node vault-reference.cjs reference-runtime pinned-default-contract output-json');
const runtime = path.resolve(process.argv[2]);
const contractPath = path.resolve(process.argv[3]);
const contractBytes = fs.readFileSync(contractPath);
if (crypto.createHash('sha256').update(contractBytes).digest('hex') !== 'cb33a91e94c0aad86ed6078c1547e3a36dff88ef1e6b65166fe3e9d0d980da58') throw Error('Wrong pinned default contract');
const wasm = require(path.join(runtime,'node_modules/saito-wasm/pkg/node'));
const base58 = require(path.join(runtime,'node_modules/base-58'));
const contract = require(contractPath);
const wasmVersion = require(path.join(runtime,'node_modules/saito-wasm/package.json')).version;
if (wasmVersion !== '0.3.15') throw Error('Wrong pinned WASM version');
const one = '0'.repeat(63)+'1', two='0'.repeat(63)+'2'; // Published unsafe fixture scalars. Never fund.
const creator=wasm.generate_public_key(one),owner=wasm.generate_public_key(two);
const metadata=Buffer.alloc(33);metadata[0]=2;metadata.fill(0x11,1,17);metadata.write('vault-nft-key',17);
const id=metadata.toString('hex'),metadataAddress=base58.encode(metadata);
function slip(key,amount,type,index=0,block=0n,ordinal=0n){let s=new wasm.WasmSlip();Object.assign(s,{public_key:key,amount:BigInt(amount),slip_type:type,slip_index:index,block_id:block,tx_ordinal:ordinal});return s;}
function transaction(body,type,key,inputs,outputs){
 const tx=new wasm.WasmTransaction();tx.timestamp=1700000999000n;tx.txs_replacements=1;tx.type=type;tx.data=Buffer.from(JSON.stringify(body));
 inputs.forEach(s=>tx.add_from_slip(s));outputs.forEach(s=>tx.add_to_slip(s));
 const wire=Buffer.from(tx.serialize()),n=wire.readUInt32BE(0)+wire.readUInt32BE(4),m=93+n*59;
 const parts=[wire.subarray(80,88)];for(let i=0;i<n;i++){const o=93+i*59;parts.push(wire.subarray(o,o+41),wire.subarray(o+57,o+59));}
 const kind=Buffer.alloc(4);kind.writeUInt32BE(type);parts.push(wire.subarray(88,92),kind,wire.subarray(m));
 const preimage=Buffer.concat(parts);tx.generate_hash_for_signature();
 if(Buffer.from(tx.get_hash_for_signature()).toString('hex')!==wasm.hash(preimage))throw Error('WASM preimage differs');
 tx.signature=wasm.sign_buffer(preimage,key);if(!wasm.verify_signature(preimage,tx.signature,wasm.generate_public_key(key)))throw Error('WASM signature invalid');
 const signed=Buffer.from(tx.serialize());const detached=Buffer.from(signed.subarray(0,m));detached.writeUInt32BE(0,8);
 return {id:tx.signature,envelope:JSON.stringify({t:detached.toString('base64'),m:signed.subarray(m).toString('base64'),opt:Buffer.from('{}').toString('base64')}),networkBase64:signed.toString('base64')};
}
const file=Buffer.from('Field notes\nSaito café 🔴\n');
const fileScript = contract.build({nftid:id});
const fileHash = wasm.get_script_hash(JSON.stringify(fileScript));
const payload = {name:'Field notes.txt',file:'data:text/plain;base64,'+file.toString('base64')};
const fileTx=transaction({request:'vault add file',access_script:JSON.stringify(fileScript),access_hash:fileHash,data:payload},0,one,[slip(creator,0n,0)],[slip(creator,0n,0)]);
const missingCommitmentFile=transaction({request:'vault add file',data:payload},0,one,[slip(creator,0n,0)],[slip(creator,0n,0)]);
function keyFor(fileId, address=metadataAddress, name='Field notes.txt') { return transaction({module:'Vault',request:'mint-vault-key',data:{link:'https://saito.io/vault',filename:name,file_id:fileId}},8,one,[slip(creator,200000000n,0,0,2000001n,3n)],[slip(creator,2n,9),slip(owner,100000000n,0,1),slip(address,0n,9,2),slip(creator,100000000n,0,3)]); }
const nftTx=keyFor(fileTx.id),missingCommitmentKey=keyFor(missingCommitmentFile.id);
const before=slip(creator,2n,9,0,2000002n,8n),middle=slip(owner,100000000n,0,1,2000002n,8n),after=slip(metadataAddress,0n,9,2,2000002n,8n);
function utxo(s){let b=Buffer.alloc(59);Buffer.from(base58.decode(s.public_key)).copy(b);b.writeBigUInt64BE(s.block_id,33);b.writeBigUInt64BE(s.tx_ordinal,41);b[49]=s.slip_index;b.writeBigUInt64BE(s.amount,50);b[58]=s.slip_type;const parsed=wasm.WasmSlip.parse_slip_from_utxokey(b.toString('hex'));if(!parsed||parsed.public_key!==s.public_key||parsed.amount!==s.amount)throw Error('WASM UTXO parse');return b.toString('hex');}
const script=contract.build({nftid:id,utxokey1:utxo(before),utxokey2:utxo(middle),utxokey3:utxo(after)});
const hash=wasm.get_script_hash(JSON.stringify(script));
let rows=[slip(owner,50000000n,0,0,2000001n,4n),middle,before,after].map(s=>`${s.public_key} ${s.block_id} ${s.tx_ordinal} ${s.slip_index} ${s.amount} ${s.slip_type}`);
const inventory=Array.from({length:21},(_,index)=>{
 const raw=Buffer.from(metadata);raw.fill(0x11+index,1,17);
 const assetId=raw.toString('hex'),address=base58.encode(raw),name=index===0?'Field notes.txt':`Field notes ${String(index+1).padStart(2,'0')}.txt`;
 const fileGuard=contract.build({nftid:assetId}),fileCommitment=wasm.get_script_hash(JSON.stringify(fileGuard));
 const content=index===0?file:Buffer.from(`Vault pagination file ${index+1}\n`);
 const original=index===0?fileTx:transaction({request:'vault add file',access_script:JSON.stringify(fileGuard),access_hash:fileCommitment,data:{name,file:'data:text/plain;base64,'+content.toString('base64')}},0,one,[slip(creator,0n,0)],[slip(creator,0n,0)]);
 const b=slip(creator,2n,9,0,2000002n,BigInt(8+index)),m=slip(owner,100000000n,0,1,2000002n,BigInt(8+index)),a=slip(address,0n,9,2,2000002n,BigInt(8+index));
 return {id:assetId,name,key:index===0?nftTx:keyFor(original.id,address,name),file:original,
  script:contract.build({nftid:assetId,utxokey1:utxo(b),utxokey2:utxo(m),utxokey3:utxo(a)}),scriptHash:fileCommitment,
  snapshotRows:[m,b,a].map(s=>`${s.public_key} ${s.block_id} ${s.tx_ordinal} ${s.slip_index} ${s.amount} ${s.slip_type}`)};
});
const largeBytes=Buffer.alloc(4*1024*1024);for(let i=0;i<largeBytes.length;i++)largeBytes[i]=(i*31+7)&255;
const largeName='Large Vault.bin',largeMime='application/octet-stream';
const largeBody={request:'vault add file',access_script:JSON.stringify(fileScript),access_hash:fileHash,data:{name:largeName,file:`data:${largeMime};base64,${largeBytes.toString('base64')}`}};
const largeFile=transaction(largeBody,0,one,[slip(creator,0n,0)],[slip(creator,0n,0)]);
const largeEnvelope=JSON.parse(largeFile.envelope);
const large={size:largeBytes.length,name:largeName,mime:largeMime,sha256:crypto.createHash('sha256').update(largeBytes).digest('hex'),
 fileSignature:largeFile.id,detached:largeEnvelope.t,opt:largeEnvelope.opt,key:keyFor(largeFile.id,metadataAddress,largeName),
 accessScript:JSON.stringify(fileScript),accessHash:fileHash};
const result={authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:wasmVersion,wasmSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(runtime,'node_modules/saito-wasm/pkg/node/index_bg.wasm'))).digest('hex'),qualification:'Owned synthetic identities and signed fixture tuples. Independently executed WASM serialisation/signatures/script hash/UTXO decoding; not a live consensus mint.'},creator,owner,id,type:'vault-nft-key',snapshot:`1700001000000-2000003-${'ab'.repeat(32)}.snap\n${rows.join('\n')}\n`,snapshotTotal:'50000000',quantity:'2',deposit:'100000000',script,scriptHash:hash,fileContentsBase64:file.toString('base64'),file:fileTx,key:nftTx,missingCommitmentFile,missingCommitmentKey,inventory,large};
fs.writeFileSync(path.resolve(process.argv[4]),JSON.stringify(result,null,2)+'\n');console.log('WASM reference complete:',result.authority.saitoWasm,id,hash);
