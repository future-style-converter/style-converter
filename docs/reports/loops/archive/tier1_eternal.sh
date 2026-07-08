#!/usr/bin/env bash
# Eternal Tier 1 expansion loop — picks the next partial row, expands its
# fixture with parser-derived keywords, tests, updates tracker. Loops until
# no more partial rows OR until killed by user.
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce
LOG=/tmp/tier1_eternal.log
RES=/tmp/tier1_eternal_results.txt
> $LOG; > $RES
echo "ETERNAL START at $(date)" >> $LOG

while true; do
  # Wait for any other test-all to finish (lock is a directory, not a file → use -d)
  while [ -d /tmp/style-converter-testall.lock ] || [ -f /tmp/style-converter-testall.lock ]; do sleep 15; done

  # Pick first partial row from tracker
  ROW=$(awk -F'|' '
    NR>10 && NF>=10 {
      s=$7; gsub(/ /,"",s)
      if(s=="partial") {
        cat=$3; gsub(/ /,"",cat)
        prop=$4; gsub(/ /,"",prop)
        parser=$5; gsub(/^ +| +$/,"",parser)
        target=$6+0; actual=$8+0
        if (target > actual) {
          print cat"|"prop"|"parser"|"actual"|"target
          exit
        }
      }
    }' testing/TIER1_VARIANT_DEPTH.md)

  if [ -z "$ROW" ]; then
    echo "ETERNAL: no more partial rows at $(date)" >> $LOG
    break
  fi

  IFS='|' read -r cat prop parser actual target <<< "$ROW"
  fixture="examples/properties/perfect/$cat/$prop.json"
  echo "[$(date +%H:%M:%S)] $cat/$prop ($actual→$target)" >> $LOG

  # Run focused expansion on this single fixture
  python3 - <<PYEOF >> $LOG 2>&1
import os, re, json
os.chdir('/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce')
CAP = 25
BAD = {'when','if','else','return','val','var','fun','it','this','let','const',
       'parseLength','parseColor','parseTime','parseAngle','parsePercent',
       'parseInteger','parseNumber','LengthParser','ColorParser','TimeParser',
       'width','height','color','length','percent','time','angle','integer',
       'number','keyword','data','src','main','app','kotlin','styleconverter',
       'irmodels','properties','longhands','parser','property','value','token',
       'string','hash','dimension','number','ident','function','keyword','key',
       'true','false','null'}

def kebab(p): return re.sub(r'(?<!^)(?=[A-Z])', '-', p).lower()

cat='$cat'; prop='$prop'; parser='$parser'
fp = f'examples/properties/perfect/{cat}/{prop}.json'
css_prop = kebab(prop)

if not os.path.exists(parser) or parser == '(none)':
    print(f'  no parser, skip')
    open('/tmp/eternal_skip','w').write('1')
    raise SystemExit

# Refuse to expand function-valued properties — bare keyword extraction
# would emit invalid CSS (e.g., 'filter: blur' instead of 'filter: blur(2px)').
# Audit round-3 caught this generating false-PASS fixtures. These properties
# need hand-written fixtures with proper function syntax.
FUNCTION_PROPS = {
    'filter', 'backdrop-filter', 'transform', 'clip-path', 'mask',
    'mask-image', 'background-image', 'border-image-source',
    'list-style-image', 'cursor', 'content', 'shape-outside',
    'will-change', 'transition', 'animation', 'offset-path',
    'd', 'src',
}
if css_prop in FUNCTION_PROPS:
    print(f'  {css_prop} is function-valued — bare-keyword expansion would be invalid CSS')
    open('/tmp/eternal_skip','w').write('1')
    raise SystemExit

src = open(parser).read()
kws = []
seen = set()
for m in re.finditer(r'"([a-z][a-z0-9-]*)"', src):
    k = m.group(1)
    if k in BAD or k in seen or len(k)<2 or len(k)>30: continue
    if k == css_prop or k == css_prop.replace('-',''): continue
    seen.add(k); kws.append(k)

d = json.load(open(fp))
existing = {str(c['properties'].get(css_prop)) for c in d['components'].values() if css_prop in c.get('properties',{})}
template = None
for c in d['components'].values():
    if css_prop in c.get('properties', {}):
        template = dict(c['properties']); break
if template is None:
    # No existing component uses this property. Build a default template from
    # the first existing component (which has base shape props like width/bg)
    # and we'll add the property to it. Skip only if the fixture is empty.
    if d['components']:
        first_comp = next(iter(d['components'].values()))
        template = dict(first_comp.get('properties', {}))
    else:
        # Truly empty — synthesize a minimal base
        template = {"width": "180px", "height": "80px", "padding": "10px",
                    "background-color": "#1f2937"}

new = [k for k in kws if k not in existing][:CAP - len(d['components'])]
if not new:
    print(f'  no new keywords to add')
    open('/tmp/eternal_skip','w').write('1')
    raise SystemExit
base_idx = len(d['components'])
for i, val in enumerate(new):
    slug = re.sub(r'[^a-zA-Z0-9]','_', val[:10])
    np = dict(template); np[css_prop] = val
    d['components'][f'V{base_idx+i}_{slug}'] = {'properties': np}
json.dump(d, open(fp,'w'), indent=2)
print(f'  added {len(new)} variants')
PYEOF

  # Detect early-exit (skip marker) and mark row 'exhausted' so we don't loop on it
  if [ -f /tmp/eternal_skip ]; then
    rm /tmp/eternal_skip
    echo "  → marking exhausted (no more variants possible)" >> $LOG
    python3 - <<PYEOF2
import re
TRACKER='testing/TIER1_VARIANT_DEPTH.md'
txt=open(TRACKER).read()
pat=re.compile(rf'(\| \d+ \| $cat \| $prop \|[^|]+\|\s*\d+\s*\| )partial( \|)')
txt=pat.sub(r'\1exhausted\g<2>', txt, count=1)
open(TRACKER,'w').write(txt)
PYEOF2
    continue
  fi

  # Test
  result=$(./test-all.sh "$fixture" 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+")
  if [ -z "$result" ]; then
    echo "  NORESULT" >> $LOG
    echo "NORESULT $cat/$prop" >> $RES
    continue
  fi
  minS=$(echo "$result" | sort -n | head -1)
  rows=$(echo "$result" | wc -l | tr -d ' ')
  variants=$((rows / 3))
  res="PASS"
  if awk "BEGIN{exit !($minS < 0.95)}"; then res="FAIL"; fi
  echo "  $res min=$minS variants=$variants" >> $LOG
  echo "$res min=$minS variants=$variants $cat/$prop" >> $RES

  # Update tracker row.
  # Use 'failing' (not 'partial') for fails so the main loop doesn't re-pick them.
  if [ "$res" = "PASS" ]; then
    new_status="passing"
    new_notes="auto-expanded with parser-derived keywords"
  else
    new_status="failing"
    new_notes="expanded to N variants, min SSIM below 0.95 — needs renderer fix or blocked-platform decision"
  fi
  python3 - <<PYEOF
import re
TRACKER='testing/TIER1_VARIANT_DEPTH.md'
txt=open(TRACKER).read()
pat=re.compile(rf'(\| \d+ \| $cat \| $prop \|[^|]+\|\s*\d+\s*\| )partial( \| )\d+( \| )[^|]+( \| )[^|]+\|')
txt=pat.sub(rf'\g<1>$new_status\g<2>$variants\g<3>$minS\g<4>$new_notes |', txt, count=1)
open(TRACKER,'w').write(txt)
PYEOF
done

echo "ETERNAL DONE at $(date)" >> $LOG
