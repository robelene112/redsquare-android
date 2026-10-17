// Read-only diagnostic for the isolated .dev public-video run, never an acceptance oracle.
// Inspect only the installed app's wrapper and the documented public YouTube example.
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const directory = process.argv[2];
const providerError = process.argv[3] === "--provider-error";
if (!directory || typeof WebSocket !== 'function') throw Error('Node with native WebSocket and evidence directory required');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const output = value => fs.appendFileSync(path.join(directory, 'video-browser.jsonl'), JSON.stringify({time:new Date().toISOString(), ...value})+'\n');
let stopped = false;
let ownedPort;
let activeSocket;
function removeForward(port) {
  try {execFileSync('adb',['forward','--remove',`tcp:${port}`],{timeout:3000,stdio:'ignore'});} catch { }
}
process.once('SIGTERM', () => {
  stopped = true;
  try {activeSocket?.close();} catch { }
  if (ownedPort) {removeForward(ownedPort);ownedPort=undefined;}
});
const allowed = value => {
  try {
    const url = new URL(value);
    return url.origin === 'https://nl.davidengelen.redsquare.dev' ||
      (url.origin === 'https://www.youtube.com' && ['/embed/M7lc1UVf-VE','/embed/bHQqvYy5KYo'].includes(url.pathname));
  } catch { return false; }
};

async function inspect(target, request) {
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  activeSocket = socket;
  let next = 0;
  const pending = new Map();
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    const request = pending.get(message.id);
    if (!request) return;
    pending.delete(message.id); clearTimeout(request.timer);
    if (message.error) request.reject(Error('CDP command failed')); else request.resolve(message.result);
  });
  const disconnected = () => {for (const request of pending.values()) {clearTimeout(request.timer);request.reject(Error('CDP disconnected'));} pending.clear();};
  socket.addEventListener('close', disconnected);
  const call = (method, params = {}, timeout = 3000) => new Promise((resolve, reject) => {
    const id = ++next;
    const timer = setTimeout(() => {pending.delete(id);reject(Error('CDP command timed out'));}, timeout);
    pending.set(id, {resolve,reject,timer});
    socket.send(JSON.stringify({id,method,params}));
  });
  try {
    await new Promise((resolve,reject) => {
      const timer = setTimeout(() => reject(Error('CDP connection timed out')), 3000);
      socket.addEventListener('open', () => {clearTimeout(timer);resolve();}, {once:true});
      socket.addEventListener('error', () => {clearTimeout(timer);reject(Error('CDP connection failed'));}, {once:true});
    });
    const tree = await call('Page.getFrameTree');
    const frames = [];
    const visit = entry => { if (allowed(entry.frame.url)) frames.push(entry.frame); for(const child of entry.childFrames || []) visit(child); };
    visit(tree.frameTree);
    if (providerError) {
      const frame = frames.find(frame => {
        const url = new URL(frame.url);
        return url.origin === 'https://www.youtube.com' && url.pathname === `/embed/${request.videoId}`;
      });
      if (!frame) throw Error('Selected public embed is not ready');
      const world = await call('Page.createIsolatedWorld', {frameId:frame.id, worldName:'public-video-error-diagnostic'});
      const arm = await call('Runtime.evaluate', {contextId:world.executionContextId, returnByValue:true, expression:`(() => {
        const videoId=${JSON.stringify(request.videoId)};
        if(location.origin!=='https://www.youtube.com'||location.pathname!=='/embed/'+videoId)return {armed:false};
        let observer,timer,finish;
        const promise=new Promise(resolve=>{finish=value=>{observer?.disconnect();clearTimeout(timer);resolve(value);};});
        const read=selector=>{
          const node=document.querySelector(selector);if(!node)return null;
          const r=node.getBoundingClientRect(),style=getComputedStyle(node);
          if(!node.getClientRects().length||r.width<=0||r.height<=0||r.right<=0||r.bottom<=0||r.x>=innerWidth||r.y>=innerHeight||style.display==='none'||style.visibility!=='visible'||style.opacity==='0')return null;
          const text=(node.textContent||'').replace(/\s+/g,' ').trim().slice(0,512);
          return text?{text,box:{x:r.x,y:r.y,width:r.width,height:r.height}}:null;
        };
        const inspect=phase=>{
          const reason=read('.ytp-error-content-wrap-reason'),subreason=read('.ytp-error-content-wrap-subreason');
          if(reason||subreason)finish({state:'observed',phase,videoId,time:Date.now(),reason,subreason});
        };
        observer=new MutationObserver(()=>inspect('after-arm'));
        observer.observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true});
        timer=setTimeout(()=>finish({state:'timeout',videoId}),60000);
        globalThis.rsPublicProviderError={promise,close:()=>finish({state:'stopped',videoId})};
        inspect('at-arm');return {armed:true,videoId};
      })()`});
      if (arm.result?.value?.armed !== true || arm.result.value.videoId !== request.videoId) throw Error('Observer not installed');
      const current = readRequest(request.pid);
      if (current.nonce !== request.nonce) throw Error('Fixture changed during collection');
      execFileSync('adb',['shell','-T','run-as','nl.davidengelen.redsquare.dev','tee','cache/video-evidence/provider-observer-ready.json'],
        {input:JSON.stringify(request),timeout:3000,maxBuffer:1024,stdio:['pipe','pipe','pipe']});
      const captured = await call('Runtime.evaluate', {contextId:world.executionContextId,returnByValue:true,awaitPromise:true,
        expression:'globalThis.rsPublicProviderError.promise'},65000);
      const value = captured.result?.value;
      if (!value || !['observed','timeout','stopped'].includes(value.state) || value.videoId !== request.videoId || JSON.stringify(value).length>4096) throw Error('Unexpected error observation');
      for (const field of ['reason','subreason']) if(value[field] && (typeof value[field].text!=='string'||value[field].text.length>512)) throw Error('Unbounded text');
      output({providerError:value});
      return true;
    }
    output({allowedFrames:frames.length});
    for (const frame of frames.slice(0, 3)) {
      const world = await call('Page.createIsolatedWorld', {frameId:frame.id, worldName:'public-video-layout-diagnostic'});
      const result = await call('Runtime.evaluate', {contextId:world.executionContextId, returnByValue:true, expression:`(() => {
        const box = node => {if(!node)return null;const r=node.getBoundingClientRect();const s=getComputedStyle(node);return {x:r.x,y:r.y,width:r.width,height:r.height,display:s.display,visibility:s.visibility};};
        return {origin:location.origin,ready:document.readyState,viewport:[innerWidth,innerHeight],elements:document.querySelectorAll('*').length,
          player:box(document.getElementById('player')),body:box(document.body),
          frames:Array.from(document.querySelectorAll('iframe')).slice(0,3).map(box),
          controls:Array.from(document.querySelectorAll('button,[role=button]')).slice(0,30).map(n=>({label:(n.getAttribute('aria-label')||n.textContent||'').trim().slice(0,80),box:box(n)})),
          videos:Array.from(document.querySelectorAll('video')).slice(0,3).map(n=>({box:box(n),paused:n.paused,readyState:n.readyState,videoWidth:n.videoWidth,videoHeight:n.videoHeight}))};
      })()`});
      if (result.result?.value) output({frame:result.result.value});
    }
  } finally {
    socket.close();
    activeSocket = undefined;
    disconnected();
  }
}

