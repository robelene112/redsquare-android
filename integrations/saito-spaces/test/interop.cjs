'use strict';
// Actual pinned public Saito dispatcher/template/transaction and JS/WASM
// callback path against the owned JVM service. No node bootstrap or live peer.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const {stripTypeScriptTypes} = require('node:module');

if (process.argv.length !== 5) throw Error('Usage: node interop.cjs primary-source runtime fixture');
const primary = path.resolve(process.argv[2]);
const runtime = path.resolve(process.argv[3]);
const fixture = path.resolve(process.argv[4]);
const pkg = name => require(path.join(runtime, 'node_modules', name));
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const version = pkg('saito-js/package.json').version;
const profiles = {
  '0.3.15': {
    source: '0db0062d873369dc013046ed77c97410b5683b64',
    template: '00c20e1a0fb8b3b12d16fdf275710205d3899029b2bc6a953b10e51c0789ad50',
    modules: '6c70d022fc125654f075a798557673605c9101f2c7cdbcd583e08a264d10a7c8',
    wasm: '6b0c1be0fbdd2152805d232cef07883d992e3d2d0daeece86a3e9c20dd22a586'
  },
  '0.3.2': {
    source: '9a4ca7e9442eb5e872168ced361ce741d7d139d7',
    template: 'c35f8577d8226e0d756dd006c5146939ca3f79b09725f440c2d927e82c497147',
    modules: '3b05a96be3b5840e0c864db9ea3d4387c393469512a4256ff832af1c58c6fa09',
    wasm: 'f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e'
  }
};
assert.ok(Object.hasOwn(profiles, version), 'only an explicitly pinned runtime is supported');
const profile = profiles[version];
const pins = {
  'transaction.ts': 'b200fe3d42a332035dd88be173eb59c52abd066f2e5161c09e8e7d4ce37ac633',
  'modtemplate.js': profile.template,
  'modules.ts': profile.modules
};
for (const [name, expected] of Object.entries(pins)) assert.equal(sha(fs.readFileSync(path.join(primary, name))), expected, name);
assert.equal(pkg('saito-wasm/package.json').version, version);
assert.equal(sha(fs.readFileSync(path.join(runtime, 'node_modules/saito-wasm/pkg/node/index_bg.wasm'))), profile.wasm);
assert.equal(sha(fs.readFileSync(path.join(runtime, 'node_modules/saito-js/shared_methods.server.js'))), '797f83574013652f22d2f05dca002860031d735be4a2ffd01d03ec81d4ef8edb');

function load(code, filename, requireAtBoundary, globals = {}) {
  const module = {exports: {}};
  vm.runInNewContext(code, {module, exports: module.exports, require: requireAtBoundary, Buffer, console, process,
    __dirname: path.dirname(filename), ...globals}, {filename});
  return module.exports;
}
function typescript(name, exportedName, requires, globals) {
  let source = fs.readFileSync(path.join(primary, name), 'utf8').split('\n').filter(line => !line.startsWith('import ')).join('\n');
  source = source.replace('export default class ', 'class ').replace(/^export default \w+;$/m, '').replace(/^export /gm, '');
  source = stripTypeScriptTypes(source, {mode: 'transform'});
  return load(source + '\nmodule.exports = ' + exportedName + ';', name, requires, globals);
}
const unusedUi = class { constructor() { throw Error('Unused browser boundary was invoked'); } };
const BigJson = pkg('json-bigint');
const wasm = pkg('saito-wasm/pkg/node');
const Saito = pkg('saito-js/saito').default;
const BaseTransaction = pkg('saito-js/lib/transaction').default;
const Slip = pkg('saito-js/lib/slip').default;
const PeerService = pkg('saito-js/lib/peer_service').default;

