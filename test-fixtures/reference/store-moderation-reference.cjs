// Actual isolated Store handler/database transitions. Owned in-memory SQL IO adapter, no node or database writes.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
if(process.argv.length!==5)throw Error('Usage: node store-moderation-reference.cjs upstream acorn output');
const root=path.resolve(process.argv[2]),parser=path.resolve(process.argv[3]);
const hashes={
  "node/mods/store/lib/database.js": "0f40d0a346d0c71aa0219fff8c446a3b55eba0f0438f30d306aa05261876dc48",
  "node/mods/store/store.js": "5ef51f3d2ca95313fe55f60429f5afea1ca56c0f1be80fe18a4d5cf331652a3f"
};
for(const [file,hash]of Object.entries(hashes))if(crypto.createHash('sha256').update(fs.readFileSync(path.join(root,file))).digest('hex')!==hash)throw Error('Wrong pinned source');
if(crypto.createHash('sha256').update(fs.readFileSync(parser)).digest('hex')!=='b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df')throw Error('Wrong acorn');
const acorn=require(parser);
function method(file,name){const source=fs.readFileSync(path.join(root,file),'utf8'),klass=acorn.parse(source,{ecmaVersion:'latest'}).body.find(n=>n.type==='ClassDeclaration'),node=klass.body.body.find(n=>n.key?.name===name);return source.slice(node.start,node.end)}
const database=vm.runInNewContext('({'+method('node/mods/store/lib/database.js','submitListingForMainStore')+'})');
const handler=vm.runInNewContext('({'+method('node/mods/store/store.js','handlePeerTransaction')+'})');
(async()=>{
 const signature='1'.repeat(128),seller='pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU',cases=[];
 for(const before of [0,-1,1,2,3]){
  let state=before,writes=0;
  const db={...database,dbname:'owned-store',returnCanonicalListingBySignature:async s=>{if(s!==signature)throw Error('Wrong signature');return{seller,approved:state}},app:{storage:{runDatabase:async(sql,bindings,name)=>{
   if(!sql.includes('SET approved = 2')||bindings.$signature!==signature||name!=='owned-store')throw Error('Unexpected SQL');
   writes++;if(state===0||state===-1)state=2;
  }}}};
  let response;
  await handler.handlePeerTransaction.call({app:{BROWSER:false},warehouse:{db}},null,{from:[{publicKey:seller}],returnMessage:()=>({request:'submit-listing',data:{signature}})},null,value=>{response=value});
  if(!response)throw Error('No official callback');cases.push({before,response,after:state,writes});
 }
 fs.writeFileSync(path.resolve(process.argv[4]),JSON.stringify({authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',sourceHashes:hashes,qualification:'Actual isolated submitListingForMainStore and handlePeerTransaction with a narrowly checked in-memory SQL IO adapter. Not a running database/node or moderation provider.'},signature,seller,cases},null,2)+'\n');
})().catch(e=>{console.error(e);process.exitCode=1});
