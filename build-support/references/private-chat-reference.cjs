// Research-only pure crypto. Public unsafe scalar1/2 identities and scalar3/4
// exchanged-key fixtures, never fund or use them in production.
// Run in a read-only, network-disabled container with env-i. No Saito app init.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodeCrypto = require('node:crypto');
const Module = require('node:module');
const vm = require('node:vm');
const root = '/opt/saito/node/node_modules/';
const wasm = require(root + 'saito-wasm/pkg/node');
const ts = require(root + 'typescript');
const {CryptoJS, JsonFormatter} = require(root + 'node-cryptojs-aes');
const base58 = require(root + 'base-58');
const JSONBig = require(root + 'json-bigint');
const {reference: signingReference, signedReference} = require('./signed-reference.cjs');
const sha256 = bytes => nodeCrypto.createHash('sha256').update(bytes).digest('hex');
const wrapperSource = fs.readFileSync('/reference/crypto.ts', 'utf8');
const wrapperHash = sha256(wrapperSource);
assert.equal(wrapperHash, '61416839726c1877875062273fd704b37002f8f3a5d299e6c0883f5d24685683');

const dependencyVersions = Object.fromEntries([
  ['saito-wasm', '0.3.2'], ['saito-js', '0.3.2'], ['node-cryptojs-aes', '0.4.0'],
  ['crypto-browserify', '3.12.1'], ['elliptic', '6.6.1'], ['base-58', '0.0.1'],
  ['typescript', '5.4.5'], ['json-bigint', '1.0.0'],
].map(([name, expected]) => {
  const version = require(root + name + '/package.json').version;
  assert.equal(version, expected, name);
  return [name, version];
}));
const publishedCryptoJsHashes = {
  'cryptojs.js': '2b41cafbdc388016fe728bbda71dea0f3fcdfd4fcc254f222b82bcb036ff2b0d',
  'lib/core.js': 'f3ba0b5034f4436fd714a9f2d9cd3f4c119a3100a3fe1d49e6cd3235ed700298',
  'lib/enc-base64.js': '0c8e5688533c3772fabcd0a2e5d8811755ae4baf6168befed428a6501d3e4504',
  'lib/md5.js': '30110c2eeae781c62239b8e870953643cb169e8d1614b1713b0c40f89cfff526',
  'lib/evpkdf.js': 'a2e623ce04de2cda60d61fd5e34631f6899fbc6efea8a8bc10d6228a526b5e3c',
  'lib/cipher-core.js': '086b68557e31019aec7562733ced8f154b9972d6af144aa79d12ee6d53e7926e',
  'lib/aes.js': 'c98e2695f1f50a65b71dcb0087059be0affd32ba7622b86a2ca0402884f10d1a',
  'lib/jsonformatter.js': 'f48ed871ad1ba13a79a4a0973d8c3a8e2d0e21d1f1ffb09012c7f630fc04e5f6',
};
for (const [file, expected] of Object.entries(publishedCryptoJsHashes)) {
  assert.equal(sha256(fs.readFileSync(root + 'node-cryptojs-aes/' + file)), expected, file);
}

// Execute the actual pinned Crypto wrapper after TypeScript transpilation.
// Its constructor only accepts the pure core crypto adapter; no wallet/app exists.
const compiledWrapper = ts.transpileModule(wrapperSource, {
  compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true},
}).outputText;
const wrapperModule = new Module('/reference/crypto.cjs');
wrapperModule.require = id => require(root + id);
wrapperModule._compile(compiledWrapper, '/reference/crypto.cjs');
const Crypto = wrapperModule.exports.default;
const crypto = new Crypto({core: {crypto: {
  hash: bytes => wasm.hash(bytes),
  generateSharedSecret: (privateKey, publicKey) => wasm.generate_shared_secret(privateKey, publicKey),
}}});

