// Independent signed Profile snapshots, public unsafe identities only. Never fund.
const { reference, signedReference } = require('./signed-reference.cjs');
const legacy = signedReference('1', {module:'Profile',request:'update profile',data:{description:'Legacy bio',archive:['https://archive.example.test']}}, 1700001000000);
const snapshot = signedReference('1', {module:'Profile',request:'update profile',snapshot:1,data:{description:'Signed bio 🌱\nSecond line',archive:['https://archive.example.test'],extension:{theme:'blue'}}}, 1700001000001);
const newerLegacy = signedReference('1', {module:'Profile',request:'update profile',data:{description:'Later legacy bio'}}, 1700001000002);
const foreign = signedReference('2', {module:'Profile',request:'update profile',snapshot:1,data:{description:'Different signer'}}, 1700001000003);
const withMedia = signedReference('1', {module:'Profile',request:'update profile',snapshot:true,data:{description:'Original bio',image:'https://images.example.test/avatar.png',banner:'https://images.example.test/banner.png',archive:['https://archive.example.test'],extension:{theme:'blue'}}}, 1700001000004);
// JavaScript decodes 1.0 as numeric1, which the pinned client treats as a snapshot.
const numericSnapshot = signedReference('1', {module:'Profile',request:'update profile',snapshot:JSON.rawJSON('1.0'),data:{description:'Numeric snapshot'}}, 1700001000001);
const longBio = signedReference('1', {module:'Profile',request:'update profile',snapshot:1,data:{description:Array.from({length:40},(_,index)=>`Public biography line ${index+1}`).join('\n')}}, 1700001000006);
process.stdout.write(JSON.stringify({synthetic:true,reference,legacy,snapshot,newerLegacy,foreign,withMedia,numericSnapshot,longBio},null,2)+'\n');
