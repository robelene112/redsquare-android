// System/framework adapters only. All Talk, dialer, stream management and Stun
// method bodies below come from independently hash-checked official sources.
window.addEventListener('unhandledrejection', e => Host.failure(String(e.reason && e.reason.stack || e.reason)));
window.addEventListener('error', e => Host.failure(String(e.message)));
const siteMessage = () => {}, salert = text => Host.failure(String(text));
const sconfirm = async () => false;
const Audio = class { play() { return Promise.resolve(); } pause() {} }; // Ring sounds only.
class ModTemplate {
  constructor(app) { this.app = app; this.publicKey = app.publicKey; this.seen = new Set(); }
  async initialize() {} handlePeerTransaction() {} buildSocial(value) { return value; }
  hasSeenTransaction(tx) { if (this.seen.has(tx.signature)) return true; this.seen.add(tx.signature); return false; }
}
class FixtureTransaction {
  constructor() { this.msg = {}; this.from = [{publicKey:Host.publicKey()}]; this.to = []; this.timestamp = Date.now(); }
  addFrom(key) { this.from = [{publicKey:key}]; }
  addTo(key) { if (!this.isTo(key)) this.to.push({publicKey:key}); }
  isTo(key) { return this.to.some(v => v.publicKey === key); }
  isFrom(key) { return this.from.some(v => v.publicKey === key); }
  returnMessage() { return this.msg; }
  async sign() {
    this.serialized = Host.sign(JSON.stringify(this.msg), JSON.stringify(this.to.map(v => v.publicKey)), this.timestamp);
    this.signature = JSON.parse(Host.inspect(this.serialized)).signature;
  }
  toJson() { return this.serialized || Host.unsigned(JSON.stringify(this.msg), JSON.stringify(this.to.map(v => v.publicKey)), this.timestamp); }
  serialize_to_web() { return this.toJson(); }
  deserialize_from_web(app, envelope) {
    const value = JSON.parse(Host.inspect(envelope));
    this.msg = value.body; this.from = [{publicKey:value.author}]; this.to = value.recipients.map(publicKey => ({publicKey}));
    this.timestamp = value.timestamp; this.signature = value.signature; this.serialized = envelope;
  }
}
const Transaction = FixtureTransaction;
const saito = {default:{transaction:FixtureTransaction}};
class SaitoOverlay {
  show(html) { document.getElementById('dialer').innerHTML = html; }
  blockClose() {} remove() { document.getElementById('dialer').innerHTML = ''; }
}
class SaitoUser { render() {} }
const DialerTemplate = (app, mod, making) => '<div class="stun-minimal-appspace"><div class="contact"></div><div class="video_switch"><input id="video_call_switch" type="checkbox" checked></div><div class="stun-phone-notice"></div>' +
  (making ? '<button id="startcall">Call</button>' : '<button id="answercall">Answer</button><button id="rejectcall">Decline</button>') + '</div>';
