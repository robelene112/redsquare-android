const fs = require('node:fs');
const path = require('node:path');
const {reference,signedReference} = require('./signed-reference.cjs');
const thread = JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_THREAD_REFERENCE.json'),'utf8'));
const recentPosts=[];
const recentLikes=[];
for (let index=1;index<=20;index++) {
  const post=signedReference('1',{module:'RedSquare',request:'create tweet',
    data:{text:'Profile newer post '+String(index).padStart(2,'0'),parent_id:'',thread_id:''}},1700000001000+index);
  recentPosts.unshift({...post,field1:'RedSquare',field4:'',field5:post.sig,flagged:0});
  const like=signedReference('1',{module:'RedSquare',request:'like tweet',data:{signature:post.sig}},1700000002000+index);
  recentLikes.unshift({...like,field1:'RedSquareLike',field4:post.sig});
}
const olderLike={...signedReference('1',{module:'RedSquare',request:'like tweet',data:{signature:thread.firstReply.sig}},1700000000035),
  field1:'RedSquareLike',field4:thread.firstReply.sig};
console.log(JSON.stringify({synthetic:true,reference,recentPosts,recentLikes,
  olderAuthorPage:[thread.nestedReply,thread.root],olderLikes:[olderLike],likedReply:thread.firstReply},null,2));
