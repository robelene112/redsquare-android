// Owned provider reference: exact pinned Mixin bodies, actual WASM carrier and web framing adapter.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto'),assert=require('assert'),cp=require('child_process');
if(![6,7].includes(process.argv.length))throw Error('Usage: node store-payment-provider-reference.cjs runtime upstream acorn output [compatibility.patch]');
const runtime=path.resolve(process.argv[2]),upstream=path.resolve(process.argv[3]),parser=path.resolve(process.argv[4]);
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const hashes={
 'node/mods/mixin/lib/transactions.js':'69530a41c56fb82c4833baa269b5b79003ef464ccda982339f9b7c12d0761348',
 'node/mods/mixin/mixin.js':'dbcb8c801fa9e195eb0fb574a7a6b398f0239efa66abddd6bb08acd5102ccc6d',
 'node/mods/mixin/lib/buysaito.js':'41b60c6f984f54c7641f195505644c1c9f989eb217b8f1adeaa249099d47652e',
 'node/mods/mixin/lib/transfers.js':'1ed82e94cbf06c4cf0809f7a6d5a883aaa72fd55f07ca0c81e4d6d35228f6ac8'
};
for(const [name,expected]of Object.entries(hashes))assert.equal(sha(fs.readFileSync(path.join(upstream,name))),expected);
assert.equal(sha(fs.readFileSync(parser)),'b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df');
const acorn=require(parser),wasm=require(path.join(runtime,'node_modules/saito-wasm/pkg/node')),base58=require(path.join(runtime,'node_modules/base-58'));
assert.equal(require(path.join(runtime,'node_modules/saito-wasm/package.json')).version,'0.3.15');
const temporary=fs.mkdtempSync(path.join(path.dirname(path.resolve(process.argv[5])),'.store-provider-'));
const output=path.resolve(process.argv[5]);let patchSha=null;
const quiet={log(){},warn(){},error(){}};let clock=1700003000000;
function load(name){return fs.readFileSync(path.join(temporary,name),'utf8')}
function declare(source,name,globals){const tree=acorn.parse(source,{ecmaVersion:'latest'});const klass=tree.body.find(n=>n.type==='ClassDeclaration'&&n.id.name===name);const helpers=tree.body.filter(n=>n.type==='FunctionDeclaration').map(n=>source.slice(n.start,n.end)).join('\n');return vm.runInNewContext(helpers+'\n'+source.slice(klass.start,klass.end)+'\n'+name,globals)}
const forbidden=()=>{throw Error('Provider reference must not use a network')};
global.shared_methods={read_value:async()=>new Uint8Array(),write_value:async()=>{},append_value:()=>{},flush_data:()=>{},ensure_directory_exists:()=>{},load_block_file_list:async()=>[],is_existing_file:async()=>false,remove_value:async()=>{},load_wallet:()=>{},save_wallet:()=>{},load_blockchain:()=>{},save_blockchain:()=>{},emit_interface_event:()=>{},connect_to_peer:forbidden,send_message_by_peer_id:forbidden,send_message:forbidden,send_message_to_all:forbidden};
(async()=>{try{
 for(const name of Object.keys(hashes)){const file=path.join(temporary,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.copyFileSync(path.join(upstream,name),file)}
 if(process.argv[6]){
  const patch=path.resolve(process.argv[6]);patchSha=sha(fs.readFileSync(patch));
  cp.execFileSync('git',['apply','--check',patch],{cwd:temporary,stdio:'pipe'});
  cp.execFileSync('git',['apply',patch],{cwd:temporary,stdio:'pipe'});
 }
 await wasm.initialize(JSON.stringify({server:{host:'127.0.0.1',port:0,protocol:'http',endpoint:{host:'127.0.0.1',port:0,protocol:'http'},verification_threads:1,channel_size:100,stat_timer_in_ms:5000,reconnection_wait_time:1000,thread_sleep_time_in_ms:10,block_fetch_batch_size:1},peers:[],spv_mode:true,browser_mode:true,consensus:{genesis_period:100000,heartbeat_interval:30000,prune_after_blocks:100,max_staker_recursions:1}}),'0'.repeat(63)+'3',0,1n,false);
 const BaseTransaction=require(path.join(runtime,'node_modules/saito-js/lib/transaction')).default;
 BaseTransaction.Type=wasm.WasmTransaction;
 class Transaction extends BaseTransaction {
  deserialize_from_web(app,raw){const envelope=JSON.parse(raw);this.deserialize(Buffer.from(envelope.t,'base64'));this.data=Buffer.from(envelope.m,'base64');this.msg=JSON.parse(Buffer.from(this.data).toString('utf8'))}
  returnMessage(){return this.msg||{}}
 }
 const shared={Transaction,Buffer,console:quiet,Date:{now:()=>clock},QUOTE_TOLERANCE:0.01};
 const transferSource=load('node/mods/mixin/lib/transfers.js');
 const open=acorn.parse(transferSource,{ecmaVersion:'latest'}).body.find(n=>n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id.name==='OPEN_STATUSES'));
 const statuses=vm.runInNewContext(transferSource.slice(open.start,open.end)+'\nOPEN_STATUSES');
 const Transfers=declare(transferSource,'Transfers',{...shared,OPEN_STATUSES:statuses});
 const BuySaito=declare(load('node/mods/mixin/lib/buysaito.js'),'BuySaitoService',shared);
 const vector=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-store-crypto-reference.json'),'utf8'));
 const buyer=vector.buyer,provider=wasm.generate_public_key('0'.repeat(63)+'3');
 const rows=[];let next=1;
 const database={getCredentials:async()=>[],findAddress:async()=>({address:'0x0000000000000000000000000000000000000001'}),
  getTransfers:async filters=>rows.filter(row=>Object.entries(filters).every(([k,v])=>row[k]===v)).map(row=>({...row})),
  insertTransfer:async row=>{await Promise.resolve();const id=next++;rows.push({id,excluded:0,...row});return id},
  updateTransfer:async(id,value)=>Object.assign(rows.find(row=>row.id===id),value)};
 const app={BROWSER:false,crypto:{isPublicKey:key=>{try{return base58.decode(key).length===33}catch{return false}}},
  wallet:{getPrivateKey:async()=>'0'.repeat(63)+'3'},blockchain:{getLatestBlockId:async()=>20n}};
 const cryptoMods=[{ticker:'USDT',asset_id:'known-public-usdt',chain_id:'43d61dcd-e413-450d-80b8-101d5e903357',price_usd:1,last_update:clock,returnMixinNetworkInfo:async()=>{}},
  {ticker:'ERC-SAITO',price_usd:0.001,last_update:clock,returnMixinNetworkInfo:async()=>{}}];
 const mod={publicKey:provider,crypto_mods:cryptoMods,database,accounts:{getCredentials:()=>({user_id:'00000000-0000-4000-8000-000000000001'})},returnSaitoIssuancePublicKey:()=>provider};
 mod.transfers=new Transfers(app,mod);const service=new BuySaito(app,mod);
 const request={initiator_pubkey:buyer,recipient_pubkey:buyer,ticker:'USDT',expected_deposit:7.5,request_id:vector.carrier.id,tx:vector.carrier.envelope};
 const first=await Promise.all([service.startPurchase(buyer,request),service.startPurchase(buyer,request)]);
 assert.equal(first[0].request_id,vector.carrier.id,'Invoice must echo the saved request identity');
 assert.equal(first[0].purchase_signature,vector.carrier.id,'Invoice must identify its actual saved carrier');
 assert.equal(first[0].id,first[1].id,'Concurrent retries must retain one invoice');assert.equal(rows.length,1);
 const before=JSON.stringify(rows);
 const status=await service.getPurchaseStatus(buyer,{ticker:'USDT',request_id:vector.carrier.id});
 assert.equal(status.found,true);assert.equal(status.request_id,vector.carrier.id);assert.equal(status.purchase_signature,vector.carrier.id);
 assert.equal(JSON.stringify(rows),before,'Status must not reserve or mutate');
 const absent=await service.getPurchaseStatus(buyer,{ticker:'USDT',request_id:'ab'.repeat(64)});
 assert.equal(absent.found,false);assert.equal(rows.length,1);
 const other=await service.startPurchase(buyer,{...request,request_id:'ab'.repeat(64)});
 assert.equal(other.request_id,vector.carrier.id,'Existing reservation must not be rebound to a new order');
 // Execute the real provider payout factory after an owned Mixin deposit observation.
 const providerWallet=await wasm.get_wallet();
 const slip=new wasm.WasmWalletSlip();slip.set_amount(1000000000000n);slip.set_block_id(10n);slip.set_tx_ordinal(2n);slip.set_slip_index(0);slip.set_slip_type(0);slip.set_lc(true);slip.set_spent(false);
 const key=Buffer.alloc(59);Buffer.from(base58.decode(provider)).copy(key);key.writeBigUInt64BE(10n,33);key.writeBigUInt64BE(2n,41);key.writeBigUInt64BE(1000000000000n,50);slip.set_utxokey(key.toString('hex'));await providerWallet.add_slip(slip);
 app.crypto.toBase58=hex=>base58.encode(Buffer.from(hex,'hex'));
 app.wallet.createUnsignedTransactionWithDefaultFee=async(recipient,amount)=>{
  const native=await providerWallet.createTransaction(recipient,amount,10000000n,false);
  return {native,msg:{},sign:async function(){native.data=Buffer.from(JSON.stringify(this.msg));native.timestamp=BigInt(clock+1000);await native.sign();this.signature=native.signature}};
 };
 const payoutSource=load('node/mods/mixin/lib/transactions.js');
 const payoutFunctions=acorn.parse(payoutSource,{ecmaVersion:'latest'}).body.filter(n=>n.type==='FunctionDeclaration').map(n=>payoutSource.slice(n.start,n.end)).join('\n');
 const payout=vm.runInNewContext(payoutFunctions+'\ncreateBuySaitoPayoutTransaction',{...shared,Transaction});
 const credited=rows[0];credited.inbound_tx_hash='ab'.repeat(32);credited.status='inbound_confirmed';
 const amount=BigInt(Math.round(Number(first[0].issue_amount)*100000000));
 const funded=await payout(app,credited,amount);
 function envelope(tx){const wire=Buffer.from(tx.serialize()),offset=93+59*(wire.readUInt32BE(0)+wire.readUInt32BE(4)),detached=Buffer.from(wire.subarray(0,offset));detached.writeUInt32BE(0,8);return {id:tx.signature,networkBase64:wire.toString('base64'),envelope:JSON.stringify({t:detached.toString('base64'),m:wire.subarray(offset).toString('base64'),opt:Buffer.from('{}').toString('base64')})}}
 const issuance=envelope(funded.tx.native);credited.outbound_tx_sig=issuance.id;credited.outbound_tx=issuance.envelope;credited.status='outbound_awaiting';
 const issuedStatus=await service.getPurchaseStatus(buyer,{ticker:'USDT',request_id:vector.carrier.id});
 assert.equal(issuedStatus.outbound_tx_sig,issuance.id);assert.equal(funded.tx.msg.transfer_uuid,String(credited.id));assert.equal(funded.tx.msg.inbound_payment_uuid,credited.inbound_tx_hash);
 const repeated=await service.startPurchase(buyer,request);assert.equal(repeated.id,first[0].id);assert.equal(rows.length,1);
 const outcomes=[];
 for(const state of ['outbound_confirmed','succeeded','failed']){
  credited.status=state;const retained=await service.startPurchase(buyer,request);
  assert.equal(retained.id,first[0].id);assert.equal(rows.length,1);
  const beforeRead=JSON.stringify(rows);
  const read=await service.getPurchaseStatus(buyer,{ticker:'USDT',request_id:vector.carrier.id});
  assert.equal(read.transfer_status,state);assert.equal(JSON.stringify(rows),beforeRead);
  outcomes.push({state,quote:retained,status:read});
 }
 const foreign=await service.getPurchaseStatus(wasm.generate_public_key('0'.repeat(63)+'5'),{ticker:'USDT',request_id:vector.carrier.id});
 assert.equal(foreign.found,false,'Another wallet cannot read an invoice');
 const methodQuotes=[];
 for(const item of vector.methodCarriers){
  let installed=mod.crypto_mods.find(c=>c.ticker===item.currency.ticker);
  if(!installed){installed={...item.currency,asset_id:'owned-'+item.currency.ticker,price_usd:1,last_update:clock,returnMixinNetworkInfo:async()=>{}};mod.crypto_mods.push(installed);}
  const current=await service.startPurchase(buyer,{...request,ticker:item.currency.ticker,request_id:item.carrier.id,tx:item.carrier.envelope});
  assert.equal(current.purchase_signature,item.carrier.id);assert.equal(current.ticker,item.currency.ticker);
  assert.equal(current.chain_id,item.currency.chain_id);assert.equal(current.expected_deposit,7.5);
  const saved=rows.find(r=>r.id===current.id);saved.status='failed';
  methodQuotes.push({currency:item.currency,carrier:item.carrier,quote:current});
 }
 const saitoQuotes=[];
 for(const item of vector.methodCarriers){
  const next={...request,ticker:item.currency.ticker,request_id:vector.saitoCarrier.id,tx:vector.saitoCarrier.envelope,issue_amount:5,expected_deposit:0.0055};
  const quote=await service.startPurchase(buyer,next);
  assert.equal(quote.purchase_signature,vector.saitoCarrier.id);assert.equal(quote.issue_amount,5);assert.equal(quote.expected_deposit,0.005501);
  rows.find(row=>row.id===quote.id).status='failed';saitoQuotes.push({currency:item.currency,quote});
 }
 const count=rows.length,usdt=cryptoMods[0],rate=usdt.price_usd;
 usdt.price_usd=0;
 await assert.rejects(()=>service.startPurchase(buyer,request),/No price is available/);
 assert.equal(rows.length,count,'Quote outage cannot allocate an invoice');usdt.price_usd=rate;

 fs.writeFileSync(output,JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',sourceHashes:hashes,patchSha256:patchSha,saitoWasm:'0.3.15',qualification:'Actual isolated pinned-plus-declared-compatibility-patch BuySaitoService/Transfers methods with actual WASM carrier decoding and a web-framing adapter. Owned in-memory database and credential/address adapters, actual isolated provider payout creation with funded WASM wallet and synthetic inbound hash. No network, real provider deposit, live activation, full initialized runtime or funded consensus.'},buyer,provider,carrier:vector.carrier,request,quote:first[0],status,absent,conflict:other,issuance,issuedBody:funded.tx.msg,issuedStatus,outcomes,foreign,methodQuotes,saitoQuotes,rows},null,2)+'\n');
 console.log('PASS: bound invoice, concurrent original retry, read-only wallet-scoped restoration, seven methods, completed/failed recovery and quote outage');
}finally{fs.rmSync(temporary,{recursive:true,force:true})}})().catch(error=>{console.error(error);process.exitCode=1});
