// Independent official Store route/category/read-request bodies. No network or publication.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
if(process.argv.length!==6)throw Error('Usage: node store-catalogue-reference.cjs upstream acorn.js deployed-bundle output');
const root=path.resolve(process.argv[2]),parser=path.resolve(process.argv[3]),bundle=path.resolve(process.argv[4]);
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const hashes={
 'node/mods/store/store.js':'5ef51f3d2ca95313fe55f60429f5afea1ca56c0f1be80fe18a4d5cf331652a3f',
 'node/mods/store/lib/categories.js':'54aa22f5b282a2564b9f38bd598c3b383eba034be3cd692ac33f98a0d76aeb9d',
 'node/mods/store/lib/ui/browse-listings.js':'c3d52169e4fd27423c0e40e3c6c812d83833dbc2aec54a2b4b30c8946fb93b00'
};
for(const [name,expected] of Object.entries(hashes))if(sha(fs.readFileSync(path.join(root,name)))!==expected)throw Error('Wrong pinned source: '+name);
if(sha(fs.readFileSync(parser))!=='b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df')throw Error('Wrong acorn8.19.0');
if(sha(fs.readFileSync(bundle))!=='bde5468da85e77160e832cc398a0d4c449a29258d8c613e73bb7e6a7f54d6e14')throw Error('Unexpected deployed bundle');
const acorn=require(parser),categories=require(path.join(root,'node/mods/store/lib/categories.js'));
const text=fs.readFileSync(path.join(root,'node/mods/store/store.js'),'utf8');
const method=acorn.parse(text,{ecmaVersion:'latest'}).body.find(n=>n.type==='ClassDeclaration'&&n.id.name==='Store').body.body.find(n=>n.key?.name==='returnStoreRouteFromPath');
const pinned=vm.runInNewContext('({'+text.slice(method.start,method.end)+'})',{URLSearchParams,mapNFTTypeToCategory:categories.mapNFTTypeToCategory}).returnStoreRouteFromPath;
const deployed=fs.readFileSync(bundle,'utf8'),marker='key:"returnStoreRouteFromPath",value:';
const pos=deployed.indexOf(marker);if(pos<0)throw Error('Missing deployed route');
const parsed=acorn.parseExpressionAt(deployed,pos+marker.length,{ecmaVersion:'latest'});
const live=vm.runInNewContext('('+deployed.slice(parsed.start,parsed.end)+')',{URLSearchParams,R:categories.mapNFTTypeToCategory,a:(...rows)=>Object.assign(...rows)});
const fixture=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../core/src/test/resources/native-vault-reference.json'),'utf8'));
const seller=fixture.owner,nftId=fixture.id,signature=fixture.key.id;
const urls=['https://saito.io/store','https://saito.io/store/'+seller,'https://saito.davidengelen.nl/store/'+seller+'/'+nftId,'https://saito.io/store/listing/'+signature,...['image','token','css','js','stack','nwasm-nft-mod','saito-app','vault-nft-key','vault-nft-rental','store-nft-rental','text','json','unknown'].map(type=>'https://saito.io/store?type='+type)];
const context={slug:'store',app:{BROWSER:false}};
const routes=urls.map(url=>{const location=new URL(url),expected=pinned.call(context,location),actual=live.call(context,location);if(JSON.stringify(expected)!==JSON.stringify(actual))throw Error('Deployed route differs: '+url);return {url,expected};});
const requestSource=fs.readFileSync(path.join(root,'node/mods/store/lib/ui/browse-listings.js'),'utf8'),moduleScope={exports:{}};
vm.runInNewContext(requestSource,{require:name=>{if(name==='../categories')return categories;if(name==='../summary')return class {constructor(a,m,data){Object.assign(this,data)}};throw Error('Unexpected dependency')},module:moduleScope,Promise,Error});
(async()=>{
 const requests=[];
 for(const options of [{},{public_key:seller,category:'Access Keys',offset:24,page_size:24},{category:'Image NFTs',offset:48,page_size:999,status:'sold'}]){
  const app={network:{sendRequestAsTransaction:(request,data,callback,peer)=>{requests.push({options,request,data,peer});callback({listings:[],public_key:data.public_key,category:data.category,pagination:{offset:data.offset,page_size:data.page_size,total:0,total_pages:0,page:1,has_next:false,has_previous:false}})}}};
  await moduleScope.exports.loadListingsPage(app,{store_public_key:seller},options);
 }
 const output={authority:{source:'0db0062d873369dc013046ed77c97410b5683b64',sourceHashes:hashes,deployedBundleSha256:sha(fs.readFileSync(bundle)),qualification:'Actual isolated pinned and deployed route bodies agree; actual pinned browse builder captures request bodies. Network/Summary adapters are not a full initialized frontend or signed listing/consensus proof.'},seller,nftId,signature,categories:categories.STORE_CATEGORY_LIST,routes,requests};
 fs.writeFileSync(path.resolve(process.argv[5]),JSON.stringify(output,null,2)+'\n');
})().catch(error=>{console.error(error);process.exitCode=1});
