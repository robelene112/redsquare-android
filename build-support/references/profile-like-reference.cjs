const fs = require('node:fs');
const path = require('node:path');
const {reference,signedReference} = require('./signed-reference.cjs');
const thread = JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_THREAD_REFERENCE.json'),'utf8'));
function like(scalar,target,timestamp) {
  return {...signedReference(scalar,{module:'RedSquare',request:'like tweet',data:{signature:target}},timestamp),
    field1:'RedSquareLike',field4:target};
}
const likedReply=like('1',thread.firstReply.sig,1700000000030);
const unavailableLike=like('1','a'.repeat(128),1700000000031);
const otherAuthorLike=like('2',thread.root.sig,1700000000032);
const alteredLike=JSON.parse(JSON.stringify(likedReply));
const envelope=JSON.parse(alteredLike.tx);
envelope.m=Buffer.from(JSON.stringify({module:'RedSquare',request:'like tweet',data:{signature:thread.root.sig}})).toString('base64');
alteredLike.tx=JSON.stringify(envelope);
console.log(JSON.stringify({synthetic:true,reference,likedReply,unavailableLike,otherAuthorLike,alteredLike},null,2));
