# Validate the proposed rule additions as MEASUREMENTS (no probe file is edited): each candidate read on the passing pictures
# (ref / web / ios / the lane's fixed picture) and on the accepted round-2 fakes (pngs/<fid>/...).
import os, sys, importlib.util
sys.dont_write_bytecode = True
import numpy as np
from PIL import Image
R = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
P = f'{R}/tools/titan/results/wave55-plan'
SCR = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('pt', f'{P}/probes-teeth.py'); pt = importlib.util.module_from_spec(spec); spec.loader.exec_module(pt)
REFS = pt.REFS
ref = lambda sec, t: pt.A(Image.open(f'{REFS}/{sec}/{t.replace("/", "__")}.png'))
fk = lambda fid, plat, t: pt.A(Image.open(f'{SCR}/pngs/{fid}/{plat}__{t.replace("/", "__")}.png'))
def say(*a): print(*a)

say('## L2 — fail ink and green-mask XOR vs the ref')
green = lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
bad = lambda a: ((a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] < 80)) | ((a[..., 0] > 240) & (np.abs(a[..., 1] - 127) < 12) & (a[..., 2] > 240))
for sec, t in pt.L2_U1:
    r = ref(sec, t); rows = [('web', pt.cap('wave54-final', sec, t, 'web')), ('ios', pt.cap('wave54-final', sec, t, 'ios')), ('fixed-android', pt.cap('wave54-probe', sec, t, 'android'))]
    for fid in ('L2-fail-ink-sliver', 'L2-notch7', 'L2-radius-moved'):
        p = f'{SCR}/pngs/{fid}/android__{t.replace("/", "__")}.png'
        if os.path.exists(p): rows.append((fid, pt.A(Image.open(p))))
    say(f'  {t.split("/")[-1]:44} ' + ' | '.join(f'{l}: xor {int((green(a) ^ green(r)).sum())} bad {int(bad(a).sum())}' for l, a in rows))

say('\n## L3 dir rows — row profile (ink px per row) shift vs the ref, and the 2-D correlation at (0,0)')
ink = lambda a: a.min(axis=2) < 200
def rowprof(a, y0): return ink(a[y0 - 4:y0 + 24]).sum(axis=1).astype(float)
def xc(p, q, span=4):
    best = (-1, 0)
    for s in range(-span, span + 1):
        qs = np.roll(q, s); den = np.sqrt((p * p).sum() * (qs * qs).sum()); c = (p * qs).sum() / den if den else 0
        best = max(best, (round(float(c), 3), s))
    return best
def corr2(a, b):
    a = a.astype(float).ravel(); b = b.astype(float).ravel(); a -= a.mean(); b -= b.mean(); d = np.sqrt((a * a).sum() * (b * b).sum()); return round(float((a * b).sum() / d), 3) if d else 0
rd = ref('selectors', 'dir-style-02a'); D = pt.l3_fixed('dir-style-02a'); wd = pt.cap('wave54-final', 'selectors', 'dir-style-02a', 'web')
for k in (6, 7, 9):
    y0 = 16 + 20 * (k - 1); rp = rowprof(rd, y0)
    rows = [('web', wd), ('fixed-android', D)] + [(f, fk(f'L3-dir-row{k}-{f}', 'android', 'dir-style-02a')) for f in ('text+2y', 'text-2y', 'text-vflip')]
    rows += [(f'r1:{f}', pt.A(Image.open(f'{SCR}/teeth-rerun/fakeruns/L3-dir-row{k}-{f}/sections/selectors/android-screenshots/wpt__selectors__dir-style-02a.png'))) for f in ('gut', 'mirror-words', 'text+3x')]
    say(f'  row{k}: ' + ' | '.join(f'{l}: rowprof {xc(rowprof(a, y0), rp)} 2d {corr2(ink(a[y0:y0+20, :260]), ink(rd[y0:y0+20, :260]))}' for l, a in rows))

