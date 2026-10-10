// Public unsafe reference identities only. Pure pinned WASM, no initialized app.
const {reference,signedReference}=require('./signed-reference.cjs');
const images={"red": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAICAIAAABChommAAAAEUlEQVR4nGO4I6dBEDGMKgIAJ1lswfSdL+UAAAAASUVORK5CYII=", "blue": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAICAIAAABChommAAAAEUlEQVR4nGMQsblDEDGMKgIAw+hwgU2NSckAAAAASUVORK5CYII=", "green": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAICAIAAABChommAAAAEUlEQVR4nGOQ2+dGEDGMKgIA1MpswTjj3mYAAAAASUVORK5CYII="};
const ordinary=signedReference('1',{module:'RedSquare',request:'create tweet',data:{text:'Signed image reference',images:[images.red,images.blue]}},1700000080000);
const body={module:'RedSquare',request:'create tweet',data:{text:'Image quote commentary',images:[images.green],signature:ordinary.sig,retweet_tx:ordinary.tx,embedded:{signature:ordinary.sig,publicKey:ordinary.field2,created_at:ordinary.created_at,text:'Signed image reference',images:[images.red,images.blue]}}};
const dual=signedReference('2',body,1700000080001,[ordinary.field2]);
const conflicting=signedReference('2',{...body,data:{...body.data,embedded:{...body.data.embedded,images:[images.blue,images.red]}}},1700000080002,[ordinary.field2]);
const malformed=signedReference('1',{module:'RedSquare',request:'create tweet',data:{text:'Text survives unsafe image entries',images:[7,null,{},'file:///private/secret','data:image/svg+xml;base64,PHN2Zz4=','//unsafe.test/image.png',' https://example.test/image.png ', '/media/reference.png',images.red]}},1700000080003);
console.log(JSON.stringify({synthetic:true,reference,images,ordinary,dual,conflicting,malformed},null,2));