// Extract the actual pure toJson/packData methods without constructing the
// saito-js Transaction class, whose getters use a Saito singleton/factory.
const transactionSourcePath = root + 'saito-js/lib/transaction.js';
const transactionSource = fs.readFileSync(transactionSourcePath, 'utf8');
const ast = ts.createSourceFile('transaction.js', transactionSource, ts.ScriptTarget.ES2022, true);
const transactionClass = ast.statements.find(node => ts.isClassDeclaration(node) && node.name.text === 'Transaction');
assert(transactionClass);
const extracted = {};
for (const name of ['packData', 'toJson']) {
  const method = transactionClass.members.find(node => ts.isMethodDeclaration(node) && node.name.getText(ast) === name);
  assert(method, name);
  extracted[name] = vm.runInNewContext(`({${method.getText(ast)}}).${name}`, {Buffer, Uint8Array});
}
const Slip = require(root + 'saito-js/lib/slip').default;
const publicKeys = Object.fromEntries(['1', '2'].map(scalar => [scalar, wasm.generate_public_key('0'.repeat(63) + scalar)]));
const groupId = crypto.hash(Object.values(publicKeys).sort().join('_'));
assert.equal(process.version, 'v24.13.0');

// Consume only the public unsafe scalar2 -> scalar1 transaction captured by the native UI fixture.
if (process.argv[2] === '--check-native-send') {
  const input = fs.readFileSync('/reference/native-sent-envelope.json');
  const envelope = JSON.parse(input);
  const detached = Buffer.from(envelope.t, 'base64');
  const message = Buffer.from(envelope.m, 'base64');
  assert.equal(detached.readUInt32BE(0), 1);
  assert.equal(detached.readUInt32BE(4), 2);
  assert.equal(detached.readUInt32BE(8), 0);
  assert.equal(detached[92], 0);
  const offset = 93 + 59 * 3;
  const network = Buffer.concat([detached.subarray(0, offset), message, detached.subarray(offset)]);
  network.writeUInt32BE(message.length, 8);
  const transaction = wasm.WasmTransaction.deserialize(network);
  assert.deepEqual(transaction.from.map(slip => slip.public_key), [publicKeys['2']]);
  assert.deepEqual(transaction.to.map(slip => slip.public_key), [publicKeys['2'], publicKeys['1']]);
  const ciphertext = JSON.parse(message.toString('utf8'));
  assert.equal(typeof ciphertext, 'string');
  const independent = signedReference('2', ciphertext, Number(transaction.timestamp), [publicKeys['1']]);
  assert.equal(transaction.signature, independent.sig);
  const secret = crypto.generateSharedSecret('0'.repeat(63) + '1', publicKeys['2']);
  const plaintext = JSON.parse(crypto.aesDecrypt(ciphertext, secret));
  assert.deepEqual(plaintext, {
    module: 'Chat', request: 'chat message', group_id: groupId,
    message: 'Native private message 😀 <&>\nsecond line',
    timestamp: Number(transaction.timestamp), mentioned: [],
  });
  console.log(JSON.stringify({
    nativeEnvelopeSha256: sha256(input), signature: transaction.signature,
    groupId, timestamp: Number(transaction.timestamp), dependencyVersions,
    wrapperSha256: wrapperHash, wasmSha256: signingReference.wasmSha256,
    independentWasmSignatureMatches: true, existingCryptoRecipientDecrypts: true,
    exactNativeFixturePlaintextMatches: true,
  }, null, 2));
  process.exit(0);
}

