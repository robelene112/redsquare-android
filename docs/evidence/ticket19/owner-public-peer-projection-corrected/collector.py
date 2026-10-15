"""Bounded public route read. Full peer rows exist only in worker process memory."""
from pathlib import Path
import datetime,json,re,subprocess,sys,urllib.request,urllib.error
url='https://saito.davidengelen.nl/json/peers'
registrar='sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK'
def base_record():
 return {'endpoint':url,'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
  'readOnly':True,'productionStateModified':False,'publishedRegistrar':registrar,
  'effectiveRegistryAuthorityEstablished':False,'sourceStatusSemantics':'getPeers lists verified peers; connected/connecting/disconnected are distinct'}
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):return None
def collect():
 record=base_record()
 try:
  opener=urllib.request.build_opener(NoRedirect)
  with opener.open(urllib.request.Request(url,headers={'Accept':'application/json'}),timeout=12) as response:
   record['httpStatus']=response.status
   if response.headers.get_content_type()!='application/json':raise ValueError('content-type')
   body=response.read(262145)
   if len(body)>262144:raise ValueError('response-bound')
   value=json.loads(body)
   if not isinstance(value,dict) or set(value)!= {'peers'}:raise ValueError('wrapper-schema')
   rows=value['peers']
   if not isinstance(rows,list) or len(rows)>2048 or not all(isinstance(row,dict) for row in rows):raise ValueError('schema')
   if not all(isinstance(row.get('publicKey'),str) and re.fullmatch(r'[1-9A-HJ-NP-Za-km-z]{40,46}',row['publicKey']) for row in rows):raise ValueError('public-key-schema')
   matching=[row for row in rows if row['publicKey']==registrar]
   statuses=[row.get('status') for row in matching]
   if not all(status in ('connected','connecting','disconnected') for status in statuses):raise ValueError('matching-status')
   record.update(status='completed',publishedRegistrarListed=bool(matching),matchingVerifiedPeerCount=len(matching),
    matchingConnectedPeerCount=statuses.count('connected'),matchingStatuses=statuses,registryServiceObservation='unknown-not-collected')
 except urllib.error.HTTPError as error:record.update(status='http-error',httpStatus=error.code)
 except ValueError as error:record.update(status='schema-or-bounds-failure',category=str(error) if str(error) in ('content-type','response-bound','schema','wrapper-schema','matching-status','public-key-schema') else 'invalid-json')
 except Exception:record.update(status='request-failed')
 return record
if __name__=='__main__':
 if sys.argv[1:]==['--worker']:
  print(json.dumps(collect()))
 else:
  try:
   result=subprocess.run([sys.executable,__file__,'--worker'],capture_output=True,text=True,timeout=12,check=True)
   record=json.loads(result.stdout)
  except subprocess.TimeoutExpired:record=dict(base_record(),status='total-deadline-exceeded')
  except Exception:record=dict(base_record(),status='collector-failed')
  out=Path('docs/evidence/ticket19/owner-public-peer-projection-corrected');out.mkdir(parents=True,exist_ok=False)
  (out/'result.json').write_text(json.dumps(record,indent=2)+'\n')
  print(json.dumps(record))
