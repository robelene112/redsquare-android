// Public unsafe scalar1/2 vectors. Pure pinned crypto, no app/network initialization.
const fs=require('node:fs');
const path=require('node:path');
const {reference,signedReference}=require('./signed-reference.cjs');
const original=JSON.parse(fs.readFileSync(path.join(__dirname,'../../docs/research/NATIVE_FEED_REFERENCE.json'),'utf8')).archiveRow;
const action=(scalar,request,data,time)=>signedReference(scalar,{module:'RedSquare',request,data},time,[original.field2]);
const edit=action('1','edit tweet',{tweet_id:original.sig,text:'Independently edited original text'},1700000070000);
const newer=action('1','edit tweet',{tweet_id:original.sig,text:'A newer verified edit'},1700000070002);
const unrelated=action('2','edit tweet',{tweet_id:original.sig,text:'An unrelated signer must not replace this text'},1700000070003);
const wrongTarget=action('1','edit tweet',{tweet_id:'f'.repeat(128),text:'Wrong target text'},1700000070004);
function updated(command) {
 const envelope=JSON.parse(original.tx);
 envelope.opt=Buffer.from(JSON.stringify({update_tx:command.tx,edit_ts:command.created_at,updated_at:command.created_at})).toString('base64');
 return {...original,tx:JSON.stringify(envelope)};
}
const invalid=JSON.parse(edit.tx);invalid.m=Buffer.from('{}').toString('base64');
const broken={...edit,tx:JSON.stringify(invalid)};
console.log(JSON.stringify({synthetic:true,reference,original,edit,newer,unrelated,wrongTarget,
 deleted:action('1','delete tweet',{tweet_id:original.sig},1700000070005),
 unrelatedDelete:action('2','delete tweet',{tweet_id:original.sig},1700000070006),
 edited:updated(edit),newerEdited:updated(newer),unrelatedEdited:updated(unrelated),wrongTargetEdited:updated(wrongTarget),brokenEdited:updated(broken)
},null,2));
