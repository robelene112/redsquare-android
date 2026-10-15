'use strict';
// One public reference in a fresh desktop browser; no app, Saito or user profile.
const http=require('node:http'),fs=require('node:fs');
const {chromium}=require('/tools/node_modules/playwright');
const out='/evidence',id='bHQqvYy5KYo';
async function main(){
  let origin;
  const server=http.createServer((request,response)=>{
    if(request.url!=='/'){response.writeHead(404).end();return;}
    response.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Referrer-Policy':'strict-origin-when-cross-origin'});
    response.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body{margin:0;background:#000}#player{width:296px;height:472px}iframe{border:0;display:block}</style></head><body><div id="player"></div><script>
window.rsVideo={state:'loading',code:0};function onYouTubeIframeAPIReady(){window.rsPlayer=new YT.Player('player',{width:'296',height:'472',videoId:'${id}',playerVars:{autoplay:0,playsinline:1,origin:'${origin}'},events:{onReady:function(e){if(window.rsVideo.state==='error')return;try{e.target.cueVideoById('${id}');if(window.rsVideo.state!=='error')window.rsVideo={state:'ready',code:0};}catch(err){window.rsVideo={state:'error',code:5};}},onStateChange:function(e){if(window.rsVideo.state==='error')return;if(e.data===1)window.rsVideo={state:'playing',code:0};else if(e.data===0||e.data===2||e.data===5)window.rsVideo={state:'ready',code:0};},onError:function(e){window.rsVideo={state:'error',code:e.data};}}});}</script><script src="https://www.youtube.com/iframe_api" onerror="window.rsVideo={state:'error',code:-1}"></script></body></html>`);
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));origin=`http://127.0.0.1:${server.address().port}`;
  const result={observedAt:new Date().toISOString(),reference:id,scope:'Fresh desktop Chromium/local HTTP origin, NOT Android app or WebView acceptance',wrapperOrigin:origin,mediaRequestsRewritten:false,appOrWalletAccess:false,autoplay:false,embedRequests:[]};
  let browser,context,page;
  try{
    browser=await chromium.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});result.browserVersion=browser.version();
    context=await browser.newContext({viewport:{width:320,height:640},locale:'en-US'});page=await context.newPage();
    page.on('request',request=>{const url=new URL(request.url());if(url.origin==='https://www.youtube.com'&&url.pathname===`/embed/${id}`&&result.embedRequests.length<4){const referer=request.headers().referer;let referrerOrigin=null;try{referrerOrigin=new URL(referer).origin;}catch{}result.embedRequests.push({origin:url.origin,path:url.pathname,referrerOrigin});}});
    await page.goto(origin,{waitUntil:'domcontentloaded',timeout:30000});
    await page.waitForFunction(()=>['ready','error'].includes(window.rsVideo?.state),{},{timeout:30000});
    result.beforePlay=await page.evaluate(()=>window.rsVideo);await page.screenshot({path:out+'/before-play.png'});
    if(result.beforePlay.state==='ready'){
      const play=page.frameLocator('iframe').getByRole('button',{name:/^Play(?: video)?$/i}).first();
      await play.click({timeout:15000});result.trustedPlayClicked=true;
      await page.waitForFunction(()=>['playing','error'].includes(window.rsVideo?.state),{},{timeout:30000});
    }else result.trustedPlayClicked=false;
    result.afterPlay=await page.evaluate(()=>window.rsVideo);
    result.providerFrames=[];
    for(const frame of page.frames()){
      let url;try{url=new URL(frame.url());}catch{continue;}
      if(url.origin!=='https://www.youtube.com'||url.pathname!==`/embed/${id}`)continue;
      result.providerFrames.push(await frame.evaluate(()=>({origin:location.origin,
        reason:(document.querySelector('.ytp-error-content-wrap-reason')?.textContent||'').replace(/\s+/g,' ').trim().slice(0,512),
        subreason:(document.querySelector('.ytp-error-content-wrap-subreason')?.textContent||'').replace(/\s+/g,' ').trim().slice(0,512),
        video:Array.from(document.querySelectorAll('video')).slice(0,1).map(video=>({paused:video.paused,readyState:video.readyState,width:video.videoWidth,height:video.videoHeight}))})));
    }
    await page.screenshot({path:out+'/after-play.png'});result.collection='complete';
  }catch(error){result.collection='incomplete';result.failureClass=error.name;try{result.lastStatus=await page?.evaluate(()=>window.rsVideo);await page?.screenshot({path:out+'/failure.png'});}catch{}}
  finally{await context?.close();await browser?.close();await new Promise(resolve=>server.close(resolve));fs.writeFileSync(out+'/result.json',JSON.stringify(result,null,2)+'\n');}
}
main().catch(()=>{process.stderr.write('Host comparison setup failed\n');process.exitCode=1;});
