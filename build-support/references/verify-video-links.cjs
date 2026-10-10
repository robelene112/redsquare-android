// Source-only compatibility proof: invoke pinned public Browser/Tweet methods without
// constructing a Saito application, wallet, browser or node.
const fs = require('node:fs');
const crypto = require('node:crypto');
const vm = require('node:vm');
const paths = process.argv.slice(2);
if (paths.length !== 4) throw new Error('Expected pinned A/B Tweet and A/B Browser source paths');
const urls = [
  'https://www.youtube.com/watch?v=M7lc1UVf-VE&t=20',
  'https://youtu.be/M7lc1UVf-VE?si=public',
  'https://youtube.com/shorts/M7lc1UVf-VE',
  'https://m.youtube.com/live/M7lc1UVf-VE#t=2',
  'https://music.youtube.com/watch?v=M7lc1UVf-VE',
];
const textCases = [
  {text:'Watch youtu.be/M7lc1UVf-VE', id:'M7lc1UVf-VE'},
  {text:'See https://example.com/info then https://youtu.be/M7lc1UVf-VE', id:'M7lc1UVf-VE'},
  {text:'See https://youtu.be/M7lc1UVf-VE then https://example.com/info', id:null},
  {text:'https://youtu.be/M7lc1UVf-VE https://youtu.be/bHQqvYy5KYo', id:'bHQqvYy5KYo'},
];
const results = paths.slice(0, 2).map((path, index) => {
  const source = fs.readFileSync(path, 'utf8');
  const start = source.indexOf('\n  extractYoutubeId(link) {');
  const end = source.indexOf('\n  async analyseTweetLinks', start);
  if (start < 0 || end < start) throw new Error('Pinned method boundary not found');
  const method = source.slice(start, end).trim().replace(/^extractYoutubeId\(link\)/, 'function(link)');
  const browser = fs.readFileSync(paths[index + 2], 'utf8');
  const methods = ['extractFirstValidURL(text)', 'urlRegexp()', 'numberFilter(potential_link)'].map(name => {
    const start = browser.indexOf(`\n  ${name} {`);
    const end = browser.indexOf('\n  }', start);
    if (start < 0 || end < start) throw new Error('Pinned Browser method boundary not found');
    return browser.slice(start, end + '\n  }'.length);
  }).join('\n');
  const context = vm.createContext({ URL, URLSearchParams, urls, textCases, console: {error(){}} });
  context.method = new vm.Script(`(${method})`).runInContext(context, { timeout: 1000 });
  context.browser = new vm.Script(`new (class {${methods}})()`).runInContext(context, {timeout:1000});
  const ids = new vm.Script('urls.map(url => method(url))').runInContext(context, { timeout: 1000 });
  if (ids.some(id => id !== 'M7lc1UVf-VE')) throw new Error('Pinned provider identity disagrees');
  const texts = new vm.Script('textCases.map(item => {const link=browser.extractFirstValidURL(item.text); return {text:item.text,link,id:method(link || "")}})').runInContext(context, {timeout:1000});
  if (texts.some((item, i) => item.id !== textCases[i].id)) throw new Error('Pinned text candidate identity disagrees');
  return { baseline: index ? 'B-214504ec' : 'A-9a4ca7e', sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), browserSha256:crypto.createHash('sha256').update(browser).digest('hex'), urls, ids, texts };
});
process.stdout.write(JSON.stringify({ scope: 'unchanged pinned method only; no initialized client or live playback', results }, null, 2) + '\n');
