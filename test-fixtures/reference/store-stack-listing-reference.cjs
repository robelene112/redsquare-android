// Actual isolated Store listing builder and WASM wallet. Public unsafe fixture identities only.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
if(![6,7].includes(process.argv.length)||(process.argv.length===7&&!['--image','--partial'].includes(process.argv[6])))throw Error('Usage: node store-listing-reference.cjs runtime upstream acorn.js output [--image]');
const illustrated=process.argv[6]==='--image',quantity=process.argv[6]==='--partial'?1:2;
const runtime=path.resolve(process.argv[2]),root=path.resolve(process.argv[3]),parser=path.resolve(process.argv[4]);
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const hashes={
 'node/mods/stack/stack.js':'fb8a757d267bb3cab87d1750d1dce4cc7ca0b2c8395175fea9459f3e341dd9e9',
 'node/mods/store/lib/transactions.js':'8e7b0c29f8bc1e8e0ede4e966d9fe5c5d385ca00b0f18c31f0304db56a7af8b3',
 'node/mods/store/lib/scripting.js':'917f112d52fd81fc5c6508cc5c1467ffa1df73cd8d12db06982d328b63bc18ff',
 'node/mods/store/lib/helpers.js':'a7617ba4350be5bf307fae6710173ecc9b2abf94c0e627adbdbb97886ec99fce',
 'node/mods/store/lib/summary.js':'e91b1f554a1da2257c4f86a0f14a7ac1abb56e1ff01dd600fd705cde2775979a'
};
for(const [name,expected]of Object.entries(hashes))if(sha(fs.readFileSync(path.join(root,name)))!==expected)throw Error('Wrong pinned source: '+name);
if(sha(fs.readFileSync(parser))!=='b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df')throw Error('Wrong acorn8.19.0');
const acorn=require(parser),wasm=require(path.join(runtime,'node_modules/saito-wasm/pkg/node')),base58=require(path.join(runtime,'node_modules/base-58'));
if(require(path.join(runtime,'node_modules/saito-wasm/package.json')).version!=='0.3.15')throw Error('Wrong WASM');
const text=name=>fs.readFileSync(path.join(root,'node/mods/store/lib',name+'.js'),'utf8');
function fn(name,key){const s=text(name),n=acorn.parse(s,{ecmaVersion:'latest'}).body.find(x=>x.type==='FunctionDeclaration'&&x.id.name===key);return s.slice(n.start,n.end)}
function method(name,key){const s=text(name),tree=acorn.parse(s,{ecmaVersion:'latest'});const object=tree.body.find(n=>n.type==='ExpressionStatement'&&n.expression.type==='AssignmentExpression'&&n.expression.left.object?.name==='module').expression.right;const node=object.properties.find(n=>n.key.name===key);return s.slice(node.start,node.end)}
const consoleAdapter={log(){},warn(){},error(){}};
const scriptGlobals={console:consoleAdapter};
const createListingScript=vm.runInNewContext(fn('scripting','buildBuyerOrStoreScript')+'\n'+fn('scripting','createListingScript')+'\ncreateListingScript',scriptGlobals);
const globals={console:consoleAdapter,createListingScript};
globals.slipPublicKey=vm.runInNewContext('('+fn('helpers','slipPublicKey')+')',globals);
const factory=vm.runInNewContext(fn('transactions','p2shPaymentRecipient')+'\n({'+method('transactions','createListAssetTransaction')+'})',globals).createListAssetTransaction;
const source=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-vault-reference.json'),'utf8'));
const scalar='0'.repeat(63)+'2',timestamp=illustrated?1700002001000n:1700002000000n;
const forbidden=()=>{throw Error('Reference must not use a network')};
global.shared_methods={read_value:async()=>new Uint8Array(),write_value:async()=>{},append_value:()=>{},flush_data:()=>{},ensure_directory_exists:()=>{},load_block_file_list:async()=>[],is_existing_file:async()=>false,remove_value:async()=>{},load_wallet:()=>{},save_wallet:()=>{},load_blockchain:()=>{},save_blockchain:()=>{},emit_interface_event:()=>{},connect_to_peer:forbidden,send_message_by_peer_id:forbidden,send_message:forbidden,send_message_to_all:forbidden};
function envelope(tx){const wire=Buffer.from(tx.serialize()),offset=93+59*(wire.readUInt32BE(0)+wire.readUInt32BE(4)),detached=Buffer.from(wire.subarray(0,offset));detached.writeUInt32BE(0,8);return {id:tx.signature,networkBase64:wire.toString('base64'),envelope:JSON.stringify({t:detached.toString('base64'),m:wire.subarray(offset).toString('base64'),opt:Buffer.from('{}').toString('base64')})}}
(async()=>{
 await wasm.initialize(JSON.stringify({server:{host:'127.0.0.1',port:0,protocol:'http',endpoint:{host:'127.0.0.1',port:0,protocol:'http'},verification_threads:1,channel_size:100,stat_timer_in_ms:5000,reconnection_wait_time:1000,thread_sleep_time_in_ms:10,block_fetch_batch_size:1},peers:[],spv_mode:true,browser_mode:true,consensus:{genesis_period:100000,heartbeat_interval:30000,prune_after_blocks:100,max_staker_recursions:1}}),scalar,0,1n,false);
 const wallet=await wasm.get_wallet(),seller=await wallet.get_public_key(),store=wasm.generate_public_key('0'.repeat(63)+'3');
 const funding=new wasm.WasmWalletSlip();const fundingKey=Buffer.alloc(59);Buffer.from(base58.decode(seller)).copy(fundingKey);fundingKey.writeBigUInt64BE(2000001n,33);fundingKey.writeBigUInt64BE(3n,41);fundingKey.writeBigUInt64BE(200000000n,50);
 funding.set_utxokey(fundingKey.toString('hex'));funding.set_amount(200000000n);funding.set_block_id(2000001n);funding.set_tx_ordinal(3n);funding.set_slip_index(0);funding.set_slip_type(0);funding.set_lc(true);funding.set_spent(false);await wallet.add_slip(funding);
 const minted=await wallet.createBoundTransaction(2n,100000000n,Buffer.from(JSON.stringify({data:{duration:86400000,path:[]},title:'Stack access',module:'Stack'})),0n,seller,'stack');minted.timestamp=1700001000000n;await minted.sign();
 const mintWire=Buffer.from(minted.serialize()),inputCount=mintWire.readUInt32BE(0),id=mintWire.subarray(93+(inputCount+2)*59,93+(inputCount+2)*59+33).toString('hex');
 function witness(index){const p=93+(inputCount+index)*59,b=Buffer.alloc(59);mintWire.subarray(p,p+33).copy(b);b.writeBigUInt64BE(2000002n,33);b.writeBigUInt64BE(8n,41);b[49]=index;b.writeBigUInt64BE(mintWire.readBigUInt64BE(p+33),50);b[58]=mintWire[p+58];return b.toString('hex')}
 const witnesses=[0,1,2].map(witness);source.id=id;source.key=envelope(minted);
 const inventory={mint:source.key,snapshot:'1700001000000-2000003-'+ 'ab'.repeat(32)+'.snap\n'+[1,0,2].map(i=>{const p=93+(inputCount+i)*59;return [base58.encode(mintWire.subarray(p,p+33)),2000002,8,i,String(mintWire.readBigUInt64BE(p+33)),mintWire[p+58]].join(' ')}).join('\n')+'\n'};
 await wallet.add_nft(witnesses[0],witnesses[1],witnesses[2],id,source.key.id,'stack');
 const txmsg=JSON.parse(Buffer.from(JSON.parse(source.key.envelope).m,'base64').toString());
 const code=fs.readFileSync(path.join(root,'node/mods/stack/stack.js'),'utf8'),stackClass=acorn.parse(code,{ecmaVersion:'latest'}).body.find(n=>n.type==='ClassDeclaration'),respond=stackClass.body.body.find(n=>n.key?.name==='respondTo');
 const stack=vm.runInNewContext('({'+code.slice(respond.start,respond.end)+'})',{Buffer,console:consoleAdapter,Date:{now:()=>Number(timestamp)}});
 let hook;
 const nft={id,amount:2,tx:{},txmsg,returnCreator:()=>seller,returnType:()=> 'stack',modifyBeforeSend:async(tx,receiver,data)=>hook(nft,tx,receiver,data)};
 const app={crypto:{hash:value=>wasm.hash(Buffer.from(value)),signMessage:(message,key)=>wasm.sign_buffer(Buffer.from(message),key),toBase58:hex=>base58.encode(Buffer.from(hex,'hex'))},core:{scripting:{hash:s=>wasm.get_script_hash(JSON.stringify(s)),address:s=>wasm.get_script_address(JSON.stringify(s))}},wallet:{getPrivateKey:async()=>scalar,getPublicKey:async()=>seller,createNFTTransaction:async(nft,recipient,amount,fee,deposit,msg)=>{
  const merged=Object.assign(msg,nft.txmsg||{}),native=await wallet.createNFTTransaction(recipient,BigInt(amount),nft.id,fee,deposit,Buffer.from(JSON.stringify(merged)));
  return {msg:merged,native,sign:async function(){native.data=Buffer.from(JSON.stringify(this.msg));native.timestamp=timestamp;await native.sign();this.signature=native.signature}};
 }}};
 hook=stack.respondTo.call({app,publicKey:seller},'saito-nft-transfer').onTransfer;
 const listing={title:'Native Stack subscription',description:'Compatible routing on a Store listing.',category:'Apps & Games',price:illustrated?'3.75':'2.5',quantity_total:quantity,quantity_available:quantity};
 const created=await factory.call({app,store_public_key:store},nft,listing);
 const signed=envelope(created.native);
 const summarySource=text('summary'),tree=acorn.parse(summarySource,{ecmaVersion:'latest'}),klass=tree.body.find(n=>n.type==='ClassDeclaration'&&n.id.name==='Summary');
 const methods=klass.body.body.filter(n=>['constructor','serialize'].includes(n.key?.name)).map(n=>summarySource.slice(n.start,n.end));
 const categories=require(path.join(root,'node/mods/store/lib/categories.js'));
 const Summary=vm.runInNewContext('(class Summary {'+methods.join('\n')+'})',{STORE_CATEGORIES:categories.STORE_CATEGORIES,SUMMARY_STATUS_ACTIVE:1});
 const summary=new Summary(null,null,{nft_id:source.id,seller,category:'Apps & Games',title:listing.title,description:listing.description,listing_signature:signed.id,price:illustrated?375000000:250000000,quantity_available:2,quantity_total:2,created_at:Number(timestamp),updated_at:Number(timestamp),approved:1}).serialize();
 const page={listings:[summary],public_key:'',category:'',pagination:{offset:0,page:1,page_size:24,total:1,total_pages:1,has_next:false,has_previous:false}};
 fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',sourceHashes:hashes,qualification:'Actual isolated Store createListAssetTransaction and Stack respondTo/onTransfer hook, script/helpers and Summary serializer, actual WASM wallet mint and NFT transfer. Wallet wrapper and standard no-hook adapters are not a full frontend or funded consensus listing.'},inventory,seller,store,nftId:source.id,timestamp:String(timestamp),listing:signed,body:created.msg,page},null,2)+'\n');
})().catch(error=>{console.error(error);process.exitCode=1});
