'use strict';

const MAX_REQUEST = 2 * 1024 * 1024;
const MAX_REPLY = 6 * 1024 * 1024;

/** Loopback transport only. The service validates original Saito signatures;
 * clients authenticate signed receipts independently of this bridge. */
module.exports = class SpacesBridge {
  constructor(endpoint, serviceKey) {
    const url = new URL(endpoint);
    if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || !url.port ||
        url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw Error('Spaces requires an explicit loopback service endpoint');
    }
    if (typeof serviceKey !== 'string' || !serviceKey) throw Error('Spaces service identity is required');
    this.endpoint = url.origin;
    this.serviceKey = serviceKey;
    this.supportsUnread = false;
    this.supportsArchive = false;
    this.supportsRoles = false;
    this.supportsPublicChannelEditing = false;
    this.supportsPrivateChannelAccess = false;
    this.supportsPrivateChannelEditing = false;
    this.supportsMembershipManagement = false;
  }

  async ready() {
    this.supportsUnread = false;
    this.supportsArchive = false;
    this.supportsRoles = false;
    this.supportsPublicChannelEditing = false;
    this.supportsPrivateChannelAccess = false;
    this.supportsPrivateChannelEditing = false;
    this.supportsMembershipManagement = false;
    try {
      const body = await this.request('/v1/readiness', {method: 'GET'}, 4096, 2000);
      const ready = body.capability === 'spaces-v1' && body.service_key === this.serviceKey &&
        body.schema === 4 && body.state === 'ready' && body.file_hosting === false;
      this.supportsUnread = ready && Array.isArray(body.features) && body.features.includes('unread-v1');
      this.supportsArchive = ready && Array.isArray(body.features) && body.features.includes('channel-archive-v1');
      this.supportsRoles = ready && Array.isArray(body.features) && body.features.includes('role-management-v1');
      this.supportsPublicChannelEditing = ready && Array.isArray(body.features) && body.features.includes('public-channel-edit-v1');
      this.supportsPrivateChannelAccess = ready && Array.isArray(body.features) && body.features.includes('private-channel-access-v1');
      this.supportsPrivateChannelEditing = ready && Array.isArray(body.features) && body.features.includes('private-channel-edit-v1');
      this.supportsMembershipManagement = ready && Array.isArray(body.features) && body.features.includes('membership-management-v1');
      return ready;
    } catch (_) { return false; }
  }

  async exchange(original) {
    if (typeof original !== 'string' || Buffer.byteLength(original, 'utf8') > MAX_REQUEST) {
      throw Error('Spaces request exceeds the transport bound');
    }
    const body = await this.request('/v1/exchange', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: original
    }, MAX_REPLY, 10000);
    if (!body || typeof body.t !== 'string' || typeof body.m !== 'string' || typeof body.opt !== 'string') {
      throw Error('Spaces service did not return an envelope');
    }
    return body;
  }

  async request(path, options, limit, milliseconds) {
    const response = await fetch(this.endpoint + path, {...options,
      signal: AbortSignal.timeout(milliseconds), redirect: 'error'});
    if (response.status !== 200 || !response.body) {
      await response.body?.cancel();
      throw Error('Spaces service did not accept the request');
    }
    const chunks = [];
    let bytes = 0;
    const reader = response.body.getReader();
    try {
      while (true) {
        const next = await reader.read();
        if (next.done) break;
        bytes += next.value.byteLength;
        if (bytes > limit) throw Error('Spaces reply exceeds the transport bound');
        chunks.push(Buffer.from(next.value));
      }
      return JSON.parse(Buffer.concat(chunks, bytes).toString('utf8'));
    } finally {
      try { await reader.cancel(); } finally { reader.releaseLock(); }
    }
  }
};
