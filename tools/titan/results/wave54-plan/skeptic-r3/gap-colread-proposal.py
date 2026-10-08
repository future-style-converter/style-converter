# skeptic-r3: the proposed extra read for flex-zero-gap-rules.geometry.py (R3-S3) — the red runs on a COLUMN through
# each column-rule gap (x65 / x115), ±1 px against the ref, i.e. every column-rule SEGMENT's vertical extent. Run
# beside the probe (not a replacement) over: wave53-final, wave54-open and every fake on disk. Expected: ref structure
# x65 [(16,60),(121,165)] / x115 [(16,60),(71,110),(121,165)]; wave53-final web OK, natives WRONG; every fake WRONG.
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'wave53-plan'))
from geometry_common import paths, load, near  # noqa: E402
red = lambda p: p[0] > 200 and p[1] < 80 and p[2] < 80
COLS = (65, 115)
def runs(px, h, x):
    out, cur = [], None
    for y in range(h):
        if red(px[x, y]): cur = [y, y] if cur is None else [cur[0], y]
        elif cur is not None: out.append(tuple(cur)); cur = None
    if cur is not None: out.append(tuple(cur))
    return out
for run in sys.argv[1:]:
    ref = None
    for label, p in paths(run, 'css-gaps', 'flex/flex-gap-decorations-033'):
        im, px = load(p)
        if im is None: print(f'{run} {label:8} MISSING'); continue
        m = {x: runs(px, im.size[1], x) for x in COLS}
        if label == 'ref': ref = m
        bad = [x for x in COLS if not (len(m[x]) == len(ref[x]) and all(near(a, b, 1) for a, b in zip(m[x], ref[x])))]
        print(f'{run.split("/")[-1]:22} {label:8} {m} → ' + ('COLUMN READ OK' if not bad else f'COLUMN READ WRONG (red runs col {bad[0]} {m[bad[0]]} vs ref {ref[bad[0]]})'))
