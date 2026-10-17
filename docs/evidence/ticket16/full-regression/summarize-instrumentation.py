from pathlib import Path
import collections, hashlib, json, re, sys
root=Path(sys.argv[1]).resolve()
counts=collections.Counter(); results=[]; totals=collections.Counter(); aggregates=[]
for file in sorted(root.rglob('*.txt')):
 if file.name=='profile-edit-ui.txt':
  parts=[file.parent/(name+'-ui.txt') for name in ['ProfileEditPreviewTest','ProfilePublishingPreviewTest','ProfileMediaPreviewTest','ProfileNewEditPreviewTest']]
  if all(part.is_file() for part in parts):
   assert file.read_bytes()==b''.join(part.read_bytes() for part in parts), 'Profile aggregate differs from individual originals'
   aggregates.append({'path':str(file.relative_to(root)),'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'individuals':[str(part.relative_to(root)) for part in parts]})
   continue
 text=file.read_text(errors='replace')
 if 'INSTRUMENTATION_STATUS' not in text and 'OK (' not in text: continue
 row={'path':str(file.relative_to(root)),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
 statuses=collections.Counter(); context={}; cases=[]
 for line in text.splitlines():
  match=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.*)',line)
  if match: context[match[1]]=match[2]
  match=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
  if match:
   if match[1] in ['0','-1','-2','-3','-4'] and set(context)=={'class','test'}:
    code=int(match[1]);statuses[str(code)]+=1
    cases.append(dict(context,code=code))
   context={}
 row.update(statuses=dict(statuses),cases=cases,
  junit_ok=[int(n) for n in re.findall(r'^OK \((\d+) tests?\)',text,re.M)],
  junit_times=re.findall(r'^Time: ([\d.]+)',text,re.M),
  deliberate_or_unexplained_crash='Process crashed.' in text,
  explicit_failures='FAILURES!!!' in text)
 counts.update(statuses)
 totals['completion_records']+=len(row['junit_ok'])
 totals['reported_ok_test_counts']+=sum(row['junit_ok'])
 totals['crash_outputs']+=int(row['deliberate_or_unexplained_crash'])
 totals['explicit_failed_files']+=int(row['explicit_failures'])
 results.append(row)
print(json.dumps(dict(root=str(root),status_codes=dict(counts),totals=dict(totals),excluded_exact_aggregates=aggregates,files=results),indent=2))
