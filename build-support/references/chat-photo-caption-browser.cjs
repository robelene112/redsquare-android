// Offline DOM and pixels of actual pinned web sanitization output.
const fs=require('node:fs'),assert=require('node:assert/strict');
const {chromium}=require('/browser-tools/node_modules/playwright');
const rendered=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  assert.equal(rendered.vectors.length,2);const observations=[];
  for(const vector of rendered.vectors){
   const page=await browser.newPage();await page.route('**/*',route=>route.abort());
   await page.setContent(vector.html);assert.equal(await page.locator('img.img-prev').count(),1);
   assert.equal(await page.locator('div').innerText(),vector.caption);
   const pixels=await page.locator('img.img-prev').evaluate(async image=>{
    await image.decode();const canvas=document.createElement('canvas');canvas.width=image.naturalWidth;canvas.height=image.naturalHeight;
    const context=canvas.getContext('2d');context.drawImage(image,0,0);
    return {width:canvas.width,height:canvas.height,rgb:Array.from(context.getImageData(6,4,1,1).data).slice(0,3)};
   });
   assert.deepEqual(pixels,{width:12,height:8,rgb:[220,30,40]});
   observations.push({pin:vector.pin,browserSha256:vector.browserSha256,exactCaption:true,pixels});await page.close();
  }
  console.log(JSON.stringify({result:'PASS',boundary:'Offline Chromium DOM/raster after actual pinned Browser.sanitize and inspected Chat newline conversion. No signed native transport or initialized Saito app claim.',observations},null,2));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