class CallInterfaceVideo { destroy() {} close() {} }
const CallInterfaceFloat = CallInterfaceVideo;
class CallDiagnostics { record() {} }
const keys = new Map(), listeners = new Map();
const emit = (event, ...args) => {
  for (const listener of listeners.get(event) || []) {
    try { Promise.resolve(listener(...args)).catch(e => Host.failure(event + ': ' + String(e.stack || e))); }
    catch (e) { Host.failure(event + ': ' + String(e.stack || e)); }
  }
};
const app = {
  BROWSER:1, publicKey:Host.publicKey(), options:{stun:{settings:{privacy:'all'}}},
  connection:{on(name, handler) { if (!listeners.has(name)) listeners.set(name, []); listeners.get(name).push(handler); }, emit},
  wallet:{async createUnsignedTransactionWithDefaultFee(key) { const tx = new FixtureTransaction(); if (key) tx.addTo(key); return tx; }},
  keychain:{returnKey:key => keys.get(key), addKey(key, value) { keys.set(key, Object.assign(keys.get(key) || {}, value)); }, saveKeys() {},
    addWatchedPublicKey() {}, returnUsername:key => key, hasPublicKey:() => true, hasSharedSecret:() => true},
  browser:{returnURLParameter:() => '', lockNavigation() {}, unlockNavigation() {}, formatTime:() => ({minutes:0,seconds:0})},
  crypto:{stringToBase64:text => btoa(text), base64ToString:text => atob(text)},
  storage:{saveOptions() {}},
  network:{propagateTransaction() {}, async getPeers() { return [{publicKey:Host.relayKey(),status:'connected'}]; },
    sendRequestAsTransaction(request, transaction) { if (request !== 'relay peer message') throw Error('Unexpected owned RPC'); Host.relay(transaction); }}, // Public/onchain propagation deliberately disabled.
  modules:{returnFirstRespondTo:() => stun.respondTo('peer-manager'), returnModulesRespondingTo:() => [stun],
    async handlePeerTransaction(tx) { await talk.handlePeerTransaction(app, tx); await stun.handlePeerTransaction(app, tx); }}
};
// INSERT_PINNED_CLASSES
const stun = new Stun(app); stun.servers = [];
const relay = new Relay(app);
const talk = new Videocall(app);
const audioContext = new (window.AudioContext || window.webkitAudioContext)();
const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480;
const paint = canvas.getContext('2d'); let tick = 0;
setInterval(() => {
  paint.fillStyle = Host.colour(); paint.fillRect(0,0,640,480);
  paint.fillStyle = 'white'; paint.fillRect((tick++ * 11) % 600, 20, 40, 40);
}, 50);
// Controlled device inputs: real canvas video tracks and real WebAudio PCM tracks.
// These cross actual RTCPeerConnection codecs/DTLS/RTP, not mocked track/connection flags.
Object.defineProperty(navigator.mediaDevices, 'getUserMedia', {value:async constraints => {
  Host.captured(); await audioContext.resume();
  const stream = new MediaStream();
  if (constraints.video) canvas.captureStream(15).getVideoTracks().forEach(t => stream.addTrack(t));
  if (constraints.audio) {
    const oscillator = audioContext.createOscillator(), gain = audioContext.createGain(), destination = audioContext.createMediaStreamDestination();
    oscillator.frequency.value = Host.frequency(); gain.gain.value = 0.2;
    oscillator.connect(gain).connect(destination); oscillator.start();
    destination.stream.getAudioTracks().forEach(track => { track.addEventListener('ended', () => oscillator.stop()); stream.addTrack(track); });
  }
  return stream;
}});
app.connection.on('peer-toggle-audio-status', value => Host.peerAudio(value.public_key, value.enabled));
app.connection.on('videocall-stream', (peer, stream) => {
  if (!stream || peer === 'local') return;
  let video = document.getElementById('remote-' + peer);
  if (!video) { video = document.createElement('video'); video.id = 'remote-' + peer; video.muted = true; video.autoplay = true; document.body.appendChild(video); }
  // Audio and video arrive as separate track events on the same MediaStream.
  // Reassigning that identical source aborts the outstanding play() request.
  if (video.srcObject !== stream) {
    video.srcObject = stream; video.play().catch(e => Host.failure(String(e)));
  }
  const audio = stream.getAudioTracks()[0];
  if (audio && !video.analysis) {
    const analyser = audioContext.createAnalyser(); analyser.fftSize = 1024;
    audioContext.createMediaStreamSource(stream).connect(analyser); video.analysis = analyser;
  }
});
window.nativeEnvelope = async envelope => {
  try { const tx = new FixtureTransaction(); tx.deserialize_from_web(app, envelope); await app.modules.handlePeerTransaction(tx); }
  catch (e) { Host.failure(String(e.stack || e)); }
};
window.joinRoom = async (room, video) => {
  talk.room_obj = room;
  emit('stun-init-call-interface', {ui:video ? 'video':'voice', audio:true, video});
  emit('start-stun-call');
};
window.dialNative = async room => { talk.room_obj = room; talk.dialer.render(Host.nativeKey(), true); document.getElementById('startcall').click(); };
window.answer = () => document.getElementById('answercall').click();
window.decline = () => document.getElementById('rejectcall').click();
window.leave = () => emit('stun-disconnect');
window.toggleAudio = () => emit('stun-toggle-audio');
window.toggleVideo = () => emit('stun-toggle-video');
window.closeFixture = () => { if (talk.streams) talk.streams.leaveCall(); for (const pc of stun.peers.values()) pc.close(); audioContext.close(); };
setInterval(async () => {
  for (const video of document.querySelectorAll('video')) {
    if (video.readyState < 2) continue;
    const sample = document.createElement('canvas'); sample.width = 8; sample.height = 8;
    const context = sample.getContext('2d'); context.drawImage(video,0,0,8,8);
    const data = context.getImageData(0,0,8,8).data;
    let nonBlack = 0; for (let i=0;i<data.length;i+=4) nonBlack += data[i]+data[i+1]+data[i+2];
    let rms = 0;
    if (video.analysis) { const pcm = new Float32Array(1024); video.analysis.getFloatTimeDomainData(pcm); rms = Math.sqrt(pcm.reduce((v,a) => v+a*a,0)/pcm.length); }
    Host.media(video.id.slice(7), video.videoWidth, video.videoHeight, nonBlack, rms);
  }
}, 200);
(async () => { await relay.initialize(app); await talk.initialize(app); Host.ready(); })();
