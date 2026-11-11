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
const pixel='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jB4kAAAAASUVORK5CYII=';
const reader=signedReference('2',{...body,data:{...body.data,image:pixel,imageUrl:'',
  content:body.data.content+'\n\n![Diagram](stack:image:diagram)\n\nEnd of the original article.',
  images:[{id:'diagram',mime:'image/png',data:pixel}]}},1700001000010);
process.stdout.write(JSON.stringify({synthetic:true,reference,article,revision,foreign,restricted,reader},null,2)+'\n');
