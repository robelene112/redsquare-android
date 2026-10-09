const fs=require('node:fs');
const path=require('node:path');
const {reference,signedReference}=require('./signed-reference.cjs');
const root=JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
const newerUnrelated=[];
for(let index=1;index<=100;index++) {
  const post=signedReference('2',{module:'RedSquare',request:'create tweet',data:{
    text:'Newer unrelated cached post '+String(index).padStart(3,'0'),parent_id:'',thread_id:''}},1700000030000+index);
  newerUnrelated.unshift({...post,field1:'RedSquare',field4:'',field5:post.sig,flagged:0});
}
console.log(JSON.stringify({synthetic:true,reference,root,newerUnrelated},null,2));
