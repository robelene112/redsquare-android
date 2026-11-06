from pathlib import Path
from collections import Counter
import sys,json,re,hashlib,shutil,tarfile,subprocess
SOURCE='1d3b6e0204bb141d9e6dedc6be2ad970b9b20110'
CASES={
'destination-reveal':('DestinationRevealMotionTest',['searchArrivesWithBriefMotionAndDisabledMotionRemainsImmediate']),
'destination-journey':('DestinationJourneyMotionTest',['primaryAndSecondaryJourneysRemainInterruptibleAtNormalAndZeroMotion']),
'image-retry':('ImageRetryOwnershipTest',['retryStartsOneReplacementReadAtNormalAndZeroMotion']),
'loaded-media':('LoadedMediaMotionTest',['profileBannerRevealsDeliveredPixelsWithoutChangingItsPlaceholderGeometry','providerDrawableRevealsDeliveredPixelsInsideItsOriginalImageContainer']),
'account-design-motion':('AccountDesignMotionTest',['accountTasksAndCategoriesRemainInterruptibleAndRetainTheActiveIdentity']),
'feed-media-motion':('FeedMediaMotionTest',['deliveredPreviewPixelsReplaceLoadingAtNormalAndZeroSystemMotion']),
'search-recovery':('SearchRecoveryPreviewTest',['disposalPausesBeforeCancelledLookupFinishesUnwinding','retryingAUsernameFailureDoesNotRepeatCompletedPostStreams','leavingAnUnfinishedSearchRetainsResultsAndRequiresExplicitContinuation']),
'app-frame-reading':('AppFrameReadingPreviewTest',['returningFromChatRetainsTheScrolledFeedThroughRecreation']),
'navigation-keyboard':('NavigationKeyboardTest',['largeTextDestinationsKeepKeyboardFocusSelectionAndDiscoverableNames']),
'giphy':('GiphyPreviewTest',['providerFailureCanRetryAndCloseWithoutLosingTheDraft','searchCanRetryAndReplaceAnOlderRequestWithoutLosingAuthoredWork','selectedAnimatedGifSurvivesSavedDraftAndFreshActivity']),
'giphy-posting':('GiphyPostingTest',['ownerGifPost','ownerGifReply','ownerGifQuote','officialGifPost','officialGifReply','officialGifQuote'])}
sdk,run,raw,dest=sys.argv[1:];raw=Path(raw);dest=Path(dest)
m=json.loads(subprocess.check_output(['gh','run','view',run,'-R','robelene112/redsquare-android','--json','databaseId,headSha,status,conclusion,url,jobs'],text=True))
assert m['headSha']==SOURCE and m['status']=='completed' and m['conclusion']=='success',m
assert len(m['jobs'])==1 and m['jobs'][0]['conclusion']=='success'
p=raw/'android-native-proof'/'.scratch';assert(p/'proof-source.txt').read_text().strip()==SOURCE
native=p/'device-evidence'/('api-29'if sdk=='10'else'api-37.0')
assert sdk in ('10','17') and native.is_dir()
assert (native/'system-image.txt').read_text().strip() == ('system-images;android-29;default;x86_64' if sdk=='10'else 'system-images;android-37.0;google_apis;x86_64')
assert not dest.exists(), "Require a fresh evidence target; do not mix prior frame sets"
dest.mkdir(parents=True)
summary={'source':SOURCE,'sdk':sdk,'run':int(run),'classes':[],'archives':[],'movies':[]}
for prefix,(cls,names)in CASES.items():
 log=native/f'{prefix}-ui.txt';s=log.read_text()
 assert re.search(r'^OK \('+str(len(names))+r' tests?\)$',s,re.M),(prefix,s[-1000:])
 assert Counter(re.findall(r'^INSTRUMENTATION_STATUS: test=(.*)$',s,re.M))==Counter({n:2 for n in names}),prefix
 assert set(re.findall(r'^INSTRUMENTATION_STATUS: class=(.*)$',s,re.M))=={'nl.davidengelen.redsquare.'+cls}
 assert len(re.findall(r'^INSTRUMENTATION_STATUS_CODE: 0$',s,re.M))==len(names)
 assert len(re.findall(r'^INSTRUMENTATION_CODE: -1$',s,re.M))==1
 exitfile=native/f'{prefix}-ui-exit.txt';assert exitfile.read_text().strip()=='0'
 for f in(log,exitfile):shutil.copy2(f,dest/f.name)
 summary['classes'].append({'prefix':prefix,'class':cls,'methods':names,'passed':len(names),'hostDeadlineSeconds':180})
for f in native.glob('*.tar'):
 if f.name not in {n+'-evidence.tar' for n in CASES}:continue
 summary['archives'].append({'file':f.name,'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'bytes':f.stat().st_size})
 with tarfile.open(f)as archive:archive.extractall(dest/'originals',filter='data')
for f in p.glob('proof-*.txt'):shutil.copy2(f,dest/f.name)
for f in(dest/'originals').rglob('*.mp4'):summary['movies'].append(str(f.relative_to(dest)))
assert sum(c['passed']for c in summary['classes'])==21
assert len(summary['archives'])==11,(len(summary['archives']),summary['archives'])
assert len(summary['movies'])==30,(len(summary['movies']),summary['movies'])
for name in ('system-image.txt','system-image.properties','requested-page-size.txt','page-size.txt','device-abi.txt','system-build.txt','emulator-capacity.txt','emulator-online-cpus.txt'):
 shutil.copy2(native/name,dest/name)
(dest/'run.json').write_text(json.dumps(m,indent=2)+'\n')
(dest/'results.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({'sdk':sdk,'run':run,'methods':21,'classes':11,'movies':30}))
