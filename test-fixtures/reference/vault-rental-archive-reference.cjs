// Actual pinned Archive.updateTransaction, real SQLite and WASM. Published unsafe fixtures only.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto'),{DatabaseSync}=require('node:sqlite');
if(![6,7].includes(process.argv.length))throw Error('Usage: node vault-rental-archive-reference.cjs runtime upstream acorn output');
const compatible=process.argv[6]==='--compatible';
const [runtime,root,parser,output]=process.argv.slice(2,6).map(p=>path.resolve(p));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const code=fs.readFileSync(path.join(root,'node/mods/archive/archive.js'),'utf8');
if(sha(code)!==(compatible?'0e539daaed9c4784df5b37626f58ec966fe096d49400b24d44dc81cb5173f131':'1f29d71bfc90d417c8feb21e1baf63ed6aab819fa6cb5e4fc0aaf9b4069c828e'))throw Error('Wrong Archive source');
if(sha(fs.readFileSync(parser))!=='b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df')throw Error('Wrong parser');
const acorn=require(parser),wasm=require(path.join(runtime,'node_modules/saito-wasm/pkg/node'));
if(require(path.join(runtime,'node_modules/saito-wasm/package.json')).version!=='0.3.15')throw Error('Wrong WASM');
const fixture=name=>JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/'+name+'.json'),'utf8'));
const rental=fixture('native-vault-rental-reference'),actors=fixture('native-vault-reference');
const creator=wasm.WasmTransaction.deserialize(Buffer.from(actors.custom.request.networkBase64,'base64'));
const renter=wasm.WasmTransaction.deserialize(Buffer.from(actors.file.networkBase64,'base64'));
const master=JSON.parse(rental.masterSource);master.args[1].args[0].witness={hops:rental.hops};
let clock=1700001000000;
const Clock=class extends Date {constructor(){super(clock)}static now(){return clock}};
const n=acorn.parse(code,{ecmaVersion:'latest'}).body.find(n=>n.type==='ClassDeclaration'&&n.id.name==='Archive').body.body.find(n=>n.key?.name==='updateTransaction');
const update=vm.runInNewContext('({'+code.slice(n.start,n.end)+'})',{Date:Clock,console:{log(){},warn(){},error(){}}}).updateTransaction;
const db=new DatabaseSync(':memory:');db.exec('CREATE TABLE archives(sig TEXT PRIMARY KEY,owner TEXT NOT NULL,tx TEXT NOT NULL,updated_at INTEGER)');
const file=rental.file.envelope;
db.prepare('INSERT INTO archives VALUES(?,?,?,?)').run(rental.fileId,rental.masterHash,file,clock);
const archive={schema:['sig','owner','tx','updated_at'],app:{BROWSER:0,storage:{
 queryDatabase:async(sql,params)=>db.prepare(sql).all(params),runDatabase:async(sql,params)=>db.prepare(sql).run(params)
},core:{scripting:{hash:s=>wasm.get_script_hash(typeof s==='string'?s:JSON.stringify(s)),evaluateWithTransaction:async(s,tx,context)=>{
 const old=Date.now;Date.now=()=>clock;
 try{return await wasm.evaluate_script_with_transaction(typeof s==='string'?s:JSON.stringify(s),tx._wasm||tx,JSON.stringify(context))}finally{Date.now=old}
}}}}};
const row=()=>db.prepare('SELECT * FROM archives WHERE sig=?').get(rental.fileId);
(async()=>{
 const first=await update.call(archive,null,{sig:rental.fileId,owner:rental.loanHash,access_script:JSON.stringify(master),request_tx:renter});
 if(first!==1||row().owner!==rental.loanHash||row().tx!==file)throw Error('Initial actual Archive rental transition failed');
 const repeated=await update.call(archive,null,{sig:rental.fileId,owner:rental.loanHash,access_script:JSON.stringify(master),request_tx:renter});
 if(repeated!==0)throw Error('Pinned master replay unexpectedly succeeded');
 const earlyCreator=await update.call(archive,null,{sig:rental.fileId,owner:rental.masterHash,access_script:rental.loanSource,request_tx:creator});
 if(earlyCreator!==0||row().owner!==rental.loanHash)throw Error('Creator reset before actual expiry');
 clock=rental.selected.expires_at+1;
 const expiredRenter=await update.call(archive,null,{sig:rental.fileId,owner:rental.masterHash,access_script:rental.loanSource,request_tx:renter});
 if(expiredRenter!==0)throw Error('Expired renter reset accepted');
 const restoredCreator=await update.call(archive,null,{sig:rental.fileId,owner:rental.masterHash,access_script:rental.loanSource,request_tx:creator});
 if(restoredCreator!==1||row().owner!==rental.masterHash||row().tx!==file)throw Error('Actual expired creator restoration failed');
 let compatibility=null;
 if(compatible){
  const receiverCode=fs.readFileSync(path.join(root,'node/mods/vault/lib/transactions/rental-checkout.js'),'utf8');
  if(sha(receiverCode)!=='03b8e633982f34447d92c400e432d883efdd569d267549306e6d6f81616370af')throw Error('Wrong compatible receiver');
  const fn=acorn.parse(receiverCode,{ecmaVersion:'latest'}).body.find(n=>n.type==='FunctionDeclaration'&&n.id.name==='receiveRestoreRentalTransaction');
  const loan=require(path.join(root,'node/mods/vault/lib/contracts/loan.js')),rentalContract=require(path.join(root,'node/mods/vault/lib/contracts/rental.js'));
  const receiver=vm.runInNewContext('('+receiverCode.slice(fn.start,fn.end)+')',{loan,rental:rentalContract});
  archive.app.storage.updateTransaction=(tx,data)=>update.call(archive,tx,data);
  async function restore(actor,publicKey,expiry){let reply;await receiver.call(archive,{from:[{publicKey}],_wasm:actor,returnMessage:()=>({data:{file_id:rental.fileId,renter:rental.renter,expires_at:expiry}})},r=>{reply=JSON.parse(JSON.stringify(r))});return reply}
  db.prepare('UPDATE archives SET owner=?').run(rental.loanHash);clock=rental.selected.expires_at-1;
  const deniedBefore=await restore(creator,rental.creator,rental.selected.expires_at);
  clock++;const deniedAt=await restore(creator,rental.creator,rental.selected.expires_at);
  clock++;const deniedRenter=await restore(renter,rental.renter,rental.selected.expires_at);
  const deniedOtherLoan=await restore(creator,rental.creator,rental.selected.expires_at+100);
  const restored=await restore(creator,rental.creator,rental.selected.expires_at);
  const repeatedRestore=await restore(creator,rental.creator,rental.selected.expires_at);
  if([deniedBefore,deniedAt,deniedRenter,deniedOtherLoan].some(r=>r.status!=='err')||restored.status!=='ok'||restored.already_restored!==false||repeatedRestore.already_restored!==true)throw Error('Compatible restore authority/idempotency failed');
  // Both callers observe the same old owner before actual asynchronous script evaluation.
  const competingHash=wasm.get_script_hash(JSON.stringify(loan.instantiate({creator_publickey:rental.creator,renter_publickey:rental.next,expires_at:rental.selected.expires_at+1000})));
  db.prepare('UPDATE archives SET owner=?').run(rental.masterHash);
  const race=await Promise.all([rental.loanHash,competingHash].map(owner=>update.call(archive,null,{sig:rental.fileId,owner,expected_owner:rental.masterHash,access_script:JSON.stringify(master),request_tx:creator})));
  if(JSON.stringify(race.slice().sort())!=='[0,1]')throw Error('Concurrent rental owner transition was not exclusive: '+JSON.stringify(race));
  const finalOwner=row().owner;
  // A stale reset may not replace a newly activated loan.
  const stale=await update.call(archive,null,{sig:rental.fileId,owner:rental.masterHash,expected_owner:rental.masterHash,access_script:JSON.stringify(master),request_tx:creator});
  if(stale!==0||row().owner!==finalOwner||row().tx!==file)throw Error('Stale owner transition rewrote the newer loan or original file');
  compatibility={receiverSha256:sha(receiverCode),deniedBefore,deniedAt,deniedRenter,deniedOtherLoan,restored,repeatedRestore,concurrentResults:race,staleReset:stale};
 }
 const result={authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',archiveSha256:sha(code),saitoWasm:'0.3.15',sqlite:db.prepare('select sqlite_version() AS v').get().v,qualification:'Actual isolated Archive.updateTransaction method, actual SQLite with the four used columns, actual WASM requester/NOW/DB evaluation. No initialized Vault/Archive frontend, live provider or consensus. Compatible variant additionally executes the new restore receiver with an explicit request/WASM-actor adapter and conditional actual Archive SQL.'},compatibility,fileId:rental.fileId,first,repeated,earlyCreator,expiredRenter,restoredCreator,originalSignedFileUnchanged:row().tx===file};
 fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');db.close();console.log('Actual Archive confirms replay denial and expiry-authorized creator restoration');
})().catch(e=>{console.error(e);process.exitCode=1});
