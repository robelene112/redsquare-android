import sys,json,urllib.request,urllib.error,re,time,datetime,hashlib
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
origin=sys.argv[1]
assert origin in ['https://saito.davidengelen.nl','https://saito.io']
key='pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU'
client=urllib.request.build_opener(NoRedirect)
report={'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'origin':origin,'publicUnsafeScalar2':key,'readOnly':True,'redirectsAllowed':False}
for path in ['/options','/balance/'+key]:
    started=time.monotonic()
    try:
        with client.open(urllib.request.Request(origin+path,headers={'Cache-Control':'no-cache, no-store'}),timeout=10) as res:
            data=res.read(2*1024*1024+1)
            assert len(data)<=2*1024*1024
            result={'httpStatus':res.status,'bytes':len(data),'bodySha256':hashlib.sha256(data).hexdigest()}
            if path=='/options':
                period=json.loads(data)['consensus']['genesis_period']
                assert isinstance(period,int) and not isinstance(period,bool) and 0<period<2**64
                result['genesisPeriod']=period
            else:
                lines=data.decode('ascii').splitlines()
                assert re.fullmatch(r'[0-9]+-[0-9]+-[0-9a-f]{64}\.snap',lines[0])
                timestamp,height,blockhash=lines[0][:-5].split('-')
                result.update(timestampMillis=int(timestamp),height=int(height),tipHash=blockhash,rows=len(lines)-1)
                total=0
                for row in lines[1:]:
                    parts=row.split();assert len(parts)==6 and parts[0]==key and all(re.fullmatch('[0-9]+',v) for v in parts[1:])
                    total+=int(parts[4])
                result['reportedTotalNolan']=str(total)
    except Exception as e:
        result={'errorClass':type(e).__name__}
    result['elapsedSeconds']=round(time.monotonic()-started,3)
    report[path]=result
print(json.dumps(report,indent=2))
