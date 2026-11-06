import concurrent.futures,datetime,json,re,subprocess,time,urllib.request
from pathlib import Path
root=Path('/mnt/storage/redsquare-android-artifacts')
output=root/'redesign94-node-recovery-catchup.jsonl'
text=(root/'redesign86/docs/evidence/ticket94/external-owner-freshness/wallet-first-failure.xml').read_text()
key=re.findall(r'(?<![1-9A-HJ-NP-Za-km-z])[1-9A-HJ-NP-Za-km-z]{43,46}(?![1-9A-HJ-NP-Za-km-z])',text)[0]
def snapshot(host):
    try:
        with urllib.request.urlopen(urllib.request.Request('https://'+host+'/balance/'+key,headers={'User-Agent':'redsquare-review-check/1'}),timeout=8) as response:
            payload=response.read(4096)
            timestamp,height,block=payload.decode('ascii').splitlines()[0].split('-')
            return dict(host=host,status=response.status,timestamp=int(timestamp),height=int(height),blockHash=block.removesuffix('.snap'),ageMinutes=round((time.time()*1000-int(timestamp))/60000,3),bytes=len(payload))
    except Exception as error:
        return dict(host=host,error=type(error).__name__,status=getattr(error,'code',None))
started=time.monotonic();consecutive=0
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    while time.monotonic()-started<300:
        owner,official=list(pool.map(snapshot,['saito.davidengelen.nl','saito.io']))
        service=subprocess.check_output(['systemctl','show','saito.service','--property=ActiveState','--property=SubState','--property=MainPID','--property=NRestarts','--property=ActiveEnterTimestamp'],text=True)
        matching=owner.get('status')==200 and official.get('status')==200 and 0<=owner.get('ageMinutes',100)<15 and owner.get('height')==official.get('height') and owner.get('blockHash')==official.get('blockHash')
        consecutive=consecutive+1 if matching else 0
        record=dict(window='catchup-after-observed-progress',observedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),elapsedSeconds=round(time.monotonic()-started,1),owner=owner,official=official,service=dict(line.split('=',1) for line in service.splitlines()),freshMatchingTips=matching,consecutiveMatchingObservations=consecutive)
        with output.open('a') as stream:stream.write(json.dumps(record)+'\n')
        print(json.dumps(record),flush=True)
        if consecutive>=2:break
        time.sleep(20)
    else:raise SystemExit('Bounded300second synchronization observation ended without two matching fresh snapshots')
