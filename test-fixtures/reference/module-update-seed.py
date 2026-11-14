"""Produce public, unsafe fixture records for an actual signed127-to-next update.
Only the existing generic encrypted-file API is used on127; current feature APIs
must subsequently read the exact original pending operations, drafts and bytes.
"""
import base64, hashlib, json, sys
from pathlib import Path
vault_path, mint_path, listing_path, purchase_path, output = map(Path, sys.argv[1:])
vault, mint, listing, purchase = [json.loads(p.read_text()) for p in (vault_path,mint_path,listing_path,purchase_path)]
owner = purchase['buyer']
assert owner == vault['owner'] == mint['owner'] == 'pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU'
records = []
def record(name, alias, family, value):
    records.append(dict(name=name,alias=alias,family=family,version=1,text=value if isinstance(value,str) else json.dumps(value,separators=(',',':'))))
order = dict(backend='owner',candidate=purchase['purchase']['networkBase64'],offer=listing['listing']['envelope'],offerSignature=listing['listing']['id'],provenance=[],mayHaveSent=True,payment=None,receipt=None,receiptBlock=None,cursor=None)
record('store-orders.'+owner+'.rows','redsquare-store-orders-v1','store-orders.',[order])
record('store-orders.'+owner+'.guard','redsquare-store-orders-v1','store-orders.',owner)
family='vault.'+owner+'.'; prefix=family+'owner.'; alias='redsquare.vault.'+owner
contents=base64.b64decode(vault['fileContentsBase64']); file_id=vault['file']['id']
for stem,name in [('copy.'+file_id,'Field notes.txt'),('draft','Module update draft.txt'),('export','Field notes.txt')]:
    record(prefix+stem+'.data',alias,family,base64.b64encode(contents).decode())
    record(prefix+stem+'.info',alias,family,dict(id=file_id,name=name,mime='text/plain',size=len(contents),sha256=hashlib.sha256(contents).hexdigest()))
record(prefix+'draft.rules',alias,family,dict(file=file_id,mode='STANDARD',source=''))
record(prefix+'operation',alias,family,dict(candidate=mint['mint']['networkBase64'],mime=mint['mime'],size=len(base64.b64decode(mint['contentsBase64'])),mayHaveSent=True,uploaded=False,owned=False,submitted=True,rejected=False))
result=dict(owner=owner,qualification='Public unsafe fixture wallet and source-derived records on a fresh emulator only. No network submission or real provider state.',inputs={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (vault_path,mint_path,listing_path,purchase_path)},expected=dict(orderId=purchase['purchase']['id'],vaultOperationId=mint['mint']['id'],fileId=file_id,fileContentsBase64=vault['fileContentsBase64']),records=records)
output.write_text(json.dumps(result,indent=2)+'\n')
