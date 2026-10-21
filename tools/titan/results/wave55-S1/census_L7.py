# S1 own census, L7 U5: MARKER_PAINT_INITIAL gains maskImage / mixBlendMode, so a marker run declines ONLY where an
# ancestor-or-self of a list item computes a non-initial mask-image / mix-blend-mode. Superset over the whole scored
# corpus (not just the 17 bake roots): every WPT source (the 1435 per-test IR stems -> tools/wpt/css/...) that has BOTH a
# list item (<li, display: list-item, ::marker) AND any mask / mix-blend-mode text (incl. -webkit-mask, imported .css).
import sys, os, re, glob
R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
stems=[os.path.basename(p)[:-5] for p in glob.glob(R+'/tools/titan/runs/wave54-final/sections/*/per-test-ir/*.json')]
LI=re.compile(r'<li\b|list-item|::marker', re.I); MB=re.compile(r'mask|mix-blend-mode', re.I)
both=[]; li=0; mb=0; missing=0
for s in stems:
    rel=s[len('wpt__'):].split('__'); p=R+'/tools/wpt/css/'+'/'.join(rel)+'.html'
    if not os.path.exists(p):
        p2=p[:-5]+'.xht'
        if os.path.exists(p2): p=p2
        else: missing+=1; continue
    t=open(p,encoding='utf-8',errors='replace').read()
    for m in re.finditer(r'<link[^>]+href="([^"]+\.css)"', t):
        q=os.path.normpath(os.path.join(os.path.dirname(p), m.group(1)))
        if os.path.exists(q): t+=open(q,encoding='utf-8',errors='replace').read()
    a=bool(LI.search(t)); b=bool(MB.search(t)); li+=a; mb+=b
    if a and b: both.append(s)
print('stems', len(stems), 'sources missing', missing, '| with a list item', li, '| with mask/mix-blend-mode', mb)
print('BOTH (U5 can decline a marker only here):', len(both))
for s in both: print('  ', s)
