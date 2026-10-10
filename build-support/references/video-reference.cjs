// Independent public unsafe identities. Pure pinned crypto, never initialize Saito.
const { reference, signedReference } = require('./signed-reference.cjs');
const text = signedReference('1', {module:'RedSquare',request:'create tweet',data:{text:'Signed YouTube reference https://www.youtube.com/watch?v=M7lc1UVf-VE'}}, 1700000900010);
const linked = signedReference('1', {module:'RedSquare',request:'create tweet',data:{text:'Explicit signed YouTube link',link:'https://youtube.com/shorts/M7lc1UVf-VE'}}, 1700000900011);
const malformed = signedReference('1', {module:'RedSquare',request:'create tweet',data:{text:'Readable post with malformed link',link:{url:'javascript:alert(1)'}}}, 1700000900012);
const quote = signedReference('2', {module:'RedSquare',request:'create tweet',data:{text:'Signed video quote',link:'https://youtu.be/bHQqvYy5KYo',signature:linked.sig,retweet_tx:linked.tx,embedded:{signature:linked.sig,publicKey:linked.field2,text:'Explicit signed YouTube link',images:[]}}}, 1700000900013, [linked.field2]);
// Literal expectations also checked through both pinned Browser/Tweet methods.
const candidates = [
  ['Watch youtu.be/M7lc1UVf-VE','M7lc1UVf-VE'],
  ['See https://example.com/info then https://youtu.be/M7lc1UVf-VE','M7lc1UVf-VE'],
  ['See https://youtu.be/M7lc1UVf-VE then https://example.com/info',null],
  ['https://youtu.be/M7lc1UVf-VE https://youtu.be/bHQqvYy5KYo','bHQqvYy5KYo'],
].map(([text,id],i) => ({...signedReference('1',{module:'RedSquare',request:'create tweet',data:{text}},1700000900014+i),videoId:id}));
process.stdout.write(JSON.stringify({synthetic:true,reference,text,linked,malformed,quote,candidates},null,2)+'\n');
