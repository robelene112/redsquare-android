// Public unsafe fixtures only. Pure pinned WASM/Elliptic, no app initialization or network.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {reference, signedReference} = require('./signed-reference.cjs');
const original = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/research/NATIVE_FEED_REFERENCE.json'), 'utf8')).archiveRow;
function post(index) {
  const text = index === 0 ? 'Available older result' : index === 100 ? 'Available newest result' : `Available page post ${String(index).padStart(3, '0')}`;
  const row = signedReference('1', {module: 'RedSquare', request: 'create tweet', data: {text, parent_id: '', thread_id: ''}}, 1700000100000 + index);
  return {...row, field1: 'RedSquare', field4: '', field5: row.sig, flagged: 0};
}
const posts = Array.from({length: 101}, (_, index) => post(index));
assert.equal(new Set(posts.map(row => row.sig)).size, 101);
const currentQuote = {...signedReference('2', {module: 'RedSquare', request: 'retweet', data: {text: 'Available quote result', signature: original.sig}}, 1700000100200), field1: 'RedSquareRetweet', flagged: 0};
console.log(JSON.stringify({synthetic: true, reference, original, firstPage: posts.slice(1).reverse(), older: posts[0], currentQuote}, null, 2));
