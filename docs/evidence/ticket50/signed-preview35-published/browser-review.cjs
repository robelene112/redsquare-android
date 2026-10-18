const { chromium } = require('/work/browser-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const [url, version, mode] = process.argv.slice(2);
  const performanceApp = mode === 'performance';
  const browser = await chromium.launch();
  const records = [];
  try {
    for (const [label, width, height] of [['mobile',390,844], ['desktop',1280,900]]) {
      const page = await browser.newPage({viewport:{width,height}, deviceScaleFactor:1});
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(url, {waitUntil:'networkidle'});
      await page.locator('#install').waitFor({state:'visible',timeout:15000});
      const manifestResponse = await page.request.get(url+'latest.json');
      if (!manifestResponse.ok()) throw new Error('Manifest unavailable');
      const manifest = await manifestResponse.json();
      const expectedId = 'nl.davidengelen.redsquare'+(performanceApp?'.performance':'');
      if (manifest.applicationId!==expectedId || manifest.versionCode!==Number(version)) throw new Error('Wrong preview identity');
      const heading = await page.locator('h1').innerText();
      if (heading !== (performanceApp?'Red Square Performance':'Red Square')) throw new Error('Wrong preview heading');
      if (await page.locator('#install').getAttribute('href') !== manifest.apkUrl) throw new Error('Wrong APK link');
      if (await page.locator('#status').innerText() !== 'Version '+manifest.versionName) throw new Error('Wrong visible version');
      const overflow = await page.evaluate(()=>document.documentElement.scrollWidth > innerWidth);
      if (overflow || errors.length) throw new Error('Page rendering failed: '+JSON.stringify({overflow,errors}));
      await page.screenshot({path:`/work/browser-${mode}${version}-${label}.png`,fullPage:true});
      records.push({url,label,width,height,heading,applicationId:manifest.applicationId,versionCode:manifest.versionCode,apkUrl:manifest.apkUrl,horizontalOverflow:overflow,pageErrors:errors});
      await page.close();
    }
    fs.writeFileSync(`/work/browser-${mode}${version}-result.json`,JSON.stringify({status:'PASS',records},null,2)+'\n');
    console.log('PASS: actual public '+mode+' page renders its own version/download on both viewport sizes');
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1});
