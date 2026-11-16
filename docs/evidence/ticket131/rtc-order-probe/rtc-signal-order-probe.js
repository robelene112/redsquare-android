const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const errors = [], calls = [];
class Rtc {
  constructor() { this.signalingState='have-local-offer'; this.connectionState='connected'; }
  createDataChannel() { return {readyState:'open'}; }
  async setRemoteDescription(description) {
    calls.push('remote-start:'+description.type);
    await Promise.resolve();
    this.remoteDescription=description;
    this.signalingState=description.type==='answer'?'stable':'have-remote-offer';
    calls.push('remote-end:'+description.type);
  }
  async setLocalDescription(description) {
    if(description.type==='rollback') throw new TypeError("The provided value 'rollback' is not a valid enum value of type RTCSdpType.");
    this.localDescription=description;this.signalingState='stable';calls.push('local:'+description.type);
  }
  async createAnswer() { return {type:'answer',sdp:'owned-fixture-answer'}; }
  async addIceCandidate() {}
}
const context = {window:{}, RTCPeerConnection:Rtc, Fileshare:class {},
  Host:{publicKey:()=> 'a',nativeKey:()=> 'b',ready(){},signal(){},failure:e=>errors.push(e)},Uint8Array};
vm.runInNewContext(fs.readFileSync(process.argv[2],'utf8'),context);
(async()=>{
  if(process.argv[3]==='real-glare') {
    await context.window.nativeSignal({request:'peer-description',description:{type:'offer',sdp:'owned-colliding-offer'}});
    console.log(JSON.stringify({negativeControl:'genuine collision remains an error on legacy RTC',calls,errors}));
    assert.equal(errors.length,1);assert.match(errors[0],/rollback.*not a valid enum/);return;
  }
  await Promise.all([
    context.window.nativeSignal({request:'peer-description',description:{type:'answer',sdp:'owned-answer'}}),
    context.window.nativeSignal({request:'peer-description',description:{type:'offer',sdp:'owned-following-offer'}})
  ]);
  console.log(JSON.stringify({calls,errors}));
  assert.deepEqual(errors,[], 'No false glare from concurrently delivered answer and subsequent valid offer');
  assert.deepEqual(calls,['remote-start:answer','remote-end:answer','remote-start:offer','remote-end:offer','local:answer']);
})();
