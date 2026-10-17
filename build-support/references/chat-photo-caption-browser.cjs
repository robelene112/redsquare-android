// Offline DOM/media of actual pinned web sanitization output. Photo pixels are inline-only.
const fs=require('node:fs'),assert=require('node:assert/strict');
const {chromium}=require('/browser-tools/node_modules/playwright');
const rendered=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  assert.equal(rendered.vectors.length,2);const observations=[];
  for(const vector of rendered.vectors){
   const page=await browser.newPage();
   await page.route('**/*',route=>rendered.mediaKind==='gif'&&route.request().url()===vector.source?
    route.fulfill({status:200,contentType:'image/gif',body:fs.readFileSync('/reference/owned.gif')}):route.abort());
   await page.setContent(vector.html);assert.equal(await page.locator('img.img-prev').count(),1);
   assert.equal(await page.locator('div').innerText(),vector.caption);
   if(vector.quote){
    assert.equal(await page.locator('blockquote').count(),1);
    assert.equal(await page.locator('blockquote').getAttribute('href'),vector.quote.reference);
    assert.equal(await page.locator('blockquote').innerText(),vector.quote.text);
   }
   if(rendered.mediaKind==='gif')assert.equal(await page.locator('img.img-prev').getAttribute('src'),vector.source);
   const pixels=await page.locator('img.img-prev').evaluate(async (image,gif)=>{
    await image.decode();
    if(gif)return {width:image.naturalWidth,height:image.naturalHeight};
    const canvas=document.createElement('canvas');canvas.width=image.naturalWidth;canvas.height=image.naturalHeight;
    const context=canvas.getContext('2d');context.drawImage(image,0,0);
    return {width:canvas.width,height:canvas.height,rgb:Array.from(context.getImageData(6,4,1,1).data).slice(0,3)};
   },rendered.mediaKind==='gif');
   if(rendered.mediaKind==='gif'){
    assert.equal(pixels.width,16);assert.equal(pixels.height,16);
   }else{
    assert.equal(pixels.width,12);assert.equal(pixels.height,8);
    pixels.rgb.forEach((value,index)=>assert(Math.abs(value-[220,30,40][index])<=(rendered.nativeEnvelopeSha256?5:0)));
   }
   observations.push({pin:vector.pin,browserSha256:vector.browserSha256,exactCaption:true,...(vector.quote?{exactQuoteReferenceAndText:true}:{}),...(rendered.mediaKind==='gif'?{exactSource:true,ownedGifDecoded:true,dimensions:[pixels.width,pixels.height]}:{pixels})});await page.close();
  }
  console.log(JSON.stringify({result:'PASS',boundary:'Offline Chromium DOM/media after actual pinned Browser.sanitize and inspected Chat newline conversion. No initialized Saito app or live network claim.',...(rendered.nativeEnvelopeSha256?{nativeEnvelopeSha256:rendered.nativeEnvelopeSha256}:{}),observations},null,2));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
