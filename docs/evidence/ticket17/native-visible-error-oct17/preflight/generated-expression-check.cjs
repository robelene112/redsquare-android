const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('/diagnostic.cjs','utf8');
const start=source.indexOf('const arm = await call(');
const a=source.indexOf('expression:`',start)+'expression:'.length;
const b=source.indexOf('})()`});',a)+'})()`'.length;
const expression=vm.runInNewContext(source.slice(a,b),{request:{videoId:'bHQqvYy5KYo'}});
new vm.Script(expression);
async function check({hidden=false,clipped=false,deferred=false,long=false}={}){
 let reads=0,disconnected=0,cancelled=0,changed,timed;
 const parent={parentElement:null,getBoundingClientRect:()=>({left:200,top:200,right:300,bottom:300}),style:{opacity:hidden?'0':'1',overflowX:clipped?'hidden':'visible',overflowY:clipped?'hidden':'visible'}};
 const node={get textContent(){reads++;return long?'s'.repeat(600):'Please  sign in\n session';},
  parentElement:parent,getClientRects:()=>[{}],getBoundingClientRect:()=>({x:1,y:1,left:1,top:1,right:101,bottom:31,width:100,height:30})};
 let present=!deferred;
 const sandbox={location:{origin:'https://www.youtube.com',pathname:'/embed/bHQqvYy5KYo'},
  document:{querySelector:s=>present&&s.endsWith('-reason')?node:null,documentElement:{}},
  getComputedStyle:n=>({display:'block',visibility:'visible',opacity:'1',...n.style}),innerWidth:400,innerHeight:300,
  MutationObserver:class{constructor(callback){changed=callback;}observe(){}disconnect(){disconnected++;}},
  setTimeout:callback=>{timed=callback;return 1;},clearTimeout:()=>cancelled++,Date};
 assert.equal(vm.runInNewContext(expression,sandbox).armed,true);
 if(hidden||clipped){assert.equal(reads,0);sandbox.rsPublicProviderError.close();}
 else if(deferred){present=true;changed();}
 const result=await sandbox.rsPublicProviderError.promise;
 assert.equal(disconnected,1);assert.equal(cancelled,1);
 assert.equal(result.videoId,'bHQqvYy5KYo');
 if(hidden||clipped)assert.equal(result.state,'stopped');
 else{assert.equal(result.reason.text,long?'s'.repeat(512):'Please sign in session');assert.equal(result.phase,deferred?'after-arm':'at-arm');}
}
(async()=>{for(const options of [{},{deferred:true},{hidden:true},{clipped:true},{long:true}])await check(options);
 console.log('PASS: generated expression syntax; initial/mutation visible text; whitespace and s preservation; hidden/clipped ancestors read no text; 512-character bound; observer/timer cleanup. Controlled DOM only, no browser/provider playback claim.');
})().catch(e=>{console.error(e);process.exitCode=1;});
