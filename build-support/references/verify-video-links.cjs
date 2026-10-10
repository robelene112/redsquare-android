// Source-only compatibility proof: invoke the pinned public Tweet method without
// constructing a Saito application, wallet, browser or node.
const fs = require('node:fs');
const crypto = require('node:crypto');
const vm = require('node:vm');
const paths = process.argv.slice(2);
if (paths.length !== 2) throw new Error('Expected pinned A and B Tweet source paths');
const urls = [
  'https://www.youtube.com/watch?v=M7lc1UVf-VE&t=20',
  'https://youtu.be/M7lc1UVf-VE?si=public',
  'https://youtube.com/shorts/M7lc1UVf-VE',
  'https://m.youtube.com/live/M7lc1UVf-VE#t=2',
  'https://music.youtube.com/watch?v=M7lc1UVf-VE',
];
const results = paths.map((path, index) => {
  const source = fs.readFileSync(path, 'utf8');
  const start = source.indexOf('\n  extractYoutubeId(link) {');
  const end = source.indexOf('\n  async analyseTweetLinks', start);
  if (start < 0 || end < start) throw new Error('Pinned method boundary not found');
  const method = source.slice(start, end).trim().replace(/^extractYoutubeId\(link\)/, 'function(link)');
  const context = vm.createContext({ URL, urls });
  context.method = new vm.Script(`(${method})`).runInContext(context, { timeout: 1000 });
  const ids = new vm.Script('urls.map(url => method(url))').runInContext(context, { timeout: 1000 });
  if (ids.some(id => id !== 'M7lc1UVf-VE')) throw new Error('Pinned provider identity disagrees');
  return { baseline: index ? 'B-214504ec' : 'A-9a4ca7e', sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), urls, ids };
});
process.stdout.write(JSON.stringify({ scope: 'unchanged pinned method only; no initialized client or live playback', results }, null, 2) + '\n');
