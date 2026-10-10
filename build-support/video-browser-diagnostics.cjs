// Read-only diagnostic for the isolated .dev public-video run, never an acceptance oracle.
// Inspect only the installed app's wrapper and the documented public YouTube example.
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const directory = process.argv[2];
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
      (url.origin === 'https://www.youtube.com' && url.pathname === '/embed/M7lc1UVf-VE');
  } catch { return false; }
};

async function inspect(target) {
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
  const call = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++next;
    const timer = setTimeout(() => {pending.delete(id);reject(Error('CDP command timed out'));}, 3000);
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
    if (frames.length) {
      const screenshot = await call('Page.captureScreenshot', {format:'png'});
      if (screenshot.data && screenshot.data.length < 4*1024*1024) {
        fs.writeFileSync(path.join(directory, 'video-browser-last.png'), Buffer.from(screenshot.data,'base64'));
        output({browserScreenshot:'video-browser-last.png'});
      }
    }
  } finally {
    socket.close();
    activeSocket = undefined;
    for(const request of pending.values()){clearTimeout(request.timer);request.reject(Error('CDP disconnected'));}
  }
}

async function main() {
for(let attempt=0; attempt<12 && !stopped; attempt++) {
  let port;
  let stage = 'app-process';
  try {
    const pid = execFileSync('adb',['shell','pidof','nl.davidengelen.redsquare.dev'],{encoding:'utf8',timeout:3000}).trim();
    if (!/^\d+$/.test(pid)) throw Error('No isolated app process');
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
    output({pageTargets:pages.length,allowedTargetUrls:pages.filter(t=>allowed(t.url)).length});
    for(const target of pages.slice(0,1)) {
      stage = 'local-endpoint';
      const endpoint = new URL(target.webSocketDebuggerUrl);
      if (!['127.0.0.1','localhost'].includes(endpoint.hostname) || endpoint.protocol!=='ws:' ||
          !endpoint.pathname.startsWith('/devtools/page/')) throw Error('Unexpected diagnostic endpoint');
      endpoint.hostname='127.0.0.1'; endpoint.port=port;
      target.webSocketDebuggerUrl=endpoint.toString();
      stage = 'frame-layout';
      await inspect(target);
    }
  } catch { output({diagnostic:'Player debug endpoint not ready or read failed',stage}); }
  finally {if(port)removeForward(port);ownedPort=undefined;}
  if (!stopped) await sleep(5000);
}
}
main().catch(() => {output({diagnostic:'Diagnostic runner failed'});process.exitCode=1;});