function vector(senderScalar, recipientScalar, timestamp, saltHex, exchangedSecret = null, content = {}) {
  assert(['1', '2'].includes(senderScalar) && ['1', '2'].includes(recipientScalar));
  assert.notEqual(senderScalar, recipientScalar);
  const senderKey = '0'.repeat(63) + senderScalar;
  const recipientKey = '0'.repeat(63) + recipientScalar;
  const sender = publicKeys[senderScalar], recipient = publicKeys[recipientScalar];
  const implicitSecret = crypto.generateSharedSecret(senderKey, recipient);
  assert.equal(implicitSecret, crypto.generateSharedSecret(recipientKey, sender));
  const ecdh = crypto.createDiffieHellman('', Buffer.from(senderKey, 'hex'));
  assert.equal(ecdh.getPublicKey(null, 'compressed').toString('hex'), Buffer.from(base58.decode(sender)).toString('hex'));
  assert.equal(ecdh.computeSecret(Buffer.from(base58.decode(recipient))).toString('hex'), implicitSecret);
  const sharedSecret = exchangedSecret ?? implicitSecret;
  const reverseSharedSecret = sharedSecret;
  const {mentioned = [], message: plaintextMessage = `Independent private Chat scalar${senderScalar} to scalar${recipientScalar} 😀 <&>\nline two`} = content;
  const plaintext = {
    module: 'Chat', request: 'chat message', group_id: groupId,
    message: plaintextMessage,
    timestamp, mentioned,
  };
  const plaintextJson = JSON.stringify(plaintext);
  const previousRandom = CryptoJS.lib.WordArray.random;
  let randomCalls = 0;
  let ciphertextJson;
  try {
    CryptoJS.lib.WordArray.random = count => {
      assert.equal(count, 8); randomCalls++;
      return CryptoJS.enc.Hex.parse(saltHex);
    };
    ciphertextJson = crypto.aesEncrypt(plaintextJson, sharedSecret);
  } finally {
    CryptoJS.lib.WordArray.random = previousRandom;
  }
  assert.equal(randomCalls, 1);
  const params = JSON.parse(ciphertextJson);
  assert.equal(params.s, saltHex);
  const derived = CryptoJS.kdf.OpenSSL.execute(sharedSecret, 8, 4, CryptoJS.enc.Hex.parse(saltHex));
  assert.equal(params.iv, derived.iv.toString());
  assert.equal(JsonFormatter.parse(ciphertextJson).ciphertext.toString(CryptoJS.enc.Base64), params.ct);
  assert.equal(crypto.aesDecrypt(ciphertextJson, sharedSecret), plaintextJson);
  assert.equal(crypto.aesDecrypt(ciphertextJson, reverseSharedSecret), plaintextJson);
  const signed = signedReference(senderScalar, ciphertextJson, timestamp, [recipient, ...mentioned]);
  const envelope = JSON.parse(signed.tx);
  const message = Buffer.from(envelope.m, 'base64');
  assert.equal(message.toString('utf8'), JSON.stringify(ciphertextJson));
  const network = Buffer.concat([Buffer.from(envelope.t, 'base64'), message]);
  network.writeUInt32BE(message.length, 8);
  const transaction = wasm.WasmTransaction.deserialize(network);
  assert.equal(transaction.signature, signed.sig);
  assert.equal(Buffer.from(transaction.data).toString('hex'), message.toString('hex'));
  const adapter = {
    msg: ciphertextJson, data: new Uint8Array(transaction.data),
    to: transaction.to.map(slip => new Slip(slip)),
    from: transaction.from.map(slip => new Slip(slip)),
    type: transaction.type, timestamp: Number(transaction.timestamp),
    signature: transaction.signature, txs_replacements: transaction.txs_replacements,
    total_fees: transaction.total_fees, routing_path: [],
    packData: extracted.packData,
  };
  const signedInner = extracted.toJson.call(adapter);
  assert.equal(signedInner.buffer, message.toString('base64'));
  assert.equal(signedInner.signature, signed.sig);
  assert.equal(crypto.aesDecrypt(JSON.parse(Buffer.from(signedInner.buffer, 'base64').toString('utf8')), reverseSharedSecret), plaintextJson);
  return {
    senderScalar, recipientScalar, sender, recipient, timestamp, groupId,
    keySource: exchangedSecret ? 'imported-exchanged' : 'implicit',
    plaintext, plaintextJson, sharedSecretHex: sharedSecret, saltHex,
    aesKeyHex: derived.key.toString(), aesIvHex: derived.iv.toString(),
    ciphertextJson, signedMessageJson: message.toString('utf8'),
    signedMessageBase64: message.toString('base64'), signature: signed.sig,
    networkBase64: network.toString('base64'), archiveEnvelope: signed.tx,
    signedInner, roundTripChecks: {
      wasmAndBrowserEcdhAgree: true, senderDecrypts: true, recipientDecrypts: true,
      signedWasmRestores: true, innerJsonPreservesSignedStringBytes: true,
    },
  };
}

const ephemeral3 = '0'.repeat(63) + '3', ephemeral4 = '0'.repeat(63) + '4';
const exchanged = crypto.generateSharedSecret(ephemeral3, wasm.generate_public_key(ephemeral4));
assert.equal(exchanged, crypto.generateSharedSecret(ephemeral4, wasm.generate_public_key(ephemeral3)));
const vectors = [vector('1', '2', 1700000080000, '0001020304050607'),
  vector('2', '1', 1700000080001, '0706050403020100'),
  vector('1', '2', 1700000080002, '08090a0b0c0d0e0f', exchanged),
  vector('1', '2', 1700000080003, '1011121314151617', exchanged.toUpperCase())];
