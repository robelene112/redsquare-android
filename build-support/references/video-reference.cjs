// Independent public unsafe identities. Pure pinned crypto, never initialize Saito.
const { reference, signedReference } = require('./signed-reference.cjs');
const text = signedReference('1', {module:'RedSquare',request:'create tweet',data:{text:'Signed YouTube reference https://www.youtube.com/watch?v=M7lc1UVf-VE'}}, 1700000900010);
const linked = signedReference('1', {module:'RedSquare',request:'create tweet',data:{text:'Explicit signed YouTube link',link:'https://youtube.com/shorts/M7lc1UVf-VE'}}, 1700000900011);
const malformed = signedReference('1', {module:'RedSquare',request:'create tweet',data:{text:'Readable post with malformed link',link:{url:'javascript:alert(1)'}}}, 1700000900012);
const quote = signedReference('2', {module:'RedSquare',request:'create tweet',data:{text:'Signed video quote',link:'https://youtu.be/bHQqvYy5KYo',signature:linked.sig,retweet_tx:linked.tx,embedded:{signature:linked.sig,publicKey:linked.field2,text:'Explicit signed YouTube link',images:[]}}}, 1700000900013, [linked.field2]);
process.stdout.write(JSON.stringify({synthetic:true,reference,text,linked,malformed,quote},null,2)+'\n');
