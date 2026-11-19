'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const SpacesBridge = require('../bridge.cjs');

test('the local bridge advertises only the matching service and preserves the received envelope', async t => {
  const originals = [];
  let unreadAvailable = false;
  const original = JSON.stringify({t: 'fixture-request-header', m: 'fixture-ciphertext', opt: 'e30='});
  const receipt = {t: 'fixture-receipt-header', m: 'fixture-signed-receipt', opt: 'e30='};
  const server = http.createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json');
    if (request.method === 'GET' && request.url === '/v1/readiness') {
      response.end(JSON.stringify({capability: 'spaces-v1', service_key: 'service-key', schema: 4, state: 'ready', file_hosting: false, features: unreadAvailable ? ['unread-v1'] : []}));
    } else if (request.method === 'POST' && request.url === '/v1/exchange') {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      originals.push(Buffer.concat(chunks).toString('utf8'));
      response.end(JSON.stringify(receipt));
    } else { response.statusCode = 404; response.end('{}'); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); });
  const endpoint = `http://127.0.0.1:${server.address().port}`;
  const bridge = new SpacesBridge(endpoint, 'service-key');
  assert.equal(await bridge.ready(), true);
  assert.equal(bridge.supportsUnread, false);
  unreadAvailable = true;
  assert.equal(await bridge.ready(), true);
  assert.equal(bridge.supportsUnread, true);
  const mismatched = new SpacesBridge(endpoint, 'another-key');
  assert.equal(await mismatched.ready(), false);
  assert.equal(mismatched.supportsUnread, false);
  unreadAvailable = false;
  assert.equal(await bridge.ready(), true);
  assert.equal(bridge.supportsUnread, false);
  assert.equal(await new SpacesBridge(endpoint, 'another-key').ready(), false);
  assert.deepEqual(await bridge.exchange(original), receipt);
  assert.deepEqual(originals, [original]);
});
