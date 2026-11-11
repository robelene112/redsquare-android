// Independent WASM-signed Stack protocol fixture. Public unsafe identities, never fund.
const { reference, signedReference } = require('./signed-reference.cjs');
const body = {module:'Stack',request:'create stack post request',data:{type:'stack_post',
  title:'Open networks, real writing',excerpt:'A short introduction',
  content:'# A real article\n\nOriginal **Markdown**.',image:'',imageUrl:'https://images.example.test/stack.jpg',
  images:[],tags:[],timestamp:1700001000000,subscriptionTier:'free'}};
const article=signedReference('1',body,1700001000000);
const revision=signedReference('1',{...body,data:{...body.data,parent_id:article.sig,title:'Revised writing'}},1700001000001);
const foreign=signedReference('2',{...body,data:{...body.data,parent_id:article.sig,title:'Different author'}},1700001000002);
const restricted=signedReference('1',{...body,access_hash:'1'.repeat(64)},1700001000003);
const pixel='iVBORw0KGgoAAAANSUhEUgAAAAwAAAAICAIAAABChommAAAAEUlEQVR4nGO4I6dBEDGMKgIAJ1lswfSdL+UAAAAASUVORK5CYII=';
const reader=signedReference('2',{...body,data:{...body.data,image:pixel,imageUrl:'',
  content:body.data.content+'\n\nInline `![Literal](stack:image:missing)` stays text.\n\n```md\n![Fenced example](stack:image:missing)\n```\n\n![Diagram](stack:image:diagram)\n\nEnd of the original article.',
  images:[{id:'diagram',mime:'image/png',data:pixel}]}},1700001000010);
const motion=signedReference('2',{...body,data:{...body.data,title:'A Node in a Browser Tab',image:pixel,imageUrl:'',
  content:Array.from({length:40},(_,i)=>`Paragraph ${i+1} of a signed Stack article. Reading stays within the native module.`).join('\n\n')}},1700001000020);
const quiet=signedReference('2',{...body,data:{...body.data,title:'The quiet value of an open network',image:pixel,imageUrl:''}},1700001000019);
process.stdout.write(JSON.stringify({synthetic:true,reference,article,revision,foreign,restricted,reader,motion,quiet},null,2)+'\n');
