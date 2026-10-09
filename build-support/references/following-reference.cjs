const fs = require('node:fs');
const path = require('node:path');
const {reference,signedReference} = require('./signed-reference.cjs');
const root=JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
const other=signedReference('2',{module:'RedSquare',request:'create tweet',
  data:{text:'Another account reference post',parent_id:'',thread_id:''}},1700000000005);
const otherRoot={...other,field1:'RedSquare',field4:'',field5:other.sig,flagged:0};
console.log(JSON.stringify({synthetic:true,reference,root,otherRoot},null,2));
