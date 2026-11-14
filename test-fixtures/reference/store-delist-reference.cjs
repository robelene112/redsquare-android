// Actual isolated Store seller delisting builder and WASM wallet. Public unsafe fixture identities only.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
if(![6,7].includes(process.argv.length)||(process.argv.length===7&&!['--atr','--remaining'].includes(process.argv[6])))throw Error('Usage: node store-delist-reference.cjs runtime upstream acorn.js output [--atr|--remaining]');
const quantity=1;
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
 const funding=new wasm.WasmWalletSlip();funding.set_amount(1000000000n);funding.set_block_id(10n);funding.set_tx_ordinal(2n);funding.set_slip_index(0);funding.set_slip_type(0);funding.set_lc(true);funding.set_spent(false);
 const key=Buffer.alloc(59);Buffer.from(base58.decode(buyer)).copy(key);key.writeBigUInt64BE(10n,33);key.writeBigUInt64BE(2n,41);key.writeBigUInt64BE(1000000000n,50);funding.set_utxokey(key.toString('hex'));await wallet.add_slip(funding);
 const listing=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-store-listing-reference.json'),'utf8'));
 const timestamp=1700003000000n;
 const app={crypto:{toBase58:hex=>base58.encode(Buffer.from(hex,'hex'))},core:{scripting:{hash:s=>wasm.get_script_hash(JSON.stringify(s)),address:s=>wasm.get_script_address(JSON.stringify(s))}},wallet:{getPublicKey:async()=>buyer,createUnsignedTransactionWithDefaultFee:async(recipient,amount)=>{
 const native=await wallet.createTransaction(recipient,amount,10000000n,false);
 return {native,msg:{},sign:async function(){native.data=Buffer.from(JSON.stringify(this.msg));native.timestamp=timestamp;await native.sign();this.signature=native.signature}};
 }}};
 const created=await factory.call({app,store_public_key:store},listing.page.listings[0],{quantity,price:'2.5',fee:'0'},250000000n*BigInt(quantity));
 const signed=envelope(created.native),wire=Buffer.from(signed.networkBase64,'base64');
 // Execute the official settlement/refund algorithms using the actual JS/WASM transaction and slip wrappers.
 const BaseTransaction=require(path.join(runtime,'node_modules/saito-js/lib/transaction')).default,Slip=require(path.join(runtime,'node_modules/saito-js/lib/slip')).default,Saito=require(path.join(runtime,'node_modules/saito-js/saito')).default;
 const Transaction=class extends BaseTransaction { addFromSlip(slip){super.addFromSlip(new Slip(undefined,slip.toJson()));} };BaseTransaction.Type=wasm.WasmTransaction;Slip.Type=wasm.WasmSlip;Saito.instance={factory:{createSlip:n=>new Slip(n)}};
 const helperGlobals={Slip,SlipType:{Bound:9,Normal:0,ATR:1},SLIP_TYPE_P2SH:10};
 for(const name of ['isP2shPublicKey','isBoundSlip','slipRequiresP2shWitness','listRustP2shInputIndexes','normalizeSlipJson','parseStoredSlip','paymentInputFromOrder','listingTxmsg','slipPublicKey'])helperGlobals[name]=vm.runInNewContext('('+fn('helpers',name)+')',helperGlobals);
 helperGlobals.isNFTTuple=vm.runInNewContext('('+fn('scripting','isNFTTuple')+')',helperGlobals);
 helperGlobals.listingInputSlipJsonFromRecord=vm.runInNewContext('('+fn('helpers','listingInputSlipJsonFromRecord')+')',helperGlobals);
 helperGlobals.parseAccessScript=vm.runInNewContext('('+fn('scripting','parseAccessScript')+')',helperGlobals);
 helperGlobals.signAccessScriptWitness=vm.runInNewContext('('+fn('scripting','signAccessScriptWitness')+')',helperGlobals);
 const settlementGlobals={...helperGlobals,Buffer,Transaction,TransactionType:{Bound:8},SaitoNFT:class{ returnType(){return 'vault-nft-key'} },Date:{now:()=>1700004000000},console:consoleAdapter};
 settlementGlobals.partitionCustodyDeposit=vm.runInNewContext('('+fn('transactions','partitionCustodyDeposit')+')',settlementGlobals);
 const methods=['attachFulfillmentTxmsg','createFulfillmentTransaction','createOrderRefundTransaction'].map(name=>method('transactions',name));
 const storeMethods=vm.runInNewContext('({'+methods.join(',')+'})',settlementGlobals);
 const storeScalar='0'.repeat(63)+'3';await wallet.set_private_key(storeScalar);await wallet.set_public_key(store);
 const storeApp={crypto:{toBase58:hex=>base58.encode(Buffer.from(hex,'hex')),fromBase58:key=>Buffer.from(base58.decode(key)).toString('hex'),hash:bytes=>wasm.hash(bytes)},wallet:{signMessage:async message=>wasm.sign_buffer(Buffer.from(message),storeScalar)}};
 function confirmedOutput(network,index,height,ordinal){const b=Buffer.from(network,'base64'),o=93+(b.readUInt32BE(0)+index)*59,k=b.subarray(o,o+33),amount=b.readBigUInt64BE(o+33);const u=Buffer.alloc(59);k.copy(u);u.writeBigUInt64BE(BigInt(height),33);u.writeBigUInt64BE(BigInt(ordinal),41);u[49]=index;u.writeBigUInt64BE(amount,50);u[58]=b[o+58];return {publicKey:base58.encode(k),amount:String(amount),type:b[o+58],index,blockId:String(height),txOrdinal:String(ordinal),utxoKey:u.toString('hex')};}
 const purchaseOutput=confirmedOutput(signed.networkBase64,0,21,0),triple=[0,1,2].map(index=>confirmedOutput(listing.listing.networkBase64,index,20,0));
 const order={order_tx_sig:signed.id,buyer,nft_id:listing.nftId,quantity,price:250000000,payment_amount:String(250000000n*BigInt(quantity)),payment_tx_sig:signed.id,payment_output_index:0,utxo_slip:purchaseOutput,access_script:created.msg.access_script};
 const row={access_hash:listing.body.access_hash,signature:listing.listing.id,seller:listing.seller,nft_id:listing.nftId,quantity:2,take_qty:quantity,price:250000000,p2sh_address:listing.body.p2sh_address,access_script:listing.body.access_script,utxo_slip1:triple[0],utxo_slip2:triple[1],utxo_slip3:triple[2]};
 if(process.argv[6]==='--atr'){
 for(let i=0;i<3;i++){
  const slip=row['utxo_slip'+(i+1)];slip.index=i+3;if(i===1)slip.type=1;
  const key=Buffer.alloc(59);Buffer.from(base58.decode(slip.publicKey)).copy(key);key.writeBigUInt64BE(BigInt(slip.blockId),33);key.writeBigUInt64BE(BigInt(slip.txOrdinal),41);key[49]=slip.index;key.writeBigUInt64BE(BigInt(slip.amount),50);key[58]=slip.type;slip.utxoKey=key.toString('hex');
 }
 }
 const mod={app:storeApp,fee:0,...storeMethods};
 let priorFulfillment=null;
 if(process.argv[6]==='--remaining'){
  priorFulfillment=await mod.createFulfillmentTransaction(order,[row],{msg:listing.body});
  const prior=envelope(priorFulfillment.wasmTransaction);
  row.signature=prior.id;row.quantity=1;
  for(let i=0;i<3;i++)row['utxo_slip'+(i+1)]=confirmedOutput(prior.networkBase64,i+3,22,0);
 }
 await wallet.set_private_key('0'.repeat(63)+'2');await wallet.set_public_key(listing.seller);
 const sellerApp={...storeApp,wallet:{getPublicKey:async()=>listing.seller,signMessage:async m=>wasm.sign_buffer(Buffer.from(m),'0'.repeat(63)+'2')}};
 const delistBuilder=vm.runInNewContext('({'+method('transactions','createDelistAssetTransaction')+'})',settlementGlobals).createDelistAssetTransaction;
 const delisted=await delistBuilder.call({app:sellerApp},row,{msg:priorFulfillment ? priorFulfillment.msg : listing.body});
 await wallet.set_private_key(storeScalar);await wallet.set_public_key(store);
 const delivered=await mod.createFulfillmentTransaction(order,[row],{msg:listing.body});
 const refund=await mod.createOrderRefundTransaction({order_row:order,reason:'insufficient-quantity'});await refund.sign();
 const settlement={delist:envelope(delisted.wasmTransaction),fulfillment:envelope(delivered.wasmTransaction),refund:envelope(refund.wasmTransaction),order,row};
 function blockFor(tx,height,parent,time){
  tx.generate_hash_for_signature();const header=Buffer.alloc(389);header.writeUInt32BE(1);header.writeBigUInt64BE(BigInt(height),4);header.writeBigUInt64BE(BigInt(time),12);Buffer.from(parent,'hex').copy(header,20);Buffer.from(base58.decode(store)).copy(header,52);Buffer.from(tx.get_hash_for_signature()).copy(header,85);
  const preimage=Buffer.concat([header.subarray(4,117),header.subarray(181,213),header.subarray(221,285)]);Buffer.from(wasm.sign_buffer(preimage,storeScalar),'hex').copy(header,117);
  const block=new wasm.WasmBlock();if(block.deserialize(Buffer.concat([header,Buffer.from(tx.serialize())]))!=='')throw Error('Block failed');const full=Buffer.from(block.serialize()),again=new wasm.WasmBlock();if(again.deserialize(full)!==''||!Buffer.from(again.serialize()).equals(full))throw Error('Block roundtrip failed');return {hash:again.hash,height:String(height),timestamp:String(time),fullBase64:full.toString('base64')};
 }
 const paymentBlock=blockFor(created.native,21,'ab'.repeat(32),1700004001000),deliveryBlock=blockFor(delivered.wasmTransaction,22,paymentBlock.hash,1700004002000),refundBlock=blockFor(refund.wasmTransaction,22,paymentBlock.hash,1700004002000);
 const deliveryWire=Buffer.from(deliveryBlock.fullBase64,'base64').subarray(389),rows=[1,0,2].map(index=>confirmedOutput(deliveryWire.toString('base64'),index,22,0));
 const snapshot=deliveryBlock.timestamp+'-22-'+deliveryBlock.hash+'.snap\n'+rows.map(r=>[r.publicKey,r.blockId,r.txOrdinal,r.index,r.amount,r.type].join(' ')).join('\n')+'\n';
 const blocks={payment:paymentBlock,delivery:deliveryBlock,refund:refundBlock,snapshot:snapshot.replaceAll('\\n','\n')};
 const olderBlocks={};let olderTip=deliveryBlock;
 for(let height=23;height<=42;height++){
  const header=Buffer.alloc(389);header.writeBigUInt64BE(BigInt(height),4);header.writeBigUInt64BE(1700004002000n+BigInt(height),12);Buffer.from(olderTip.hash,'hex').copy(header,20);Buffer.from(base58.decode(store)).copy(header,52);
  const preimage=Buffer.concat([header.subarray(4,117),header.subarray(181,213),header.subarray(221,285)]);Buffer.from(wasm.sign_buffer(preimage,storeScalar),'hex').copy(header,117);
  const parsed=new wasm.WasmBlock();if(parsed.deserialize(header)!=='')throw Error('Empty ancestor failed');olderTip={hash:parsed.hash,height:String(height),timestamp:String(1700004002000n+BigInt(height)),fullBase64:Buffer.from(parsed.serialize()).toString('base64')};olderBlocks[olderTip.hash]=olderTip.fullBase64;
 }
 olderBlocks[deliveryBlock.hash]=deliveryBlock.fullBase64;olderBlocks[paymentBlock.hash]=paymentBlock.fullBase64;
 blocks.older={blocks:olderBlocks,snapshot:snapshot.replace(deliveryBlock.timestamp+'-22-'+deliveryBlock.hash,olderTip.timestamp+'-42-'+olderTip.hash)};

 const delayedBlocks={};let delayedTip=paymentBlock;
 for(let height=22;height<=40;height++){
  const header=Buffer.alloc(389);header.writeBigUInt64BE(BigInt(height),4);header.writeBigUInt64BE(1700005000000n+BigInt(height),12);Buffer.from(delayedTip.hash,'hex').copy(header,20);Buffer.from(base58.decode(store)).copy(header,52);
  const preimage=Buffer.concat([header.subarray(4,117),header.subarray(181,213),header.subarray(221,285)]);Buffer.from(wasm.sign_buffer(preimage,storeScalar),'hex').copy(header,117);
  const parsed=new wasm.WasmBlock();if(parsed.deserialize(header)!=='')throw Error('Delayed ancestor failed');delayedTip={hash:parsed.hash,height:String(height),timestamp:String(1700005000000n+BigInt(height)),fullBase64:Buffer.from(parsed.serialize()).toString('base64')};delayedBlocks[delayedTip.hash]=delayedTip.fullBase64;
 }
 const delayedDelivery=blockFor(delivered.wasmTransaction,41,delayedTip.hash,1700005001000);delayedBlocks[delayedDelivery.hash]=delayedDelivery.fullBase64;delayedBlocks[paymentBlock.hash]=paymentBlock.fullBase64;
 const delayedWire=Buffer.from(delayedDelivery.fullBase64,'base64').subarray(389),delayedRows=[1,0,2].map(index=>confirmedOutput(delayedWire.toString('base64'),index,41,0));
 blocks.delayed={blocks:delayedBlocks,snapshot:delayedDelivery.timestamp+'-41-'+delayedDelivery.hash+'.snap\n'+delayedRows.map(r=>[r.publicKey,r.blockId,r.txOrdinal,r.index,r.amount,r.type].join(' ')).join('\n')+'\n'};
 const exactNumbers={maximum:String(Number.MAX_SAFE_INTEGER),firstRounded:'9007199254740993',numberRoundTrip:String(Number('9007199254740993'))};



 const slips=[];for(let i=0;i<wire.readUInt32BE(0)+wire.readUInt32BE(4);i++){const o=93+i*59;slips.push({key:base58.encode(wire.subarray(o,o+33)),amount:String(wire.readBigUInt64BE(o+33)),block:String(wire.readBigUInt64BE(o+41)),ordinal:String(wire.readBigUInt64BE(o+49)),index:wire[o+57],type:wire[o+58]});}
 const removalHeight=priorFulfillment ? 23 : 21;
 const removalParent=priorFulfillment ? blockFor(priorFulfillment.wasmTransaction,22,paymentBlock.hash,1700004002000).hash : 'cd'.repeat(32);
 const removalBlock=blockFor(delisted.wasmTransaction,removalHeight,removalParent,1700004003000);
 const removalWire=Buffer.from(removalBlock.fullBase64,'base64').subarray(389);
 const removalRows=[1,0,2].map(i=>confirmedOutput(removalWire.toString('base64'),i,removalHeight,0));
 const returnedSnapshot=removalBlock.timestamp+'-'+removalHeight+'-'+removalBlock.hash+'.snap\n'+removalRows.map(r=>[r.publicKey,r.blockId,r.txOrdinal,r.index,r.amount,r.type].join(' ')).join('\n')+'\n';
 fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',sourceHashes:hashes,qualification:'Actual isolated official createDelistAssetTransaction with actual JS/WASM transaction and slip wrappers, original reference offer and assigned custody slips. Controlled wallet/input-copy adapters; no full initialized frontend or funded consensus delist.'},seller:listing.seller,store,nftId:listing.nftId,block:removalBlock,returnedSnapshot,listing:listing.listing,purchase:signed,priorFulfillment:priorFulfillment ? envelope(priorFulfillment.wasmTransaction) : null,row,delist:envelope(delisted.wasmTransaction)},null,2)+'\n');
})().catch(error=>{console.error(error);process.exitCode=1});
