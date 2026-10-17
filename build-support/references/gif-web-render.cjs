// Presentation-only adapter after independent verification of a controlled public Android frame.
// Execute offline/read-only. No initialized Saito application, wallets, network or DOM.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const proof = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
assert.equal(proof.result, 'PASS');
const kind = process.argv[5];
assert(['post', 'reply', 'quote'].includes(kind));
assert.match(proof.outer.body.data.text, new RegExp(`^Native GIF ${kind} (owner|official)$`));
if (kind === 'quote') assert.equal(proof.original.body.data.text, 'Signed image reference');
const sourceDir = path.resolve(process.argv[3]);
const childDir = path.resolve(process.argv[4]);
const pins = {A: '9a4ca7e9442eb5e872168ced361ce741d7d139d7', B: '214504ecece76d1cd20a245fe4468eccbfca2951'};
const app = {
  BROWSER: true,
  keychain: {returnUsername: key => `Anon-${key.slice(0, 6)}`, returnIdenticon: () => '/controlled-avatar.png'},
  browser: {formatRelativeTime: () => 'controlled time', extractFirstValidURL: text => {
    assert(!/https?:|www\.|@/.test(text)); return '';
  }, markupMentions: text => text, sanitize: text => {
    assert(!/[<>&]/.test(text)); return text;
  }}
};
const observations = [];
for (const pin of ['A', 'B']) {
  const loaded = new Map(), hashes = {};
  let sentinelUsed = false;
  const forbidden = () => { sentinelUsed = true; throw new Error('Unused dependency invoked'); };
  function load(name) {
    if (loaded.has(name)) return loaded.get(name);
    let filename;
    if (name === './tweet') filename = path.join(sourceDir, `${pin}-node_mods_redsquare_lib_tweet.js`);
    else if (name === './tweet.template') filename = path.join(sourceDir, `${pin}-node_mods_redsquare_lib_tweet.template.js`);
    else if (/^\.\/tweet-(header|body|gallery|footer)\.template$/.test(name))
      filename = path.join(childDir, `${pin}-node_mods_redsquare_lib_${name.slice(2)}.js`);
    else if (name === '../../../lib/saito/transaction') return {default: forbidden};
    else if (['../../../lib/saito/ui/saito-link-preview/saito-link-preview',
      '../../../lib/saito/ui/saito-link-preview/saito-link-preview.template'].includes(name)) return forbidden;
    else throw new Error(`Unallowlisted import ${name}`);
    const source = fs.readFileSync(filename, 'utf8');
    hashes[path.basename(filename)] = crypto.createHash('sha256').update(source).digest('hex');
    const module = {exports: {}};
    vm.runInNewContext(source, {module, exports: module.exports, require: load,
      console: {warn: forbidden, log: forbidden, error: forbidden}}, {filename, timeout: 2000});
    loaded.set(name, module.exports);
    return module.exports;
  }
  const Tweet = load('./tweet');
  const tx = {signature: proof.outer.signature, timestamp: proof.outer.timestamp,
    from: [{publicKey: proof.outer.author}], to: proof.recipients.map(publicKey => ({publicKey})),
    optional: {}, msg: proof.outer.body, returnMessage() { return this.msg; }};
  const tweet = new Tweet(app, {}, tx);
  const html = tweet.renderHTML();
  assert(!sentinelUsed);
  assert.equal(tweet.signature, proof.outer.signature);
  assert.equal(tweet.publicKey, proof.outer.author);
  assert.deepEqual(Array.from(tweet.images), proof.outer.body.data.images);
  const expectedImages = [...proof.outer.body.data.images];
  if (kind === 'quote') {
    assert.equal(tweet.embedded.signature, proof.original.signature);
    assert.equal(tweet.embedded.publicKey, proof.original.author);
    assert.equal(tweet.embedded.text, proof.original.body.data.text);
    assert.deepEqual(Array.from(tweet.embedded.images), proof.original.body.data.images);
    expectedImages.push(...proof.original.body.data.images);
  }
  const renderedImages = [...html.matchAll(/<img\b[^>]*src="([^"]*)"/g)].map(match => match[1].replaceAll('&amp;', '&'))
    .filter(src => src === proof.outer.body.data.images[0] || src.startsWith('data:image/'));
  assert.deepEqual(renderedImages, expectedImages);
  assert(html.includes(proof.outer.body.data.text));
  assert(!sentinelUsed);
  observations.push({pin, commit: pins[pin], hashes, kind,
    outerCommentaryRendered: html.includes(proof.outer.body.data.text),
    originalIdentityRendered: kind === 'quote' ? html.includes(`data-id="${proof.original.signature}"`) : null,
    exactGifUrlRendered: renderedImages[0] === proof.outer.body.data.images[0],
    expectedImages, html});
}
console.log(JSON.stringify({nodeVersion: process.version, frameSha256: proof.frameSha256,
  boundary: 'Verified decoded data adapter plus unmodified pinned constructor/template graph; no DOM/network acceptance proof',
  observations}, null, 2));