function readRequest(pid) {
  const bytes=execFileSync('adb',['shell','run-as','nl.davidengelen.redsquare.dev','cat','cache/video-evidence/provider-observer-request.json'],{encoding:'utf8',timeout:3000,maxBuffer:1024});
  if(bytes.length>512)throw Error('Unexpected fixture request');
  const request=JSON.parse(bytes);
  if(Object.keys(request).sort().join(',')!=='nonce,pid,version,videoId'||request.version!==1||request.pid!==Number(pid)||
    !['M7lc1UVf-VE','bHQqvYy5KYo'].includes(request.videoId)||typeof request.nonce!=='string'||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(request.nonce))throw Error('Unexpected fixture request');
  return request;
}
async function main() {
if(providerError && !execFileSync('adb',['get-serialno'],{encoding:'utf8',timeout:3000}).trim().startsWith('emulator-'))throw Error('Isolated emulator required');
const deadline=Date.now()+120000;
let lastStage='',lastFailure=0;
for(let attempt=0; (providerError?Date.now()<deadline:attempt<12) && !stopped; attempt++) {
  let port;
  let stage = 'app-process';
  try {
    const pid = execFileSync('adb',['shell','pidof','nl.davidengelen.redsquare.dev'],{encoding:'utf8',timeout:3000}).trim();
    if (!/^\d+$/.test(pid)) throw Error('No isolated app process');
    stage = 'fixture-request';
    const request = providerError ? readRequest(Number(pid)) : undefined;
    stage = 'owned-forward';
    port = execFileSync('adb',['forward','tcp:0',`localabstract:webview_devtools_remote_${pid}`],{encoding:'utf8',timeout:3000}).trim();
    if (!/^\d+$/.test(port)) throw Error('No diagnostic port');
    ownedPort = port;
    stage = 'target-discovery';
    const response = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(3000)});
    const targets = await response.json();
    // Discovery is already restricted to this .dev process. Wrapper target URLs
    // can differ from loadDataWithBaseURL; inspect only explicitly allowed frames.
    const pages = targets.filter(t=>t.type==='page');
    if(!providerError)output({pageTargets:pages.length,allowedTargetUrls:pages.filter(t=>allowed(t.url)).length});
    for(const target of pages.slice(0,1)) {
      stage = 'local-endpoint';
      const endpoint = new URL(target.webSocketDebuggerUrl);
      if (!['127.0.0.1','localhost'].includes(endpoint.hostname) || endpoint.protocol!=='ws:' ||
          !endpoint.pathname.startsWith('/devtools/page/')) throw Error('Unexpected diagnostic endpoint');
      endpoint.hostname='127.0.0.1'; endpoint.port=port;
      target.webSocketDebuggerUrl=endpoint.toString();
      stage = 'frame-layout';
      if(await inspect(target,request) && providerError)return;
    }
  } catch { if(!providerError||stage!==lastStage||Date.now()-lastFailure>=2000) {
    output({diagnostic:'Player debug endpoint not ready or read failed',stage});lastStage=stage;lastFailure=Date.now();
  } }
  finally {if(port)removeForward(port);ownedPort=undefined;}
  if (!stopped) await sleep(providerError?200:5000);
}
}
main().catch(() => {output({diagnostic:'Diagnostic runner failed'});process.exitCode=1;});
