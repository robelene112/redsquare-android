// Pure official rental builders/hooks and actual WASM decisions. Public unsafe identities only.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
if(process.argv.length!==6)throw Error('Usage: node vault-rental-reference.cjs runtime upstream acorn.js output');
const runtime=path.resolve(process.argv[2]),root=path.resolve(process.argv[3]),parser=path.resolve(process.argv[4]);
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const hashes={
 'node/mods/store/store.js':'5ef51f3d2ca95313fe55f60429f5afea1ca56c0f1be80fe18a4d5cf331652a3f',
 'node/mods/vault/vault.js':'ec63c8744018f6021ed3c93ddeb026d03d3f06988b7ba819a1a7d005847e2cc4',
 'node/mods/vault/lib/transactions/rental-checkout.js':'51853d4eabb6f6432056a47410d9701ab00d61bff037318e118b2c20d5ef3453',
 'node/mods/vault/lib/contracts/rental.js':'c0385317ee08e703bd17b9a9655c142bf1179f3336cd98485a95b792abc3b8e8',
 'node/mods/vault/lib/contracts/loan.js':'870877c0cb6b5926023a9e3e624ebee35cbdb12c82bec9a3eb4fb19aff328156'
};
for(const [name,expected]of Object.entries(hashes))if(sha(fs.readFileSync(path.join(root,name)))!==expected)throw Error('Wrong pinned source: '+name);
if(sha(fs.readFileSync(parser))!=='b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df')throw Error('Wrong acorn8.19.0 parser');
const acorn=require(parser),wasm=require(path.join(runtime,'node_modules/saito-wasm/pkg/node'));
if(require(path.join(runtime,'node_modules/saito-wasm/package.json')).version!=='0.3.15')throw Error('Wrong WASM');
const load=name=>fs.readFileSync(path.join(root,name),'utf8');
function method(name,className,methodName){const text=load(name),tree=acorn.parse(text,{ecmaVersion:'latest'});const node=tree.body.find(n=>n.type==='ClassDeclaration'&&n.id.name===className).body.body.find(n=>n.key?.name===methodName);return text.slice(node.start,node.end)}
function fn(name,functionName){const text=load(name),node=acorn.parse(text,{ecmaVersion:'latest'}).body.find(n=>n.type==='FunctionDeclaration'&&n.id.name===functionName);return text.slice(node.start,node.end)}
const mint=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-vault-mint-reference.json'),'utf8'));
const actors=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-vault-reference.json'),'utf8'));
const file=mint.advanced.find(x=>x.type==='rental').file,fileId=file.id;
let scalar='0'.repeat(63)+'2',clock=1700001000000;
const creator=wasm.generate_public_key(scalar),custodian=wasm.generate_public_key('0'.repeat(63)+'3'),renter=wasm.generate_public_key('0'.repeat(63)+'1'),next=wasm.generate_public_key('0'.repeat(63)+'4');
const master=require(path.join(root,'node/mods/vault/lib/contracts/rental.js')).build({creator_publickey:creator});
const expiresAt=1700001001000;
const globals={Buffer,loan:require(path.join(root,'node/mods/vault/lib/contracts/loan.js')),Date:{now:()=>clock},console:{log(){},warn(){},error(){}},siteMessage(){}};
const hook=vm.runInNewContext('({'+method('node/mods/store/store.js','Store','respondTo')+'})',globals).respondTo.call({app:{
 crypto:{hash:v=>wasm.hash(Buffer.from(v)),signMessage:(v,key)=>wasm.sign_buffer(Buffer.from(v),key)},wallet:{getPrivateKey:async()=>scalar}
}},'saito-nft-transfer').onTransfer;
const helpers=fn('node/mods/vault/vault.js','findCheckPathHop')+'\n'+fn('node/mods/vault/vault.js','firstUndelegatedHopFromRental');
const selector=vm.runInNewContext(helpers+'\nfirstUndelegatedHopFromRental',globals);
(async()=>{
 const tx={msg:{data:{file_id:fileId,expires_at:expiresAt}}};
 await hook(null,tx,custodian,{delegated:true});scalar='0'.repeat(63)+'3';clock++;
 await hook(null,tx,renter,{});
 const hops=JSON.parse(JSON.stringify(tx.msg.data.path));
 const selected=JSON.parse(JSON.stringify(selector(master,hops)));
 const loan=require(path.join(root,'node/mods/vault/lib/contracts/loan.js')).instantiate({creator_publickey:creator,renter_publickey:selected.renter,expires_at:selected.expires_at});
 const loanHash=wasm.get_script_hash(JSON.stringify(loan));
 const witnessed=JSON.parse(JSON.stringify(master));witnessed.args[1].args[0].witness={hops};
 const pathAccepted=await wasm.evaluate_script(JSON.stringify(witnessed.args[1].args[0]));
 if(pathAccepted!==1)throw Error('Official signed path rejected');
 const updates=[];
 for(const owner of [loanHash,'00'.repeat(32)])updates.push({owner,accepted:await wasm.evaluate_script(JSON.stringify(witnessed),JSON.stringify({db:{type:'UPDATE',owner,updated_at:clock}}))});
 if(updates[0].accepted!==1||updates[1].accepted!==0)throw Error('Unexpected DB constitution');
 // Execute the actual unsigned-message builders; only their body shape is claimed.
 const checkoutBuilder=vm.runInNewContext('('+fn('node/mods/vault/lib/transactions/rental-checkout.js','createCheckOutRentalTransaction')+')',globals);
 const checkout=await checkoutBuilder.call({app:{wallet:{createUnsignedTransaction:async()=>({msg:null,sign:async()=>{}})}}},{msg:{data:{file_id:fileId,path:hops,file_access_script:master}}});
 let accessMessage;
 const accessBuilder=vm.runInNewContext(helpers+'\n({'+method('node/mods/vault/vault.js','Vault','sendAccessRentalFileRequest')+'})',globals).sendAccessRentalFileRequest;
 await accessBuilder.call({peer:{publicKey:custodian},app:{core:{scripting:{hash:s=>wasm.get_script_hash(s)}},network:{sendRequestAsTransaction:(request,data)=>{accessMessage={request,data}}}}},{data:{file_id:fileId,path:hops,file_access_script:master}});
 if(!accessMessage)throw Error('No actual access payload');
 // Verify FIRST undelegated selection survives a later signed delegation.
 scalar='0'.repeat(63)+'1';clock++;await hook(null,tx,next,{delegated:true});
 const extended=JSON.parse(JSON.stringify(tx.msg.data.path)),extendedSelection=JSON.parse(JSON.stringify(selector(master,extended)));
 if(JSON.stringify(extendedSelection)!==JSON.stringify(selected))throw Error('First rental changed');
 const actorsTx=[wasm.WasmTransaction.deserialize(Buffer.from(actors.custom.request.networkBase64,'base64')),wasm.WasmTransaction.deserialize(Buffer.from(actors.file.networkBase64,'base64'))];
 const masterCreatorAccepted=await wasm.evaluate_script_with_transaction(JSON.stringify(master),actorsTx[0]);
 const masterOtherAccepted=await wasm.evaluate_script_with_transaction(JSON.stringify(master),actorsTx[1]);
 if(masterCreatorAccepted!==1||masterOtherAccepted!==0)throw Error('Unexpected original master actor rights');
 const roles=[],oldNow=Date.now;Date.now=()=>clock;
 try{for(const time of [1700001000000,expiresAt,expiresAt+1]){clock=time;roles.push({time,creator:await wasm.evaluate_script_with_transaction(JSON.stringify(loan),actorsTx[0]),renter:await wasm.evaluate_script_with_transaction(JSON.stringify(loan),actorsTx[1])})}}finally{Date.now=oldNow}
 if(JSON.stringify(roles.map(r=>[r.creator,r.renter]))!==JSON.stringify([[0,1],[0,0],[1,0]]))throw Error('Unexpected actual time rights');
 const bad=JSON.parse(JSON.stringify(hops));bad[0].sig='00'.repeat(64);
 const invalidScript={...master.args[1].args[0],witness:{hops:bad}};
 const invalidAccepted=await wasm.evaluate_script(JSON.stringify(invalidScript));if(invalidAccepted!==0)throw Error('Changed signature accepted');
 const output={authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',saitoWasm:'0.3.15',acorn:'8.19.0',sourceHashes:hashes,
 qualification:'Actual isolated official transfer hook/message-builder bodies and WASM path/DB/role decisions. No initialized client, signed request wire, live provider or consensus acceptance.'},creator,custodian,renter,next,fileId,file,contentsBase64:mint.contentsBase64,
 masterSource:JSON.stringify(master),masterHash:wasm.get_script_hash(JSON.stringify(master)),hops,selected,loanSource:JSON.stringify(loan),loanHash,pathAccepted,updates,checkoutMessage:checkout.msg,accessMessage,extended,extendedSelection,roles,masterCreatorAccepted,masterOtherAccepted,invalidHops:bad,invalidAccepted};
 fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify(output,null,2)+'\n');console.log('Independent rental path, bodies and actual rights generated');
})().catch(e=>{console.error(e);process.exitCode=1});
