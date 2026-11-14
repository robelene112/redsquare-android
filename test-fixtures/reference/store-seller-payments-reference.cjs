// Isolated pinned Store/Mixin methods, real SQLite and actual WASM signing. Public unsafe fixtures only.
const fs = require('fs'), path = require('path'), vm = require('vm'), crypto = require('crypto'), assert = require('assert');
const { DatabaseSync } = require('node:sqlite');
if (process.argv.length !== 7) throw Error('Usage: node store-seller-payments-reference.cjs runtime upstream acorn validator-deps output');
const [runtime, root, parser, deps, output] = process.argv.slice(2).map(p => path.resolve(p));
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const hashes = {
  'node/mods/store/lib/database.js': '0f40d0a346d0c71aa0219fff8c446a3b55eba0f0438f30d306aa05261876dc48',
  'node/mods/store/lib/withdrawal.js': '80857f87587937a681ac1d8ccad8bedd00d6663bf29a882dac65d9bf5bbdb2ab',
  'node/mods/store/lib/withdrawal-quote.js': 'fc77bb19a6852e5e8d361cd9abcab3c712752df1d20556ca1dc76940f4071e52',
  'node/mods/store/lib/web3-amount.js': '880c4400896f00bababfc69be7b32e8527c3f3c845ed3395782f86e9b08b4e48',
  'node/mods/mixin/lib/store-withdrawal.js': '9c178cba9bbee1e59838e9e6121c7eac528425231d233653adc088222553f9a1',
  'node/mods/mixin/lib/mixinmodule.js': '95ff9fd88877a4dec358c53bf5a3367a32bf1c5a9a7262197766fe41390451ba',
  'node/mods/mixin/lib/database.js': '59b652568d76e8b6e3fb2223527a322b245c262876f493beeae4a1f8ea3621c6',
  'node/mods/store/sql/listing_sales.sql': '649f4a4b678ac31c1e5dec8d9aef186138092f02865aa7ec860e4fa1d023fb5f',
  'node/mods/store/sql/withdrawals.sql': '62829072447756b9215f67aa6a5c9716593fea8443cde75a918317381e494790',
  'node/mods/mixin/sql/transfers.sql': '2e2d6d50610c41c0727c7b0649f9228c202af44d22ecf2b7a41e41d5a8293fc7'
};
for (const [file, hash] of Object.entries(hashes)) assert.equal(sha(fs.readFileSync(path.join(root, file))), hash, file);
assert.equal(sha(fs.readFileSync(parser)), 'b95392d79457d0357555c5ed3e56302d074b92dff119bcf3b44179500b10e9df');
assert.equal(require(path.join(runtime, 'node_modules/saito-wasm/package.json')).version, '0.3.15');
assert.equal(require(path.join(deps, 'node_modules/@taasfi/multicoin-address-validator/package.json')).version, '0.5.27');
const acorn = require(parser), wasm = require(path.join(runtime, 'node_modules/saito-wasm/pkg/node'));
const base58 = require(path.join(runtime, 'node_modules/base-58'));
const WAValidator = require(path.join(deps, 'node_modules/@taasfi/multicoin-address-validator'));
const quiet = { log() {}, warn() {}, error() {} }, clone = v => JSON.parse(JSON.stringify(v));
let clock = 1700006000000, height = 24;
const globals = () => ({ console: quiet, Date: { now: () => clock }, Buffer, transactionTails: new WeakMap() });
const source = file => fs.readFileSync(path.join(root, file), 'utf8');
function klass(file, name, context, selected = null) {
  const text = source(file), tree = acorn.parse(text, { ecmaVersion: 'latest' });
  const declaration = tree.body.find(n => n.type === 'ClassDeclaration' && n.id.name === name);
  const body = selected ? 'class ' + name + ' {' + declaration.body.body.filter(n => selected.includes(n.key.name)).map(n => text.slice(n.start, n.end)).join('\n') + '}' : text.slice(declaration.start, declaration.end);
  return vm.runInNewContext(body + '\n' + name, context);
}
function moduleFunctions(file, context) {
  const text = source(file), tree = acorn.parse(text, { ecmaVersion: 'latest' });
  const declarations = tree.body.filter(n => n.type === 'FunctionDeclaration').map(n => text.slice(n.start, n.end)).join('\n');
  const exported = tree.body.filter(n => n.type === 'ExpressionStatement' && n.expression.type === 'AssignmentExpression' && n.expression.left.object?.name === 'module').map(n => text.slice(n.start, n.end)).join('\n');
  return vm.runInNewContext(declarations + '\n' + exported + '\nmodule.exports', { ...context, module: { exports: {} } });
}
function sqlite(sql) {
  const database = new DatabaseSync(':memory:', { enableDoubleQuotedStringLiterals: true });
  database.exec(sql);
  const facade = {
    all: async (query, params = {}) => database.prepare(query).all(params),
    run: async (query, params = {}) => {
      const r = database.prepare(query).run(params);
      return { changes: Number(r.changes), lastID: Number(r.lastInsertRowid) };
    },
    exec: async query => database.exec(query)
  };
  return { database, facade };
}
const forbidden = () => { throw Error('Reference must not use a network'); };
global.shared_methods = { read_value: async () => new Uint8Array(), write_value: async () => {}, append_value() {}, flush_data() {}, ensure_directory_exists() {}, load_block_file_list: async () => [], is_existing_file: async () => false, remove_value: async () => {}, load_wallet() {}, save_wallet() {}, load_blockchain() {}, save_blockchain() {}, emit_interface_event() {}, connect_to_peer: forbidden, send_message_by_peer_id: forbidden, send_message: forbidden, send_message_to_all: forbidden };
function envelope(tx) {
  const bytes = Buffer.from(tx.serialize()), offset = 93 + 59 * (bytes.readUInt32BE(0) + bytes.readUInt32BE(4));
  const detached = Buffer.from(bytes.subarray(0, offset)); detached.writeUInt32BE(0, 8);
  return { id: tx.signature, networkBase64: bytes.toString('base64'), envelope: JSON.stringify({ t: detached.toString('base64'), m: bytes.subarray(offset).toString('base64'), opt: Buffer.from('{}').toString('base64') }) };
}
(async () => {
  await wasm.initialize(JSON.stringify({ server: { host: '127.0.0.1', port: 0, protocol: 'http', endpoint: { host: '127.0.0.1', port: 0, protocol: 'http' }, verification_threads: 1, channel_size: 100, stat_timer_in_ms: 5000, reconnection_wait_time: 1000, thread_sleep_time_in_ms: 10, block_fetch_batch_size: 1 }, peers: [], spv_mode: true, browser_mode: true, consensus: { genesis_period: 100000, heartbeat_interval: 30000, prune_after_blocks: 100, max_staker_recursions: 1 } }), '0'.repeat(63) + '2', 0, 1n, false);
  const wallet = await wasm.get_wallet(), seller = await wallet.get_public_key();
  const storeScalar = '0'.repeat(63) + '3', store = wasm.generate_public_key(storeScalar);
  const external = '0x1111111111111111111111111111111111111111';
  const amounts = moduleFunctions('node/mods/store/lib/web3-amount.js', globals());
  const quotes = moduleFunctions('node/mods/store/lib/withdrawal-quote.js', { ...globals(), QUOTE_TTL_MS: 25 * 60 * 1000 });
  const MixinModule = klass('node/mods/mixin/lib/mixinmodule.js', 'MixinModule', { ...globals(), WAValidator }, ['validateAddress']);
  const withdrawalFunctions = moduleFunctions('node/mods/mixin/lib/store-withdrawal.js', { ...globals(), MixinModule, ...quotes });
  const StoreDatabase = klass('node/mods/store/lib/database.js', 'Database', { ...globals(), ...amounts }, ['constructor', 'dbname', 'returnWeb3WithdrawalBalances', 'withImmediateTransaction']);
  const MixinDatabase = klass('node/mods/mixin/lib/database.js', 'Database', { ...globals(), require: name => { if (name === './store-withdrawal') return withdrawalFunctions; throw Error('Unexpected require ' + name); } }, ['constructor', 'dbname', 'query', 'run', 'ensureStoreWithdrawalClaims', 'withImmediateTransaction', 'recordStoreWithdrawal']);
  const storeDb = sqlite(source('node/mods/store/sql/listing_sales.sql') + source('node/mods/store/sql/withdrawals.sql'));
  const mixinDb = sqlite(source('node/mods/mixin/sql/transfers.sql'));
  const databases = { store: storeDb.facade, mixin: mixinDb.facade };
  const app = { BROWSER: false, crypto: { signMessage: (text, key) => wasm.sign_buffer(Buffer.from(text), key), verifyMessage: (text, signature, key) => wasm.verify_signature(Buffer.from(text), signature, key) },
    wallet: { getPrivateKey: async () => storeScalar, convertSaitoToNolan: value => BigInt(Math.round(Number(value) * 100000000)) },
    blockchain: { getLatestBlockId: async () => height }, storage: { returnDatabaseByName: async name => databases[name], queryDatabase: async (sql, params, name) => databases[name].all(sql, params), runDatabase: async (sql, params, name) => databases[name].run(sql, params) }, network: { peers: [] } };
  const provider = { app, publicKey: store, dbname: 'mixin', crypto_mods: [{ ticker: 'USDT' }] };
  provider.database = new MixinDatabase(app, provider);
  let providerAvailable = true, loseProviderReply = false;
  provider.receiveStoreWithdrawal = async (tx, peer) => {
    const result = await withdrawalFunctions.receiveStoreWithdrawal(provider, tx, peer);
    return loseProviderReply ? { err: 'Owned treasury reply lost after recording' } : result;
  };
  app.modules = { returnModule: name => name === 'Mixin' && providerAvailable ? provider : null };
  const mod = { app, publicKey: store, dbname: 'store', fee: '0.25', web3_withdrawal_blocks: 5, returnWeb3TreasuryPublicKey: () => store };
  mod.warehouse = { db: new StoreDatabase(app, mod) };
  const withdrawalText = source('node/mods/store/lib/withdrawal.js'), withdrawalTree = acorn.parse(withdrawalText, { ecmaVersion: 'latest' });
  const helperText = withdrawalTree.body.filter(n => n.type === 'FunctionDeclaration').map(n => withdrawalText.slice(n.start, n.end)).join('\n');
  const withdrawalClass = withdrawalTree.body.find(n => n.type === 'ClassDeclaration');
  const StoreWithdrawals = vm.runInNewContext(helperText + '\n' + withdrawalText.slice(withdrawalClass.start, withdrawalClass.end) + '\nStoreWithdrawals', { ...globals(), ...quotes, ...amounts, normalizeWithdrawalEmail: () => '', sendWithdrawalNotices() {}, require: name => { if (name === '../../mixin/lib/mixinmodule') return MixinModule; throw Error('Unexpected require ' + name); } });
  const service = new StoreWithdrawals(mod);
  const sales = [
    { signature: 'sale-a', seller, web3_ticker: 'USDT', web3_amount: '3.75', quantity_sold: 1, block_id_sold: 20, longest_chain_sold: 1, transfer_uuid: '101', inbound_payment_uuid: 'ab'.repeat(32) },
    { signature: 'sale-b', seller, web3_ticker: 'USDT', web3_amount: '1.25', quantity_sold: 2, block_id_sold: 20, longest_chain_sold: 1, transfer_uuid: '102', inbound_payment_uuid: 'cd'.repeat(32) },
    { signature: 'orphan', seller, web3_ticker: 'USDT', web3_amount: '9.5', quantity_sold: 1, block_id_sold: 20, longest_chain_sold: 0 },
    { signature: 'younger', seller, web3_ticker: 'BTC', web3_amount: '0.1', quantity_sold: 1, block_id_sold: 22, longest_chain_sold: 1 },
    { signature: 'native', seller, web3_ticker: 'SAITO', web3_amount: '50', quantity_sold: 1, block_id_sold: 20, longest_chain_sold: 1 }
  ];
  for (const row of sales) {
    const names = Object.keys(row); await storeDb.facade.run('INSERT INTO listing_sales (' + names.join(',') + ') VALUES (' + names.map(n => '$' + n).join(',') + ')', Object.fromEntries(names.map(n => ['$' + n, row[n]])));
  }
  for (const [id, amount, hash] of [[101, '3.75', 'ab'.repeat(32)], [102, '2.5', 'cd'.repeat(32)]]) await mixinDb.facade.run('INSERT INTO transfers(id,purpose,status,inbound_ticker,inbound_amount,inbound_tx_hash) VALUES($id,\'store-purchase\',\'outbound_confirmed\',\'USDT\',$amount,$hash)', { $id: id, $amount: amount, $hash: hash });
  const pending = await mod.warehouse.db.returnWeb3WithdrawalBalances(seller, height, 5);
  assert.equal(pending.pending.USDT, '15.75'); assert.equal(Object.keys(pending.eligible).length, 0);
  height = 25;
  const eligible = await mod.warehouse.db.returnWeb3WithdrawalBalances(seller, height, 5);
  assert.equal(eligible.eligible.USDT, '6.25'); assert.equal(eligible.pending.USDT, '9.5'); assert.equal(eligible.pending.BTC, '0.1');
  const quote = await service.createQuote({ seller, ticker: 'USDT', withdrawalAddress: external });
  assert.equal(quote.data.withdrawal_amount, '6.25'); assert.equal(quote.data.amount, '0.25');
  assert(app.crypto.verifyMessage(quotes.canonicalJson(quote.data), quote.signature, store));
  const slip = new wasm.WasmWalletSlip(); slip.set_amount(1000000000n); slip.set_block_id(10n); slip.set_tx_ordinal(2n); slip.set_slip_index(0); slip.set_slip_type(0); slip.set_lc(true); slip.set_spent(false);
  const key = Buffer.alloc(59); Buffer.from(base58.decode(seller)).copy(key); key.writeBigUInt64BE(10n, 33); key.writeBigUInt64BE(2n, 41); key.writeBigUInt64BE(1000000000n, 50); slip.set_utxokey(key.toString('hex')); await wallet.add_slip(slip);
  const message = { module: 'Store', request: 'store-withdrawal', data: clone(quote.data), signature: quote.signature };
  const native = await wallet.createTransaction(store, 25000000n, 5000000n, false); native.timestamp = BigInt(clock + 1000); native.data = Buffer.from(JSON.stringify(message)); await native.sign();
  const candidate = envelope(native);
  // Independent zero-fee alternative. It is not submitted alongside the funded candidate.
  mod.fee = '0';
  const zeroQuote = await service.createQuote({ seller, ticker: 'USDT', withdrawalAddress: external });
  const zeroBody = { module: 'Store', request: 'store-withdrawal', data: clone(zeroQuote.data), signature: zeroQuote.signature };
  const zeroNative = await wallet.createTransaction(store, 0n, 0n, false);
  zeroNative.timestamp = BigInt(clock + 2000); zeroNative.data = Buffer.from(JSON.stringify(zeroBody)); await zeroNative.sign();
  const zero = { quote: zeroQuote, body: zeroBody, candidate: envelope(zeroNative), timestamp: clock + 2000 };
  mod.fee = '0.25';
  const BaseTransaction = require(path.join(runtime, 'node_modules/saito-js/lib/transaction')).default;
  const Slip = require(path.join(runtime, 'node_modules/saito-js/lib/slip')).default, Saito = require(path.join(runtime, 'node_modules/saito-js/saito')).default;
  BaseTransaction.Type = wasm.WasmTransaction; Slip.Type = wasm.WasmSlip; Saito.instance = { factory: { createSlip: n => new Slip(n) } };
  class Transaction extends BaseTransaction { returnMessage() { return JSON.parse(Buffer.from(this.data).toString('utf8')); } }
  const transaction = new Transaction(native);
  const concurrent = await Promise.all([service.acceptSubmittedTransaction(transaction, candidate.envelope), service.acceptSubmittedTransaction(transaction, candidate.envelope)]);
  const submitted = concurrent[0];
  assert(concurrent.every(result => result.status === 'submitted' && result.id === submitted.id), JSON.stringify(concurrent));
  const replayed = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(replayed.id, submitted.id);
  assert.equal((await storeDb.facade.all('SELECT * FROM store_withdrawals')).length, 1);
  const after = await mod.warehouse.db.returnWeb3WithdrawalBalances(seller, height, 5);
  assert.equal(Object.keys(after.eligible).length, 0);
  const manual = await mixinDb.facade.all('SELECT * FROM transfers WHERE purpose=\'store-withdrawal\'');
  assert.equal(manual.length, 1); assert.equal(manual[0].mode, 'manual'); assert.equal(manual[0].status, 'other');
  assert(!manual[0].outbound_tx_hash, 'Registration must not pretend the external transfer happened');
  const transfers = await mixinDb.facade.all('SELECT * FROM transfers');
  clock += 25 * 60 * 1000 + 1;
  const replayAfterExpiry = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(replayAfterExpiry.status, 'submitted'); assert.equal(replayAfterExpiry.id, submitted.id);
  // Independent cloned fixture branches, never a rollback of accepted live work.
  async function resetBranch() {
    clock = 1700006000000; providerAvailable = true; loseProviderReply = false;
    await storeDb.facade.exec("DELETE FROM store_withdrawals; DELETE FROM sqlite_sequence WHERE name='store_withdrawals'; UPDATE listing_sales SET web3_payment_withdrawal_request='',web3_payment_withdrawal_publickey='',web3_payment_withdrawal_address='',web3_payment_withdrawal_created_at=0;");
    await mixinDb.facade.exec("DELETE FROM store_withdrawal_claims; DELETE FROM transfers WHERE purpose='store-withdrawal'; UPDATE sqlite_sequence SET seq=102 WHERE name='transfers';");
  }
  await resetBranch(); providerAvailable = false;
  const unavailable = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(unavailable.status, 'forward_failed');
  assert.equal((await storeDb.facade.all('SELECT * FROM store_withdrawals')).length, 1);
  providerAvailable = true;
  const recovered = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(recovered.status, 'submitted'); assert.equal(recovered.id, unavailable.id);
  await resetBranch(); clock += 25 * 60 * 1000 + 1;
  const expired = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(expired.err, 'The withdrawal quote has expired.');
  assert.equal((await storeDb.facade.all('SELECT * FROM store_withdrawals')).length, 0);
  await resetBranch(); loseProviderReply = true;
  const lostTreasuryReply = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(lostTreasuryReply.status, 'forward_failed');
  assert.equal((await mixinDb.facade.all("SELECT * FROM transfers WHERE purpose='store-withdrawal'")).length, 1);
  loseProviderReply = false; clock += 25 * 60 * 1000 + 1;
  const expiredRecovery = await service.acceptSubmittedTransaction(transaction, candidate.envelope);
  assert.equal(expiredRecovery.status, 'forward_failed'); assert.equal(expiredRecovery.err, 'The withdrawal quote has expired.');
  assert.equal((await mixinDb.facade.all("SELECT * FROM transfers WHERE purpose='store-withdrawal'")).length, 1);
  // Independent many-sale and retained-history branches use actual source quoting and SQLite eligibility.
  await resetBranch(); mod.fee = '0';
  async function replaceSales(rows) {
    await storeDb.facade.exec('DELETE FROM listing_sales');
    for (const row of rows) {
      const names = Object.keys(row);
      await storeDb.facade.run('INSERT INTO listing_sales (' + names.join(',') + ') VALUES (' + names.map(n => '$' + n).join(',') + ')', Object.fromEntries(names.map(n => ['$' + n, row[n]])));
    }
  }
  function oneSale(index, family) { return { signature: family + index, seller, web3_ticker: 'USDT', web3_amount: '1', quantity_sold: 1, block_id_sold: 20, longest_chain_sold: 1, transfer_uuid: String(2000 + index), inbound_payment_uuid: sha(Buffer.from(family + index)) }; }
  await replaceSales(Array.from({length:513}, (_,i) => oneSale(i, 'bulk')));
  const bulkQuote = await service.createQuote({ seller, ticker: 'USDT', withdrawalAddress: external });
  assert.equal(bulkQuote.data.transfer_uuids.length, 513); assert.equal(bulkQuote.data.withdrawal_amount, '513');
  const bulkBody = { module:'Store', request:'store-withdrawal', data:clone(bulkQuote.data), signature:bulkQuote.signature };
  const bulkNative = await wallet.createTransaction(store,0n,0n,false); bulkNative.timestamp=BigInt(clock+3000); bulkNative.data=Buffer.from(JSON.stringify(bulkBody)); await bulkNative.sign();
  const bulk = {quote:bulkQuote,candidate:envelope(bulkNative),timestamp:clock+3000};
  const historyQuotes = [];
  for (let i=0;i<101;i++) {
    await replaceSales([oneSale(i,'history')]);
    const original = await service.createQuote({seller,ticker:'USDT',withdrawalAddress:external});
    assert.equal(original.data.withdrawal_amount,'1'); historyQuotes.push(original);
  }
  const history = {quotes:historyQuotes,timestamp:clock+4000};
  fs.writeFileSync(output, JSON.stringify({ authority: { source: '0db0062d873369dc013046ed77c97410b5683b64', sourceHashes: hashes, saitoWasm: '0.3.15', validator: '0.5.27', validatorLockSha256: sha(fs.readFileSync(path.join(deps, 'package-lock.json'))), sqlite: storeDb.database.prepare('SELECT sqlite_version() AS version').get().version, qualification: 'Actual isolated pinned Store/Mixin quote, SQL eligibility/reservation, forwarding and manual recording algorithms, actual source schemas and SQLite3, actual validator and WASM signing. Native transaction wrapper/message adapter, owned clock, database and no-op mail. Independent reset branches are not live rollback. Direct inner-transaction admission, not full browser/peer initialization or consensus. No network, real funds or external payout.' }, seller, store, timestamp: 1700006001000, networkFee: '0.05', funding: { block: '10', ordinal: '2', index: 0, amount: '1000000000', type: 0 }, sales, pending, eligible, quote, body: message, candidate, zero, concurrent, submitted, replayed, replayAfterExpiry, unavailable, recovered, expired, lostTreasuryReply, expiredRecovery, after, bulk, history, manual: clone(manual), transfers: clone(transfers) }, null, 2) + '\n');
  console.log('PASS: official matured proceeds, signed quote, exact WASM candidate, SQLite reservation and idempotent manual request, no payout claimed');
  storeDb.database.close(); mixinDb.database.close();
})().catch(error => { console.error(error); process.exitCode = 1; });
