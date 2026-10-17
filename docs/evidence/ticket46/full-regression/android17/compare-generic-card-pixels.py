from pathlib import Path
import struct,zlib,sys

def rgb(path):
    b=path.read_bytes(); assert b[:8]==b'\x89PNG\r\n\x1a\n'
    offset=8; packed=bytearray()
    while offset<len(b):
        size=struct.unpack('>I',b[offset:offset+4])[0]; kind=b[offset+4:offset+8]; data=b[offset+8:offset+8+size];offset+=size+12
        if kind==b'IHDR':
            w,h,bits,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',data)
            assert bits==8 and color in (2,6) and compression==filtering==interlace==0
        elif kind==b'IDAT':packed.extend(data)
    channels=3 if color==2 else 4; stride=w*channels
    raw=zlib.decompress(packed); assert len(raw)==h*(stride+1)
    rows=[]; prior=bytearray(stride)
    def paeth(a,b,c):
        p=a+b-c; pa,pb,pc=abs(p-a),abs(p-b),abs(p-c)
        return a if pa<=pb and pa<=pc else b if pb<=pc else c
    for y in range(h):
        start=y*(stride+1); f=raw[start]; row=bytearray(raw[start+1:start+stride+1]);assert 0<=f<=4
        for x in range(stride):
            left=row[x-channels] if x>=channels else 0; up=prior[x]; corner=prior[x-channels] if x>=channels else 0
            predictor=(0,left,up,(left+up)//2,paeth(left,up,corner))[f]
            row[x]=(row[x]+predictor)&255
        rows.append(bytes(row));prior=row
    return w,h,channels,rows

import hashlib,json
base=Path(sys.argv[1])
names=['private-worker-generic-alert','private-ordinary-first-generic-alert','private-receipt-storage-generic-alert']
bounds=(16,195,304,318)
observations={}
regions=[]
for name in names:
    path=base/(name+'.png');w,h,c,rows=rgb(path)
    assert(w,h,c)==(320,640,3)
    left,top,right,bottom=bounds
    region=b''.join(row[left*c:right*c]for row in rows[top:bottom])
    regions.append(region)
    observations[name]={'sourceSha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'dimensions':[w,h,c],'cardRgbSha256':hashlib.sha256(region).hexdigest()}
assert len(set(regions))==1,'The original notification-card regions differ'
print(json.dumps({'method':'Read-only original PNG unfiltering with the previously retained #44 decoder; no image edited.',
    'bounds':bounds,'observation':'All three original generic notification-card pixel regions exactly equal. Worker card positively viewed. Incomplete other viewer previews do not establish an Android rendering cause.',
    'images':observations},indent=2))
