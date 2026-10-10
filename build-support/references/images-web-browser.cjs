// Offline DOM/raster proof of the unmodified pinned template graph's synthetic image quote.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {chromium} = require('/browser-tools/node_modules/playwright');
const rendered = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const pins = {A: '9a4ca7e9442eb5e872168ced361ce741d7d139d7', B: '214504ecece76d1cd20a245fe4468eccbfca2951'};
assert.equal(rendered.observations.length, 2);
assert.deepEqual(rendered.observations.map(item => [item.pin, item.commit]).sort(), Object.entries(pins));
(async () => {
  const browser = await chromium.launch({headless: true});
  try {
    const observations = [];
    for (const source of rendered.observations) {
      assert.equal(source.imageOrderAndSafetyChecked, true);
      const page = await browser.newPage();
      await page.route('**/*', route => route.abort());
      await page.setContent(source.html);
      const images = page.locator('img[src^="data:image/"]');
      assert.equal(await images.count(), 3);
      const expected = [[30,190,70], [220,30,40], [20,60,220]];
      const pixels = [];
      for (let index = 0; index < 3; index++) {
        const image = images.nth(index);
        await image.scrollIntoViewIfNeeded();
        const observed = await image.evaluate(async node => {
          await node.decode();
          const canvas = document.createElement('canvas');
          canvas.width = node.naturalWidth; canvas.height = node.naturalHeight;
          const context = canvas.getContext('2d'); context.drawImage(node, 0, 0);
          return {width: canvas.width, height: canvas.height,
            rgb: Array.from(context.getImageData(6, 4, 1, 1).data).slice(0, 3)};
        });
        assert.equal(observed.width, 12); assert.equal(observed.height, 8);
        observed.rgb.forEach((channel, component) => assert(Math.abs(channel - expected[index][component]) <= 5));
        pixels.push(observed);
      }
      observations.push({pin: source.pin, commit: source.commit, pixels});
      await page.close();
    }
    console.log(JSON.stringify({result: 'PASS', frameSha256: rendered.frameSha256,
      boundary: 'Offline Chromium DOM and raster decode of pinned template output; no Saito app/network/node acceptance',
      observations}, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