if (process.argv[2] === '--mentioned') {
  // Chat addresses an outside mention but encrypts to the original DM counterpart.
  const mention = wasm.generate_public_key('0'.repeat(63) + '3');
  vectors.push(vector('1', '2', 1700000080020, '2021222324252627', null, {mentioned: [mention]}),
    vector('2', '1', 1700000080021, '28292a2b2c2d2e2f', null, {mentioned: [mention]}));
}
let replyFormat = null;
if (process.argv[2] === '--reply') {
  const browserSource = fs.readFileSync('/reference/browser.ts', 'utf8');
  const browserSha256 = sha256(browserSource);
  assert.equal(browserSha256, 'e85b67cc138dae5047bb855bf0961b2852a808fc7be3ffc5bc0479a1281bab38');
  const browserAst = ts.createSourceFile('browser.ts', browserSource, ts.ScriptTarget.ES2022, true);
  const browserClass = browserAst.statements.find(node => ts.isClassDeclaration(node) && node.name.text === 'Browser');
  const adapter = {};
  for (const name of ['sanitize', 'normalizeLegacySmartQuotedAnchors']) {
    const method = browserClass.members.find(node => ts.isMethodDeclaration(node) && node.name.getText(browserAst) === name);
    assert(method, name);
    adapter[name] = vm.runInNewContext(`({${method.getText(browserAst)}}).${name}`, {
      sanitizeHtml: require(root + 'sanitize-html'), sanitizer: require(root + 'sanitizer'), console,
      emoji: require(root + 'node-emoji'),
    });
  }
  // Pinned popup builds this prefix; SaitoInput stores sanitize(prefix), then appends innerText.
  const quote = adapter.sanitize(`<blockquote href="${publicKeys['2']}1700000079990">Earlier & quoted text</blockquote>`);
  assert.equal(quote, `<blockquote href="${publicKeys['2']}1700000079990">Earlier &amp; quoted text</blockquote>`);
  const body = 'Reply body <b>literal</b> 😀 <&>\nsecond line';
  const literal = 'Literal <blockquote>text</blockquote> and <b>body</b> 😀 <&>\nsecond line';
  vectors.push(vector('1', '2', 1700000080030, '3031323334353637', null, {message: quote + body}),
    vector('1', '2', 1700000080031, '38393a3b3c3d3e3f', null, {message: literal}));
  replyFormat = {browserSha256, sanitizeHtmlVersion: require(root + 'sanitize-html/package.json').version,
    sanitizeHtmlSha256: sha256(fs.readFileSync(require.resolve(root + 'sanitize-html'))),
    quotedText: 'Earlier & quoted text', body, literal,
    boundary: 'actual pinned Browser.sanitize and Crypto methods; prefix follows pinned popup/input source; no DOM or Saito application initialization'};
}
const malformedCiphertexts = ['ct', 'iv', 's'].map((field, index) => {
  const formatter = JSON.parse(vectors[0].ciphertextJson);
  formatter[field] = [formatter[field]];
  const ciphertext = JSON.stringify(formatter);
  let recovered = null, existingClientThrows = false;
  try { recovered = crypto.aesDecrypt(ciphertext, vectors[0].sharedSecretHex); }
  catch { existingClientThrows = true; }
  assert.notEqual(recovered, vectors[0].plaintextJson, field);
  const signed = signedReference('1', ciphertext, 1700000080010 + index, [publicKeys['2']]);
  return {field, archiveEnvelope: signed.tx, signature: signed.sig,
    existingClientRecoveredChat: false, existingClientThrows,
    returnedCharacters: typeof recovered === 'string' ? recovered.length : null};
});
console.log(JSONBig.stringify({
  synthetic: true, warning: 'Public unsafe scalar1/2 keys, scalar3/4 exchanged keys and fixed salts. Never fund or use in production.',
  reference: {
    ...signingReference, saitoPins: ['9a4ca7e9442eb5e872168ced361ce741d7d139d7', '214504ecece76d1cd20a245fe4468eccbfca2951'],
    nodeVersion: process.version, dependencyVersions, cryptoWrapperSha256: wrapperHash,
    saitoJsTransactionSha256: sha256(transactionSource), publishedCryptoJsHashes,
    publishedCryptoJsTarballSha256: 'b514fa78b0d056973a260586a45b77a83a4720b2ea45323f5bea3d22691e9b01',
    execution: 'read-only, network none, env-i; actual pinned Crypto methods and extracted pure saito-js JSON methods; no Saito application initialization',
  }, ...(replyFormat ? {replyFormat} : {}), vectors, malformedCiphertexts,
}, null, 2));
