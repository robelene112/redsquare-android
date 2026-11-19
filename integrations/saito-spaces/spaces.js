'use strict';
const ModTemplate = require('../../lib/templates/modtemplate');
const PeerService = require('saito-js/lib/peer_service').default;
const SpacesBridge = require('./bridge.cjs');

/** Explicit opt-in adapter. Copy both source files to node/mods/spaces only
 * after the owner authorizes activation and the rehearsal has passed. */
class Spaces extends ModTemplate {
  constructor(app) {
    super(app);
    this.name = 'Spaces';
    this.slug = 'spaces';
    this.description = 'Durable encrypted community event transport';
    this.bridge = null;
    this.available = false;
  }

  async initialize(app) {
    await super.initialize(app);
    if (app.BROWSER || !process.env.SAITO_SPACES_SERVICE_URL) return;
    try {
      this.bridge = new SpacesBridge(process.env.SAITO_SPACES_SERVICE_URL, await app.wallet.getPublicKey());
      this.available = await this.bridge.ready();
    } catch (_) {
      this.bridge = null;
      this.available = false;
      // Invalid optional configuration must not prevent other node modules
      // from starting. No configured URL or wallet material enters this log.
      console.warn('Spaces is unavailable: check the local service configuration');
    }
  }

  returnServices() {
    return !this.app.BROWSER && this.available ? [new PeerService(null, 'spaces-v1')] : [];
  }

  async handlePeerTransaction(app, tx = null, peer, callback) {
    if (!tx || tx.returnMessage()?.module !== 'Spaces') return 0;
    const body = tx.returnMessage();
    let responded = false;
    const respond = async value => {
      if (responded || typeof callback !== 'function') return;
      responded = true;
      await callback(value);
    };
    try {
      if (app.BROWSER || !this.bridge || body.version !== 1 ||
          !['event', 'challenge', 'read'].includes(body.request) ||
          !peer?.publicKey || tx.from?.[0]?.publicKey !== peer.publicKey) {
        throw Error('Spaces request is not available to this peer');
      }
      // Actual maintained web serialization retains original signed message
      // bytes. Callback receives an object so Saito serializes it exactly once.
      const original = tx.serialize_to_web(app);
      await respond(await this.bridge.exchange(original));
    } catch (_) {
      await respond({spaces_error: 'unavailable'});
    }
    return 1;
  }
}
module.exports = Spaces;
