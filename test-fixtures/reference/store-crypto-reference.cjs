// Actual isolated Store listing builder and WASM wallet. Public unsafe fixture identities only.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
if(process.argv.length!==6)throw Error('Usage: node store-crypto-reference.cjs runtime upstream acorn.js output');

const quantity=2;
const runtime=path.resolve(process.argv[2]),root=path.resolve(process.argv[3]),parser=path.resolve(process.argv[4]);
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const hashes={
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
const createPurchaseScript=vm.runInNewContext(fn('scripting','buildBuyerOrStoreScript')+'\n'+fn('scripting','createListingScript')+'\n'+fn('scripting','createPurchaseScript')+'\ncreatePurchaseScript',scriptGlobals);
const globals={console:consoleAdapter,createPurchaseScript};
globals.slipPublicKey=vm.runInNewContext('('+fn('helpers','slipPublicKey')+')',globals);
const factory=vm.runInNewContext(fn('transactions','p2shPaymentRecipient')+'\n({'+method('transactions','createPurchaseAssetTransaction')+'})',globals).createPurchaseAssetTransaction;
const forbidden=()=>{throw Error('Reference must not use a network')};
global.shared_methods={read_value:async()=>new Uint8Array(),write_value:async()=>{},append_value:()=>{},flush_data:()=>{},ensure_directory_exists:()=>{},load_block_file_list:async()=>[],is_existing_file:async()=>false,remove_value:async()=>{},load_wallet:()=>{},save_wallet:()=>{},load_blockchain:()=>{},save_blockchain:()=>{},emit_interface_event:()=>{},connect_to_peer:forbidden,send_message_by_peer_id:forbidden,send_message:forbidden,send_message_to_all:forbidden};
function envelope(tx){const wire=Buffer.from(tx.serialize()),offset=93+59*(wire.readUInt32BE(0)+wire.readUInt32BE(4)),detached=Buffer.from(wire.subarray(0,offset));detached.writeUInt32BE(0,8);return {id:tx.signature,networkBase64:wire.toString('base64'),envelope:JSON.stringify({t:detached.toString('base64'),m:wire.subarray(offset).toString('base64'),opt:Buffer.from('{}').toString('base64')})}}

(async()=>{
 await wasm.initialize(JSON.stringify({server:{host:'127.0.0.1',port:0,protocol:'http',endpoint:{host:'127.0.0.1',port:0,protocol:'http'},verification_threads:1,channel_size:100,stat_timer_in_ms:5000,reconnection_wait_time:1000,thread_sleep_time_in_ms:10,block_fetch_batch_size:1},peers:[],spv_mode:true,browser_mode:true,consensus:{genesis_period:100000,heartbeat_interval:30000,prune_after_blocks:100,max_staker_recursions:1}}),'0'.repeat(63)+'4',0,1n,false);
 const wallet=await wasm.get_wallet(),buyer=await wallet.get_public_key(),store=wasm.generate_public_key('0'.repeat(63)+'3');
 const listing=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-store-listing-reference.json'),'utf8'));
 const timestamp=1700003000000n;
 const app={crypto:{toBase58:hex=>base58.encode(Buffer.from(hex,'hex'))},core:{scripting:{hash:s=>wasm.get_script_hash(JSON.stringify(s)),address:s=>wasm.get_script_address(JSON.stringify(s))}},wallet:{getPublicKey:async()=>buyer,createUnsignedTransactionWithDefaultFee:async(recipient,amount,fee)=>{
 const native=await wallet.createTransaction(recipient,amount,fee ?? 10000000n,false);
 return {native,msg:{},sign:async function(){native.data=Buffer.from(JSON.stringify(this.msg));native.timestamp=timestamp;await native.sign();this.signature=native.signature}};
 }}};
 const summary={...listing.page.listings[0],price:0,web3_ticker:'USDT',web3_amount:'3.75'};
 const created=await factory.call({app,store_public_key:store},summary,{quantity,price:'0',fee:'0'},0n);
 const signed=envelope(created.native),wire=Buffer.from(signed.networkBase64,'base64');
 // Execute the pinned service's currency enumeration; no hand-maintained allowed list.
 const mixinSource=fs.readFileSync(path.join(root,'node/mods/mixin/mixin.js'),'utf8');
 if(sha(mixinSource)!=='dbcb8c801fa9e195eb0fb574a7a6b398f0239efa66abddd6bb08acd5102ccc6d')throw Error('Wrong Mixin source');
 const mixinClass=acorn.parse(mixinSource,{ecmaVersion:'latest'}).body.find(n=>n.type==='ClassDeclaration'&&n.id.name==='Mixin');
 const handler=mixinClass.body.body.find(n=>n.key.name==='handlePeerTransaction');
 const officialHandler=vm.runInNewContext('({'+mixinSource.slice(handler.start,handler.end)+'})',{console:consoleAdapter,Buffer,Date:{now:()=>Number(timestamp)},PROVIDER_UNAVAILABLE:'Payment provider unavailable'}).handlePeerTransaction;
 const observed=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-store-currency-observation.json'),'utf8'));
 const currencies=observed.response.ac.map(item=>({...item,last_update:Number(timestamp)-1000}));
 const cryptoMods=[...currencies,{ticker:'ERC-SAITO',price_usd:observed.response.erc,last_update:Number(timestamp)-1000},{ticker:'BEP-SAITO',price_usd:1,last_update:Number(timestamp)-1000},{ticker:'NO-PRICE',price_usd:0,last_update:Number(timestamp)-1000}];
 let methods=null;
 const discoveryApp={BROWSER:false,modules:{returnModule:()=>null},network:{sendRequestAsTransaction:()=>{}}};
 const service={app:discoveryApp,publicKey:store,appSession:{},transfers:{},crypto_mods:cryptoMods,
  tunnelRequestToTreasury:async()=>false,isHandshakeVerifiedPeer:()=>true,configuredTreasuryDelegatePublickey:()=>'',isAuthorizedForwardingPeer:()=>false,isAuthorizedFulfillmentPeer:()=>true};
 await officialHandler.call(service,discoveryApp,{returnMessage:()=>({request:'buysaito available currencies',data:{module:'Mixin'}}),from:[{publicKey:buyer}],isTo:()=>true},{publicKey:buyer},reply=>{methods=reply});
 if(!methods?.ac?.length)throw Error('Missing official currency response');
 const methodCarriers=[];
 for(const currency of methods.ac){
  const item={...summary,web3_ticker:currency.ticker};
  const payment=await factory.call({app,store_public_key:store},item,{quantity,price:'0',fee:'0'},0n);
  methodCarriers.push({currency,carrier:envelope(payment.native),body:payment.msg});
 }
 fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',sourceHashes:{...hashes,'node/mods/mixin/mixin.js':sha(mixinSource)},qualification:'Actual isolated official Store unfunded purchase carrier factory and actual WASM wallet.createTransaction(0,0). Deterministic public unsafe identities and fixed clock; no provider reservation, deposit, broadcast or funded consensus. Actual isolated Mixin.handlePeerTransaction currency enumeration over the seven observed public modules plus explicit excluded/unpriced candidates, with owned identity and module-state adapters; not full peer/runtime authentication.'},buyer,store,nftId:listing.nftId,timestamp:String(timestamp),summary,methods,methodCarriers,carrier:signed,body:created.msg},null,2)+'\n');
})().catch(error=>{console.error(error);process.exitCode=1});
