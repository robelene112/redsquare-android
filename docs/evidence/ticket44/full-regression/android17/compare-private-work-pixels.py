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
for name in ['private-cold-official-draft-restored','group-cold-official-work-retained','group-cold-official-membership-refused']:
    w,h,c,rows=rgb(base/(name+'.png'))
    assert(w,h,c)==(320,640,3)
    region=b''.join(rows[520:640])
    nonwhite=sum(region[i:i+3]!=bytes([255,255,255]) for i in range(0,len(region),3))
    print(json.dumps(dict(path=name,width=w,height=h,composerRegionNonwhitePixels=nonwhite,composerRegionRgbSha256=hashlib.sha256(region).hexdigest())))
