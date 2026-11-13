// Independent Vault mint fixture. Known unsafe test identity; never fund. No network or real storage.
const fs = require('fs'), path = require('path'), crypto = require('crypto');
if (process.argv.length !== 5) throw Error('Usage: node vault-mint-reference.cjs reference-runtime pinned-source-root output');
const runtime=path.resolve(process.argv[2]), source=path.resolve(process.argv[3]);
const hashes={
 'node/mods/vault/lib/contracts/default.js':'cb33a91e94c0aad86ed6078c1547e3a36dff88ef1e6b65166fe3e9d0d980da58',
 'node/mods/vault/lib/ui/overlays/file-upload.js':'f4d8761d55e2415238d999992016ddec38a108fc89459b8aa1af4056b3c74aeb',
 'node/mods/vault/lib/transactions/add-file.js':'bca07d28003e32d148fbdf0f8422f635e634453cfd6047b3e698fcaaca5893f9',
 'rust/saito-core/src/core/consensus/wallet.rs':'5225c5b419f670bae1043d42a9c0c50a5430a46498d8ded44ca895de405ec432'
};
for(const [name,expected] of Object.entries(hashes)) if(crypto.createHash('sha256').update(fs.readFileSync(path.join(source,name))).digest('hex')!==expected)throw Error('Wrong pinned source: '+name);
const wasm=require(path.join(runtime,'node_modules/saito-wasm/pkg/node'));
if(require(path.join(runtime,'node_modules/saito-wasm/package.json')).version!=='0.3.15')throw Error('Wrong runtime');
const base58=require(path.join(runtime,'node_modules/base-58'));
const contract=require(path.join(source,'node/mods/vault/lib/contracts/default.js'));
const scalar='0'.repeat(63)+'2', timestamp=1700000999000n;
const forbidden=()=>{throw Error('Reference must not use a network')};
global.shared_methods={read_value:async()=>new Uint8Array(),write_value:async()=>{},append_value:()=>{},flush_data:()=>{},ensure_directory_exists:()=>{},load_block_file_list:async()=>[],is_existing_file:async()=>false,remove_value:async()=>{},load_wallet:()=>{},save_wallet:()=>{},load_blockchain:()=>{},save_blockchain:()=>{},emit_interface_event:()=>{},connect_to_peer:forbidden,send_message_by_peer_id:forbidden,send_message:forbidden,send_message_to_all:forbidden};
function envelope(tx){
 const wire=Buffer.from(tx.serialize()), offset=93+59*(wire.readUInt32BE(0)+wire.readUInt32BE(4));
 const detached=Buffer.from(wire.subarray(0,offset));detached.writeUInt32BE(0,8);
 return {id:tx.signature,networkBase64:wire.toString('base64'),envelope:JSON.stringify({t:detached.toString('base64'),m:wire.subarray(offset).toString('base64'),opt:Buffer.from('{}').toString('base64')})};
}
async function message(owner,body,time){
 const tx=new wasm.WasmTransaction();tx.timestamp=time;tx.txs_replacements=1;tx.type=0;tx.data=Buffer.from(JSON.stringify(body));
 for(const direction of ['add_from_slip','add_to_slip']){const s=new wasm.WasmSlip();s.public_key=owner;s.amount=0n;s.slip_type=0;tx[direction](s)}
 await tx.sign();return envelope(tx);
}
(async()=>{
 await wasm.initialize(JSON.stringify({server:{host:'127.0.0.1',port:0,protocol:'http',endpoint:{host:'127.0.0.1',port:0,protocol:'http'},verification_threads:1,channel_size:100,stat_timer_in_ms:5000,reconnection_wait_time:1000,thread_sleep_time_in_ms:10,block_fetch_batch_size:1},peers:[],spv_mode:true,browser_mode:true,consensus:{genesis_period:100000,heartbeat_interval:30000,prune_after_blocks:100,max_staker_recursions:1}}),scalar,0,1n,false);
 const wallet=await wasm.get_wallet(), owner=await wallet.get_public_key();
 const utxo=Buffer.alloc(59);Buffer.from(base58.decode(owner)).copy(utxo);utxo.writeBigUInt64BE(2000001n,33);utxo.writeBigUInt64BE(3n,41);utxo.writeBigUInt64BE(200000000n,50);
 const row=new wasm.WasmWalletSlip();row.set_utxokey(utxo.toString('hex'));row.set_amount(200000000n);row.set_block_id(2000001n);row.set_tx_ordinal(3n);row.set_slip_index(0);row.set_slip_type(0);row.set_lc(true);row.set_spent(false);await wallet.add_slip(row);
 const name='Upload notes.txt',mime='text/plain',bytes=Buffer.from('A native Vault upload\nSaito café 🔴\n');
 const body={module:'Vault',request:'mint-vault-key',data:{link:'https://saito.io/vault',filename:name,file_id:''}};
 // This executes the independent Rust wallet factory, including input selection, UUID, deposit and change.
 const mint=await wallet.createBoundTransaction(1n,100000000n,Buffer.from(JSON.stringify(body)),0n,owner,'vault-nft-key');
 const before=Buffer.from(mint.serialize()), offset=93+59*before.readUInt32BE(0)+118;
 const id=before.subarray(offset,offset+33).toString('hex');
 const script=contract.build({nftid:id}), hash=wasm.get_script_hash(JSON.stringify(script));
 // Zero-fund off-chain message shape; official add-file body, verified through actual WASM signing.
 const file=await message(owner,{request:'vault add file',access_script:JSON.stringify(script),access_hash:hash,data:{file:`data:${mime};base64,${bytes.toString('base64')}`,name}},timestamp);
 body.data.file_id=file.id;mint.timestamp=timestamp;mint.data=Buffer.from(JSON.stringify(body));await mint.sign();
 const upload=await message(owner,{request:'vault add file',data:file.envelope},timestamp+1n);
 const destination=wasm.generate_public_key('0'.repeat(63)+'1'); // Published unsafe peer identity.
 const routed=Buffer.from(mint.serialize());routed.writeUInt32BE(1,12);
 const peerBytes=Buffer.from(base58.decode(destination));
 const hopSignature=Buffer.from(wasm.sign_buffer(Buffer.concat([Buffer.from(mint.signature,'hex'),peerBytes]),scalar),'hex');
 const mintBroadcastBase64=Buffer.concat([Buffer.from([4]),routed,Buffer.from(base58.decode(owner)),peerBytes,hopSignature]).toString('base64');
 const largeBytes=Buffer.alloc(4*1024*1024);for(let i=0;i<largeBytes.length;i++)largeBytes[i]=(i*31+7)&255;
 const largeFile=await message(owner,{request:'vault add file',access_script:JSON.stringify(script),access_hash:hash,data:{file:`data:application/octet-stream;base64,${largeBytes.toString('base64')}`,name:'Large upload.bin'}},timestamp);
 const largeMint=wasm.WasmTransaction.deserialize(mint.serialize());
 largeMint.data=Buffer.from(JSON.stringify({...body,data:{...body.data,filename:'Large upload.bin',file_id:largeFile.id}}));await largeMint.sign();
 const largeUpload=await message(owner,{request:'vault add file',data:largeFile.envelope},timestamp+1n);
 const large={size:largeBytes.length,name:'Large upload.bin',mime:'application/octet-stream',contentsSha256:crypto.createHash('sha256').update(largeBytes).digest('hex'),fileSignature:largeFile.id,mint:envelope(largeMint),uploadSignature:largeUpload.id,uploadWireSha256:crypto.createHash('sha256').update(Buffer.from(largeUpload.networkBase64,'base64')).digest('hex')};
 const advanced=[];
 for (const type of ['custom','rental']) {
  await wallet.reset(true);
  const available=new wasm.WasmWalletSlip();available.set_utxokey(utxo.toString('hex'));available.set_amount(200000000n);available.set_block_id(2000001n);available.set_tx_ordinal(3n);available.set_slip_index(0);available.set_slip_type(0);available.set_lc(true);available.set_spent(false);await wallet.add_slip(available);
  const rules=require(path.join(source,'node/mods/vault/lib/contracts/'+type+'.js'));
  const access=type==='rental'?rules.build({creator_publickey:owner}):rules.script;
  const nftType=type==='rental'?'vault-nft-rental':'vault-nft-key';
  const metadata={module:'Vault',request:'mint-vault-key',data:{link:'https://saito.io/vault',filename:name,file_id:''}};
  const key=await wallet.createBoundTransaction(1n,100000000n,Buffer.from(JSON.stringify(metadata)),0n,owner,nftType);
  const keyWire=Buffer.from(key.serialize()), keyOffset=93+59*keyWire.readUInt32BE(0)+118;
  const nftId=keyWire.subarray(keyOffset,keyOffset+33).toString('hex');
  const file=await message(owner,{request:'vault add file',access_script:JSON.stringify(access),access_hash:wasm.get_script_hash(JSON.stringify(access)),data:{file:`data:${mime};base64,${bytes.toString('base64')}`,name}},timestamp);
  metadata.data.file_id=file.id;metadata.data.file_access_script=access;
  key.timestamp=timestamp;key.data=Buffer.from(JSON.stringify(metadata));await key.sign();
  advanced.push({type,nftType,nftId,accessSource:JSON.stringify(access),file,mint:envelope(key)});
 }
 const output={authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',sourceHashes:hashes,wasmSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(runtime,'node_modules/saito-wasm/pkg/node/index_bg.wasm'))).digest('hex'),qualification:'Actual isolated WASM wallet mint factory and signing; synthetic disposable inputs, no consensus mint or provider write.'},owner,destination,mintBroadcastBase64,nftId:id,name,mime,contentsBase64:bytes.toString('base64'),timestamp:String(timestamp),deposit:'100000000',inputs:[{block:'2000001',ordinal:'3',index:0,amount:'200000000',type:0}],file,mint:envelope(mint),upload,large,advanced};
 fs.writeFileSync(path.resolve(process.argv[4]),JSON.stringify(output,null,2)+'\n');console.log('Independent Vault mint reference generated',id);
})().catch(error=>{console.error(error);process.exitCode=1});
