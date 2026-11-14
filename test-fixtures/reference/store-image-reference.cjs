// Independent Vault mint fixture. Known unsafe test identity; never fund. No network or real storage.
const fs = require('fs'), path = require('path'), crypto = require('crypto');
if(process.argv.length!==6)throw Error('Usage: node store-image-reference.cjs runtime upstream acorn output');
const runtime=path.resolve(process.argv[2]), source=path.resolve(process.argv[3]);
const hashes={
 'node/lib/saito/ui/saito-nft/overlays/create-overlay.js':'39fae6c9fd184f85ba91dc8479b50cf64187364b98f3dede4f3d0fbb7b3a12c1',
 'node/mods/vault/lib/contracts/custom.js':'e16a289d21a55e0c82a150a00319603bdd2298a9dae086316e71c833a1d95a82',
 'node/mods/vault/lib/contracts/rental.js':'c0385317ee08e703bd17b9a9655c142bf1179f3336cd98485a95b792abc3b8e8',
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
 const parser=path.resolve(process.argv[4]);
 if(crypto.createHash('sha256').update(fs.readFileSync(parser)).digest('hex')!=='b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df')throw Error('Wrong acorn');
 const acorn=require(parser),vm=require('vm'),code=fs.readFileSync(path.join(source,'node/lib/saito/ui/saito-nft/overlays/create-overlay.js'),'utf8');
 const klass=acorn.parse(code,{ecmaVersion:'latest'}).body.find(n=>n.type==='ClassDeclaration');
 const method=klass.body.body.find(n=>n.key?.name==='createObject');
 const image=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-images-reference.json'),'utf8')).images.red;
 const object=vm.runInNewContext('({'+code.slice(method.start,method.end)+'})',{document:{querySelector:q=>{if(q!=='#create-nft-type-dropdown')throw Error('Unexpected DOM');return{value:'image'}}},salert:()=>{throw Error('Unexpected alert')}});
 const data=await object.createObject.call({module_provided_nfts:[],image,supportsThumbnail:()=>false});
 const body={data,title:'Native red image',description:'A public image NFT.'};
 const mint=await wallet.createBoundTransaction(1n,100000000n,Buffer.from(JSON.stringify(body)),0n,owner,'image');
 mint.timestamp=timestamp;await mint.sign();
 const signed=envelope(mint),wire=Buffer.from(mint.serialize()),inputs=[];
 for(let i=0;i<wire.readUInt32BE(0);i++){const p=93+i*59;inputs.push({block:String(wire.readBigUInt64BE(p+41)),ordinal:String(wire.readBigUInt64BE(p+49)),index:wire[p+57],amount:String(wire.readBigUInt64BE(p+33)),type:wire[p+58]})}
 fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',sourceHashes:hashes,qualification:'Actual isolated official image createObject method with a minimal DOM type/thumbnail adapter and actual funded WASM wallet.createBoundTransaction. No full frontend, consensus admission or public mint.'},owner,timestamp:String(timestamp),inputs,body,mint:signed},null,2)+'\n');
})().catch(e=>{console.error(e);process.exitCode=1});
