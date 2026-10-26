from pathlib import Path
import json,hashlib,re,xml.etree.ElementTree as ET

def validate(target,png):
 chat=target/'chat';base=chat/'status-originals'
 for name,count in [('navigation',2),('navigation-keyboard',1),('navigation-motion',2)]:
  log=(chat/f'{name}-ui.txt').read_text()
  assert f'OK ({count} tests)' in log if count!=1 else 'OK (1 test)' in log
  assert log.count('INSTRUMENTATION_CODE: -1')==1
  assert (chat/f'{name}-ui-exit.txt').read_text().strip()=='0'
 folder=base/'navigation-evidence'
 for width in [320,412]:
  for scale in [100,130,200]:
   for theme in ['light','dark']:
    label=f'navigation-{width}-font{scale}-{theme}'
    config=json.loads((folder/f'{label}-configuration.json').read_text())
    assert config['widthPixels']==width and config['widthDp']==width
    assert abs(config['fontScale']-scale/100)<.01 and config['selectedAppearance'].lower()==theme
    info=json.loads((folder/f'{label}-targets.json').read_text())
    assert info['disjoint'] is True and info['minimumPixels']>=47
    assert set(info['targets'])=={'Home','Search','Notifications','Chat','Your profile','All posts','Following'}
    rects=[list(map(int,b.split()))for b in info['targets'].values()]
    for i,(l,t,r,b) in enumerate(rects):
     assert r-l>=info['minimumPixels'] and b-t>=info['minimumPixels']
     assert 0<=l<r<=width and 0<=t<b<=(640 if width==320 else 915)
     for ll,tt,rr,bb in rects[i+1:]:assert not(l<rr and ll<r and t<bb and tt<b)
    layout=(folder/f'{label}-layout.txt').read_text()
    assert 'heightOverflow=false' in layout and 'exceeded=false' in layout and 'lineEnd=9' in layout and 'ellipsized=false' in layout
    text_width=int(re.search(r'size=(\d+) x',layout)[1]);left=float(re.search(r'lineLeft=([\d.]+)',layout)[1]);right=float(re.search(r'lineRight=([\d.]+)',layout)[1]);assert 0<=left<right<=text_width
    for suffix in ['', '-following']:
     png((folder/f'{label}{suffix}.png').read_bytes());ET.parse(folder/f'{label}{suffix}.xml')
 keyboard=base/'navigation-keyboard-evidence'
 for theme in ['light','dark']:
  label=f'keyboard-320-font200-{theme}'
  for suffix in [f'focus-{i}'for i in range(5)]+[f'name-{i}'for i in range(5)]+[f'selected-{i}'for i in range(1,5)]+['chat-keyboard-selected']:
   png((keyboard/f'{label}-{suffix}.png').read_bytes());ET.parse(keyboard/f'{label}-{suffix}.xml')
  for i,name in enumerate(['Home','Search','Notifications','Chat','Your profile']):
   tree=ET.parse(keyboard/f'{label}-focus-{i}.xml')
   assert any(n.get('text')==name and n.get('focused')=='true' for n in tree.iter('node'))
  for i,name in enumerate(['Search','Notifications','Chat','Your profile'],1):
   tree=ET.parse(keyboard/f'{label}-selected-{i}.xml')
   assert any(n.get('text')==name and n.get('selected')=='true'for n in tree.iter('node'))
  for i,name in enumerate(['Home','Search','Activity','Chat','Profile']):
   tree=ET.parse(keyboard/f'{label}-name-{i}.xml')
   captions=[n.get('text') for n in tree.iter('node') if n.get('text') in ['Home','Search','Activity','Chat','Profile'] and n.get('clickable')=='false' and n.get('selected')!='true']
   assert captions==[name], (name,captions)
 motion=base/'navigation-motion-evidence'
 for scale in [1,0]:
  label=f'navigation-{scale}'
  info=json.loads((motion/f'{label}.json').read_text())
  assert info['requestedMotionScale']==scale and info['actualMotionScale']==scale and info['isRecomposer']is True
  assert abs(info['fontScale']-2)<.01
  assert (motion/f'{label}.mp4').stat().st_size>1000
  assert 'REDSQUARE_RECORDER_EXIT=0' in (motion/f'{label}-recorder.txt').read_text()
  frames=info['decodedOriginalFrames'];assert info['frameCount']==len(frames)>=2
  times=[f['presentationTimeUs']for f in frames];assert all(a<b for a,b in zip(times,times[1:]))
  assert len({hashlib.sha256((motion/f['file']).read_bytes()).hexdigest()for f in frames})>1
  for f in frames:png((motion/f['file']).read_bytes())
  for suffix in ['before','after']:
   png((motion/f'{label}-{suffix}.png').read_bytes());ET.parse(motion/f'{label}-{suffix}.xml')
