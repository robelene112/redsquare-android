// Test-only RTC/browser adapters around the actual hash-pinned Saito Fileshare class.
// Wallet signing is supplied by the public unsafe native fixture; independent wallet vectors live in core.
const ModTemplate = class { handlePeerTransaction() {} };
const encode = bytes => {
  let text = '';
  for (let i = 0; i < bytes.length; i += 8192) text += String.fromCharCode(...bytes.subarray(i, i + 8192));
  return btoa(text);
};
const decode = text => Uint8Array.from(atob(text), c => c.charCodeAt(0));
const Buffer = {from(data, type) {
  const bytes = type === 'base64' ? decode(data) : new Uint8Array(data);
  bytes.toString = () => encode(bytes);
  return bytes;
}};
class FileReceiveOverlay {
  constructor(app, mod, id) { this.mod = mod; this.id = id; }
  render() { this.mod.prepareToReceive(this.id); }
  renderStats() {} onConnectionSuccess() {} onCancel() {} remove() {}
  finishTransfer(file) {
    const data = new Uint8Array(file.receivedSize); let offset = 0;
    for (const bytes of file.receiveBuffer) { data.set(bytes, offset); offset += bytes.byteLength; }
    if (offset !== file.size) throw Error('Wrong completed size');
    Host.complete(encode(data));
  }
}
class FileShareOverlay {
  render() {} updateFileData() {} onPeerAccept() {} beginTransfer() {} renderStats() {}
  updateRStats() {} onPeerReject() {} onCancel() {} onConnectionSuccess() {}
  finishTransfer() { Host.sent(); }
}
class FixtureTransaction {
  constructor(body) {
    if (body) {
      this.msg = body.body; this.from = [{publicKey:body.author, public_key:body.author}];
      this.to = body.recipients.map(key => ({publicKey:key, public_key:key}));
    }
  }
  isTo(key) { return this.to.some(value => value.publicKey === key); }
  returnMessage() { return this.msg; }
  async sign() { this.serialized = Host.sign(JSON.stringify(this.msg), false); }
  serialize_to_web() { return this.serialized; }
}
const app = {
  publicKey: Host.publicKey(), BROWSER: 1,
  browser: {lockNavigation() {}, unlockNavigation() {}, formatTime() { return {minutes:0,seconds:1}; }},
  wallet: {async createUnsignedTransaction() { return new FixtureTransaction(); }},
  connection: {emit(name, value) {
    if (name === 'relay-send-message') {
      dc.send(Host.sign(JSON.stringify({module:'Relay', request:value.request, data:value.data}), true));
    }
  }}
};
const siteMessage = () => {};
let holdReceipt = false, holdChunks = false, chunksSent = 0;
let heldReceipts = [], heldChunks = [];
window.holdTransfer = (receipts, chunks) => { holdReceipt = receipts; holdChunks = chunks; chunksSent = 0; };
window.releaseTransfer = () => {
  holdReceipt = false; holdChunks = false;
  for (const text of [...heldReceipts, ...heldChunks]) dc.send(text);
  heldReceipts = []; heldChunks = [];
};
let rtc, dc, makingOffer = false, ignored = false, queuedCandidates = [];
let pendingFile;
const polite = Host.publicKey() < Host.nativeKey();
function signal(request, details = {}) { Host.signal(JSON.stringify({request, ...details})); }
function connection(initiator = false) {
  if (rtc && ['new','connecting','connected'].includes(rtc.connectionState) && dc.readyState !== 'closed') return;
  if (rtc) rtc.close();
  rtc = new RTCPeerConnection({iceServers:[]});
  dc = rtc.createDataChannel('data-channel', {negotiated:true, id:42});
  rtc.onicecandidate = e => { if (e.candidate) signal('peer-candidate', {iceCandidate:{
    candidate:e.candidate.candidate, sdpMid:e.candidate.sdpMid, sdpMLineIndex:e.candidate.sdpMLineIndex}}); };
  rtc.onnegotiationneeded = async () => {
    // Match pinned Stun: a lower-key receiver also starts negotiated-channel setup.
    // File direction does not determine the SDP offerer.
    if (!initiator && !polite) return;
    try { makingOffer = true; await rtc.setLocalDescription(await rtc.createOffer());
      signal('peer-description', {description:{type:rtc.localDescription.type,sdp:rtc.localDescription.sdp}});
    } catch (e) { Host.failure(String(e)); } finally { makingOffer = false; }
  };
  dc.onopen = () => { Host.opened(); if (pendingFile) { const action = pendingFile; pendingFile = null; action(); } };
  dc.onmessage = async e => {
    try { await fss.handlePeerTransaction(app, new FixtureTransaction(JSON.parse(Host.inspect(e.data)))); }
    catch (e) { Host.failure(String(e)); }
  };
}
window.nativeSignal = async body => {
  try {
    if (body.request === 'peer-left') { if (rtc) rtc.close(); return; }
    connection();
    if (body.request === 'peer-description') {
      const description = body.description;
      const collision = description.type === 'offer' && (makingOffer || rtc.signalingState !== 'stable');
      ignored = collision && !polite;
      if (ignored) return;
      if (collision) await rtc.setLocalDescription({type:'rollback'});
      await rtc.setRemoteDescription(description);
      for (const candidate of queuedCandidates) await rtc.addIceCandidate(candidate);
      queuedCandidates = [];
      if (description.type === 'offer') {
        await rtc.setLocalDescription(await rtc.createAnswer());
        signal('peer-description', {description:{type:rtc.localDescription.type,sdp:rtc.localDescription.sdp}});
      }
    } else if (body.request === 'peer-candidate' && !ignored) {
      if (rtc.remoteDescription) await rtc.addIceCandidate(body.iceCandidate);
      else queuedCandidates.push(body.iceCandidate);
    }
  } catch (e) { Host.failure(String(e)); }
};
const fss = Object.create(Fileshare.prototype);
Object.assign(fss, {app, publicKey:Host.publicKey(), name:'Fileshare', chunkSize:64256, incoming:{}, outgoing_files:{},
  stun:{hasConnection:() => dc && dc.readyState === 'open', sendTransaction:(peer, tx) => {
    const text = tx.serialize_to_web(app);
    if (tx.msg.request === 'read receipt') {
      Host.chunkReceived();
      if (holdReceipt) { heldReceipts.push(text); return; }
    }
    if (tx.msg.request === 'share file' && holdChunks && chunksSent++ > 0) { heldChunks.push(text); return; }
    dc.send(text);
  }}});
function shareFile(file, id) {
  pendingFile = () => {
    fss.outgoing_files[id] = {recipient:Host.nativeKey(), overlay:new FileShareOverlay(), offset:0,
      bytesPrev:0, timestampPrev:0, byteRateMax:0};
    fss.addFileUploader(file, id);
  };
  signal('peer-joined'); connection(true);
  if (dc.readyState === 'open') { const action = pendingFile; pendingFile = null; action(); }
}
window.sendFile = (base64, name, id) => shareFile(new File([decode(base64)], name, {type:'application/octet-stream'}), id);
window.sendGeneratedFile = (size, name, id) => {
  const bytes = new Uint8Array(size);
  for (let i = 0; i < size; i++) bytes[i] = (i * 13 + 7) % 251;
  shareFile(new File([bytes], name, {type:'application/octet-stream'}), id);
};
window.closePeer = () => { if (rtc) rtc.close(); };
Host.ready();