(async () => {
  try {
    await wasm.initialize(JSON.stringify({server: {host: '127.0.0.1', port: 0, protocol: 'http', endpoint: {host: '127.0.0.1', port: 0, protocol: 'http'},
      verification_threads: 1, channel_size: 100, stat_timer_in_ms: 5000, reconnection_wait_time: 1000, thread_sleep_time_in_ms: 10, block_fetch_batch_size: 1},
      peers: [], spv_mode: true, browser_mode: true, consensus: {genesis_period: 100000, heartbeat_interval: 30000, prune_after_blocks: 100, max_staker_recursions: 1}}), '0'.repeat(63) + '1', 0, 1n, false);
    BaseTransaction.Type = wasm.WasmTransaction;
    Slip.Type = wasm.WasmSlip;
    PeerService.Type = wasm.WasmPeerService;
    const factory = {createSlip: native => new Slip(native)};
    Saito.instance = {factory};
    const Transaction = typescript('transaction.ts', 'Transaction', () => { throw Error('Unexpected transaction import'); },
      {JSON: BigJson, SaitoTransaction: BaseTransaction, Slip, TransactionType: pkg('saito-js/lib/transaction').TransactionType});
    const Template = load(fs.readFileSync(path.join(primary, 'modtemplate.js'), 'utf8'), 'modtemplate.js', name => {
      if (name === 'fs' || name === 'path') return require('node:' + name);
      if (name === 'json-bigint') return BigJson;
      if (name === './index' || name.includes('saito-calendar')) return unusedUi;
      throw Error('Unexpected template import');
    });
    const Mods = typescript('modules.ts', 'Mods', name => {
      if (name.includes('modtemplate')) return Template;
      throw Error('Unexpected dispatcher import');
    }, {Transaction, path, fs, ws: pkg('ws'), parse: require('node:url').parse, fromBase58: pkg('saito-js/lib/util').fromBase58});
    const ready = JSON.parse(fs.readFileSync(path.join(fixture, 'ready.json'), 'utf8'));
    const callbackBytes = [];
    const app = {BROWSER: 0, options: {}, storage: {returnFileSystem: () => null},
      wallet: {getPublicKey: async () => ready.service}, crypto: {
        stringToBase64: text => Buffer.from(text, 'utf8').toString('base64'), base64ToString: text => Buffer.from(text, 'base64').toString('utf8')},
      core: {network: {getPeer: async publicKey => ({publicKey}), api: {success: async (buffer, index, publicKey) => {
        callbackBytes.push({bytes: Buffer.from(buffer), index, publicKey});
      }}}}};
    process.env.SAITO_SPACES_SERVICE_URL = `http://127.0.0.1:${ready.port}`;
    const adapter = path.resolve(__dirname, '../spaces.js');
    const Spaces = load(fs.readFileSync(adapter, 'utf8'), adapter, name => {
      if (name.includes('modtemplate')) return Template;
      if (name === 'saito-js/lib/peer_service') return {default: PeerService};
      if (name === './bridge.cjs') return require('../bridge.cjs');
      throw Error('Unexpected Spaces adapter import');
    });
    const module = new Spaces(app);
    await module.initialize(app);
    assert.equal(module.returnServices().length, 13);
    assert.equal(module.returnServices()[0].instance.service, 'spaces-v1');
    assert.equal(module.returnServices()[1].instance.service, 'spaces-directory-v1');
    assert.equal(module.returnServices()[2].instance.service, 'spaces-unread-v1');
    assert.equal(module.returnServices()[3].instance.service, 'spaces-channel-archive-v1');
    assert.equal(module.returnServices()[4].instance.service, 'spaces-role-management-v1');
    assert.equal(module.returnServices()[5].instance.service, 'spaces-public-channel-edit-v1');
    assert.equal(module.returnServices()[6].instance.service, 'spaces-private-channel-access-v1');
    assert.equal(module.returnServices()[7].instance.service, 'spaces-private-channel-edit-v1');
    assert.equal(module.returnServices()[8].instance.service, 'spaces-membership-management-v1');
    assert.equal(module.returnServices()[9].instance.service, 'spaces-ownership-management-v1');
    assert.equal(module.returnServices()[10].instance.service, 'spaces-category-management-v1');
    assert.equal(module.returnServices()[11].instance.service, 'spaces-visibility-management-v1');
    assert.equal(module.returnServices()[12].instance.service, 'spaces-dissolution-v1');
    app.modules = new Mods(app, []);
    app.modules.mods.push(module);
    const {ServerSharedMethods} = pkg('saito-js/shared_methods.server');
    const shared = new ServerSharedMethods(app);
    const original = Buffer.from(fs.readFileSync(path.join(fixture, 'original.txt'), 'utf8').trim(), 'base64');
    const expected = JSON.parse(fs.readFileSync(path.join(fixture, 'original-envelope.json'), 'utf8'));
    const request = new Transaction(); request.deserialize(original); request.unpackData();
    assert.equal(request.from[0].publicKey, ready.owner);
    assert.equal(request.signature, ready.original);
    assert.deepEqual(JSON.parse(request.serialize_to_web(app)), expected);
    for (let index = 1; index <= 3; index++) {
      await shared.processApiCall(original, index, ready.owner);
      assert.equal(callbackBytes.length, index, 'exactly one actual dispatcher callback per request');
      assert.equal(callbackBytes[index - 1].publicKey, ready.owner);
      assert.equal(callbackBytes[index - 1].index, index);
    }
    const first = JSON.parse(callbackBytes[0].bytes.toString('utf8'));
    assert.deepEqual(JSON.parse(callbackBytes[1].bytes.toString('utf8')), first);
    assert.deepEqual(JSON.parse(callbackBytes[2].bytes.toString('utf8')), first);
    const received = new Transaction(); received.deserialize_from_web(app, JSON.stringify(first));
    assert.equal(received.from[0].publicKey, ready.service);
    assert.equal(received.returnMessage().request, 'receipt');
    assert.equal(received.returnMessage().stream, 'control');
    assert.deepEqual(JSON.parse(received.serialize_to_web(app)), first);
    fs.writeFileSync(path.join(fixture, 'node-receipt.json'), JSON.stringify(first));
    const otherIdentity = wasm.generate_public_key('0'.repeat(63) + '4');
    await shared.processApiCall(original, 4, otherIdentity);
    assert.equal(callbackBytes.length, 4);
    assert.equal(JSON.parse(callbackBytes[3].bytes.toString('utf8')).spaces_error, 'unavailable');
    const result = {source: profile.source, saitoJs: version, saitoWasm: version,
      actualDispatcher: true, actualTemplateInitialize: true, actualServerCallback: true, actualTransactionRoundTrip: true,
      repeatedReceiptStable: true, wrongAuthenticatedPeerDenied: true, callbacks: 4,
      qualification: 'Owned loopback service and public unsafe identities; runtime wallet/filesystem adapters, unused browser boundaries excluded. Not a bootstrapped production node.'};
    fs.writeFileSync(path.join(fixture, 'node-result.json'), JSON.stringify(result, null, 2) + '\n');
    process.stdout.write(JSON.stringify(result) + '\n');
  } finally { fs.writeFileSync(path.join(fixture, 'stop'), 'stop'); }
})().catch(error => { console.error(error); process.exitCode = 1; });
