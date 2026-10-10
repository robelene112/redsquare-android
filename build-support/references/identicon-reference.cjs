// Pure pinned WASM and author Identicon source, public unsafe references only.
// Download the exact author bytes linked in NATIVE_PROFILE_EDIT_CONTRACT.md first.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const { reference } = require('./signed-reference.cjs');
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
assert(process.argv[2], 'Provide the downloaded identicon.js2.3.3 source path');
const source = fs.readFileSync(process.argv[2], 'utf8');
const identiconSha256 = crypto.createHash('sha256').update(source).digest('hex');
assert.equal(identiconSha256, '17994ee41aae1f7057151524517f74a3489b58ea08f56c1cff33992774262e68');
const sandbox = { window: {} };
vm.runInNewContext(source, sandbox, {timeout:1000});
const vectors = [];
for (let index = 1; index <= 128; index++) {
  // Explicit unsafe synthetic scalars, never wallet files or application initialization.
  const address = wasm.generate_public_key(index.toString(16).padStart(64,'0'));
  const hash = wasm.hash(Buffer.from(address, 'utf8'));
  if (index <= 2 || (hash.startsWith('0') && !vectors.some(v => v.hash.startsWith('0')))) {
    const avatar = new sandbox.window.Identicon(hash, {saturation:0.6,brightness:0.4,margin:0,size:420,format:'svg'});
    const image = avatar.render();
    vectors.push({address,hash,foreground:image.foreground,background:image.background,
      rectangles:image.rectangles.filter(r => r.color === image.foreground),svg:image.getDump()});
  }
  if (vectors.length >= 3) break;
}
assert.equal(vectors.length, 3, 'A leading-zero public reference must exist within the bounded input set');
process.stdout.write(JSON.stringify({synthetic:true,reference,identiconVersion:'2.3.3',identiconSha256,vectors},null,2)+'\n');
