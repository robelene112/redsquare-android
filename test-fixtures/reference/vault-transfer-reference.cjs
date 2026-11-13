// Independent Vault mint fixture. Known unsafe test identity; never fund. No network or real storage.
const fs = require('fs'), path = require('path'), crypto = require('crypto');
if (process.argv.length !== 6) throw Error('Usage: node vault-transfer-reference.cjs reference-runtime pinned-source-root key-reference output');
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

(async()=>{
 await wasm.initialize(JSON.stringify({server:{host:'127.0.0.1',port:0,protocol:'http',endpoint:{host:'127.0.0.1',port:0,protocol:'http'},verification_threads:1,channel_size:100,stat_timer_in_ms:5000,reconnection_wait_time:1000,thread_sleep_time_in_ms:10,block_fetch_batch_size:1},peers:[],spv_mode:true,browser_mode:true,consensus:{genesis_period:100000,heartbeat_interval:30000,prune_after_blocks:100,max_staker_recursions:1}}),scalar,0,1n,false);

const vector=JSON.parse(fs.readFileSync(process.argv[4],'utf8'));
const wallet=await wasm.get_wallet();const w=vector.script.witness;
await wallet.add_nft(w.utxokey1,w.utxokey2,w.utxokey3,vector.id,vector.key.id,'vault-nft-key');
const recipient=wasm.generate_public_key('0'.repeat(63)+'3');const original=JSON.parse(vector.key.envelope);
const metadata=JSON.parse(Buffer.from(original.m,'base64').toString('utf8'));
const transfer=await wallet.createSendBoundTransaction(2n,w.utxokey1,w.utxokey2,w.utxokey3,recipient,Buffer.from(JSON.stringify(metadata)));
transfer.timestamp=1700001000001n;transfer.txs_replacements=1;await transfer.sign();
const wire=Buffer.from(transfer.serialize()),n=wire.readUInt32BE(0),m=wire.readUInt32BE(4);
// The independent WASM block serializer assigns the actual output coordinates.
transfer.generate_hash_for_signature();
const header=Buffer.alloc(389);header.writeUInt32BE(1,0);header.writeBigUInt64BE(2000003n,4);header.writeBigUInt64BE(1700001005000n,12);
Buffer.alloc(32,0xab).copy(header,20);Buffer.from(base58.decode(recipient)).copy(header,52);
Buffer.from(transfer.get_hash_for_signature()).copy(header,85);
const preimage=Buffer.concat([header.subarray(4,117),header.subarray(181,213),header.subarray(221,285)]);
Buffer.from(wasm.sign_buffer(preimage,'0'.repeat(63)+'3'),'hex').copy(header,117);
const block=new wasm.WasmBlock();if(block.deserialize(Buffer.concat([header,wire]))!=='')throw Error('Block did not decode');
const full=Buffer.from(block.serialize());const again=new wasm.WasmBlock();if(again.deserialize(full)!==''||!Buffer.from(again.serialize()).equals(full))throw Error('Block did not round trip');
if(again.transactions[0].signature!==transfer.signature)throw Error('Different included transfer');
const changed=Buffer.from(full);changed.writeBigUInt64BE(2000001n,389+93+41);
const altered=new wasm.WasmBlock();if(altered.deserialize(changed)!==''||altered.hash!==again.hash)throw Error('Unexpected coordinate mutation');
const blockReference={hash:again.hash,height:'2000003',timestamp:'1700001005000',fullBase64:full.toString('base64'),changedInputBase64:changed.toString('base64')};
fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',sourceHashes:hashes,qualification:'Actual isolated wallet createSendBoundTransaction, whole quantity/deposit; no chain inclusion or public transmission.'},owner:await wallet.get_public_key(),recipient,timestamp:'1700001000001',holding:vector.script.witness,block:blockReference,key:vector.key,transfer:envelope(transfer)},null,2)+'\n');
console.log('Independent whole-key transfer reference generated');
})().catch(error=>{console.error(error);process.exitCode=1});
