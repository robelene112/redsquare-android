// Pure pinned web receiver boundary. No Saito initialization or network.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto');
const root='/opt/saito/node/node_modules/';
const ts=require(root+'typescript'),marked=require(root+'marked'),sanitizeHtml=require(root+'sanitize-html'),sanitizer=require(root+'sanitizer'),emoji=require(root+'node-emoji');
const methods=['normalizeLegacySmartQuotedAnchors','sanitize','urlRegexp','numberFilter','formatUrlForDisplay','escapeHTML','hrefFromAutolink','isSafeHref'];
const pins=[['9a4ca7e9442eb5e872168ced361ce741d7d139d7','browser-A.ts','e85b67cc138dae5047bb855bf0961b2852a808fc7be3ffc5bc0479a1281bab38'],['214504ecece76d1cd20a245fe4468eccbfca2951','browser-B.ts','8ba723fc2deea89999275878b3590510263d655b176fd46e270ab9f8057993d3']];
const gif=process.argv.includes('--native-private-gif')||process.argv.includes('--native-group-gif');
const caption=gif?'Keep my Chat draft 😀 <&> "quoted"\nA second line':'Photo caption 😀 <&> "quoted"\nA second line';
const escape=text=>text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
const image=JSON.parse(fs.readFileSync('/reference/images-reference.json','utf8')).images.red;
const group=process.argv.includes('--native-group-photo')||process.argv.includes('--native-group-gif');
const native=group||gif||process.argv.includes('--native-photo')?JSON.parse(fs.readFileSync(gif?(group?'/reference/native-group-gif-proof.json':'/reference/native-private-gif-proof.json'):group?'/reference/native-group-photo-proof.json':'/reference/native-private-photo-proof.json','utf8')):null;
const media=native?(gif?native.nativeGif:native.nativePhoto):null;
if(native){
 assert.equal(native.independentWasmSignatureMatches,true);assert.equal(media.caption,caption);
 if(group)assert.equal(native.actualPinnedConstructorsMatchNativeEnvelope,true);
 else{assert.equal(native.existingCryptoRecipientDecrypts,true);assert.equal(native.existingChatReceiverAcceptsNativePlaintext,true);}
}
const message=native?media.message:`<img class="img-prev" src="${image}">\n<div>${escape(caption)}</div>`;
const vectors=[];
for(const [pin,file,expected] of pins){
 const source=fs.readFileSync('/reference/'+file,'utf8');assert.equal(crypto.createHash('sha256').update(source).digest('hex'),expected);
 const ast=ts.createSourceFile(file,source,ts.ScriptTarget.ES2022,true),cls=ast.statements.find(node=>ts.isClassDeclaration(node)&&node.name.text==='Browser');assert(cls);
 const browser={};for(const name of methods){const method=cls.members.find(node=>ts.isMethodDeclaration(node)&&node.name.getText(ast)===name);assert(method,name);browser[name]=vm.runInNewContext(`({${method.getText(ast)}}).${name}`,{marked,sanitizeHtml,sanitizer,emoji,console,URL});}
 const sanitized=browser.sanitize(message,true);assert(sanitized.includes('class="img-prev"'));if(!native)assert(sanitized.includes(image));
 // returnChatBody performs this newline conversion after the actual sanitize call.
 const html=sanitized.split('\n').join('<br>');
 vectors.push({pin,browserSha256:expected,message,caption,html,...(gif?{source:media.source}:{})});
}
assert.equal(vectors[0].html,vectors[1].html);
console.log(JSON.stringify({scope:'Actual pinned Browser.sanitize(msg,true), followed by inspected returnChatBody newline conversion. No initialized Saito app claim.',...(native?{nativeEnvelopeSha256:native.nativeEnvelopeSha256}:{}),...(gif?{mediaKind:'gif'}:{}),vectors},null,2));
