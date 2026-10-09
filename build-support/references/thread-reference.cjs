const fs = require('node:fs');
const path = require('node:path');
const {reference,signedReference} = require('./signed-reference.cjs');
const root = JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
function reply(scalar,text,parent,timestamp,threadId=root.sig) {
  return {...signedReference(scalar,{module:'RedSquare',request:'create tweet',
    data:{text,parent_id:parent,thread_id:threadId}},timestamp),
    updated_at:timestamp,field1:'RedSquare',field4:parent,field5:threadId,flagged:0};
}
const firstReply=reply('2','Independent Saito reply',root.sig,1700000000010);
const nestedReply=reply('1','Nested Saito reply',firstReply.sig,1700000000020);
const malformedThread=reply('2','Malformed reference thread','',1700000000040,'invalid/thread');
const tamperedReply=JSON.parse(JSON.stringify(firstReply));
const changed=JSON.parse(tamperedReply.tx);
changed.m=Buffer.from(JSON.stringify({module:'RedSquare',request:'create tweet',data:{text:'Forged reply'}})).toString('base64');
tamperedReply.tx=JSON.stringify(changed);
console.log(JSON.stringify({synthetic:true,reference,root,firstReply,nestedReply,tamperedReply,malformedThread},null,2));
