// Offline DOM/media decode for ordinary GIF posts rendered by pinned web templates.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {chromium} = require('/browser-tools/node_modules/playwright');
const rendered = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const gif = fs.readFileSync(process.argv[3]);
const pins = {A: '9a4ca7e9442eb5e872168ced361ce741d7d139d7', B: '214504ecece76d1cd20a245fe4468eccbfca2951'};
assert.deepEqual(rendered.observations.map(item => [item.pin, item.commit]).sort(), Object.entries(pins));
(async () => {
  const browser = await chromium.launch({headless: true});
  try {
    const observations = [];
    for (const source of rendered.observations) {
      assert.equal(source.exactGifUrlRendered, true);
      const gifUrl = source.expectedImages[0];
      const requests = [];
      const page = await browser.newPage();
      await page.route('**/*', route => {
        if (route.request().url() === gifUrl) {
          requests.push(route.request().url());
          return route.fulfill({contentType: 'image/gif', body: gif});
        }
        return route.abort();
      });
      await page.setContent(source.html);
      const images = page.locator('.gallery img');
      assert.equal(await images.count(), source.expectedImages.length);
      const decoded = [];
      for (let index = 0; index < source.expectedImages.length; index++) {
        const image = images.nth(index);
        await image.scrollIntoViewIfNeeded();
        const observed = await image.evaluate(async node => {
          await node.decode();
          return {src: node.getAttribute('src'), width: node.naturalWidth, height: node.naturalHeight};
        });
        assert.equal(observed.src, source.expectedImages[index]);
        assert.equal(observed.width, index === 0 ? 16 : 12);
        assert.equal(observed.height, index === 0 ? 16 : 8);
        decoded.push(observed);
      }
      assert(requests.length > 0);
      assert(requests.every(url => url === gifUrl));
      observations.push({pin: source.pin, commit: source.commit, requests, decoded});
      await page.close();
    }
    console.log(JSON.stringify({result: 'PASS', frameSha256: rendered.frameSha256,
      boundary: 'Offline Chromium DOM and decode of owned GIF bytes in pinned template output; no animation, live provider, node or production publication proof',
      observations}, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