say('\n## L3 bidi — per RTL band: blue column-profile corr at shift 0 vs the ref, blue row-profile shift; yellow bbox per box')
blue = pt._blue
yel = lambda a: (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] < 80)
rb = ref('css-text', 'bidi/bidi-tab-001'); B = pt.l3_fixed('bidi-tab-001')
rows = [('web', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'web')), ('ios', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'ios')), ('fixed-android', B)]
rows += [(f, fk(f'L3-bidi-{f}', 'android', 'bidi/bidi-tab-001')) for f in ('rtl-run-order-mirrored', 'rtl-runs+2y', 'yellow-boxes-erased')]
def ybox(a):
    out = []
    for y0, y1 in ((60, 155), (160, 255)):
        ys, xs = np.nonzero(yel(a[y0:y1])); out.append((int(xs.min()), int(ys.min()) + y0, int(xs.max()), int(ys.max()) + y0) if len(xs) else None)
    return out
for l, a in rows:
    cc = []; rs = []
    for y0 in (86, 106, 186, 226):
        p = blue(a[y0:y0 + 20]).sum(axis=0).astype(float); q = blue(rb[y0:y0 + 20]).sum(axis=0).astype(float)
        cc.append(round(float((p * q).sum() / np.sqrt((p * p).sum() * (q * q).sum())), 3))
        rs.append(xc(blue(a[y0 - 4:y0 + 24]).sum(axis=1).astype(float), blue(rb[y0 - 4:y0 + 24]).sum(axis=1).astype(float))[1])
    say(f'  {l:24} colcorr@0 {cc} rowshift {rs} yellow {ybox(a)} (ref {ybox(rb)})')

say('\n## L3 stack — red px and green px outside the four cells (ring 2), vs web')
gr = lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60)
red = lambda a: (a[..., 0] > 200) & (a[..., 1] < 60) & (a[..., 2] < 60)
ST = pt.l3_fixed('anchor-position-multicol-nested-001'); ws = pt.cap('wave54-final', 'css-anchor-position', 'anchor-position-multicol-nested-001', 'web')
keep = np.zeros(ST.shape[:2], bool)
for x, y in pt.L3_CELLS: keep[y - 2:y + 12, x - 2:x + 12] = True
for l, a in [('ref', ref('css-anchor-position', 'anchor-position-multicol-nested-001')), ('web', ws), ('ios', pt.cap('wave54-final', 'css-anchor-position', 'anchor-position-multicol-nested-001', 'ios')), ('fixed-android', ST),
             ('today-android', pt.cap('wave54-final', 'css-anchor-position', 'anchor-position-multicol-nested-001', 'android'))] + [(f, fk(f'L3-stack-{f}', 'android', 'anchor-position-multicol-nested-001')) for f in ('red-shown', 'other-squares-erased')]:
    say(f'  {l:22} red {int(red(a).sum())} green outside cells {int((gr(a) & ~keep).sum())} xor-vs-web {int((gr(a) ^ gr(ws)).sum())}')

say('\n## L3 dynamic-offset — yellow parent bbox + (yellow + magenta inside it) count')
mag = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
def par(a):
    ys, xs = np.nonzero(yel(a)); b = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if len(xs) else None
    n = int((yel(a) | mag(a))[b[1]:b[3] + 1, b[0]:b[2] + 1].sum()) if b else 0
    return b, n
for doc in ('dynamic-offset-rtl-001', 'dynamic-offset-vrl-rtl-001', 'dynamic-offset-rtl-002'):
    rows = [('ref', ref('css-writing-modes', doc)), ('web', pt.cap('wave54-final', 'css-writing-modes', doc, 'web')), ('ios', pt.cap('wave54-final', 'css-writing-modes', doc, 'ios')), ('fixed-android', pt.l3_fixed(doc))]
    for f in ('parent-erased', 'parent+3x'):
        p = f'{SCR}/pngs/L3-{doc[-3:]}-{f}/android__{doc}.png'
        if os.path.exists(p) and doc != 'dynamic-offset-vrl-rtl-001': rows.append((f, pt.A(Image.open(p))))
    say(f'  {doc:28} ' + ' | '.join(f'{l}: {par(a)}' for l, a in rows))
