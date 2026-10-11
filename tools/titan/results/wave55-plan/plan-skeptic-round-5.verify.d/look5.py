#!/usr/bin/env python3
# Round-5 verifier: one look picture of representative ACCEPTED fakes — per row, the fixed picture's crop (left) and the
# accepted fake's crop (right), 2× nearest-neighbour, around the added paint ±48 px, labelled with the key that PASSED it.
# Usage: python3.12 -B look5.py <out.png> <fakes5.Lx.json …>   (reads the fake runs the records name, under tools/titan/runs; the
#        recorded JSON writes them <scratch>/…: point it at fakes5.py's own --json output, made beside its fake runs)
import json, os, re, sys
from PIL import Image, ImageDraw
RUNS = os.path.realpath('/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs')
DIRS = {'ios': 'ios-screenshots', 'android': 'android-screenshots'}
PICK = [  # (lane, capture prefix, variant, class) — one row each
    ('L1', 'hyphenate-character-001 ios', 'f50-sp3-open', (0, 0, 0)),
    ('L1', 'hyphenate-character-001 ios', 'f50-ln-open', (0, 0, 0)),
    ('L2', 'abspos-autopos-htb-ltr', 'f50-dup-end', (0, 128, 0)),
    ('L2', 'backdrop-filter-nested-border-radius-clip-3', 'sp3-touch-last', (102, 204, 255)),
    ('L3', 'bidi-tab-001', 'f50-dup-end', (0, 0, 255)),
    ('L3', 'anchor-position-multicol-nested-001', 'sp3-open', (0, 0, 0)),
    ('L3', 'dynamic-offset-rtl-001', 'f50-dup-end', (255, 255, 0)),
    ('L4', 'text-decoration-inset-005 ios', 'f50-sp3-open', (0, 0, 0)),
    ('L4', 'text-decoration-inset-005 android', 'f50-ln-open', (0, 0, 0)),
    ('L6', 'grid-abspos-staticpos-align-self-001', 'sp3-open', (0, 0, 0)),
    ('L6', 'grid-abspos-staticpos-align-self-001', 'f50-dup-end', (0, 128, 128)),
    ('L6', 'backdrop-filter-clip-rect', 'sp3-gap', (0, 0, 255)),
]
recs = [r for f in sys.argv[2:] for r in json.load(open(f))]
rows = []
for lane, cap, var, col in PICK:
    r = next((r for r in recs if r['lane'] == lane and r['capture'].startswith(cap) and r['variant'] == var and tuple(r['class']) == col
              and (r['gatingPassQ'] or r['tallyPass'])), None)
    if r is None:
        print('not found (or not accepted):', lane, cap, var, col); continue
    name = f'wpt__{r["sec"]}__{r["test"].replace("/", "__")}.png'
    fake = os.path.join(RUNS, r['run'], 'sections', r['sec'], DIRS[r['plat']], name)
    fixed = os.path.join(RUNS, re.sub(r'-z\dc\d-[^/]*$', '-FIXED', r['run']), 'sections', r['sec'], DIRS[r['plat']], name)
    x0, y0, x1, y1 = r['bbox']
    box = (max(0, x0 - 48), max(0, y0 - 48), min(390, x1 + 49), min(600, y1 + 49))
    if box[2] - box[0] > 300:
        box = (box[0], box[1], box[0] + 300, box[3])
    if box[3] - box[1] > 160:
        box = (box[0], box[1], box[2], box[1] + 160)
    a, b = [Image.open(os.path.realpath(p)).convert('RGB').crop(box) for p in (fixed, fake)]
    a, b = [i.resize((i.size[0] * 2, i.size[1] * 2), Image.NEAREST) for i in (a, b)]
    label = f'{lane} {r["capture"]} · {var} class {col} · +{r["added"]} px · PASS: {", ".join(k.split(" ", 1)[1] for k in r["gatingPassQ"]) or "tally " + ", ".join(r["tallyPass"])}'
    rows.append((label, a, b))
W = max(a.size[0] + b.size[0] for _, a, b in rows) + 30
H = sum(a.size[1] + 22 for _, a, _ in rows) + 10
o = Image.new('RGB', (max(W, 900), H), (200, 200, 200)); d = ImageDraw.Draw(o); y = 5
for label, a, b in rows:
    d.text((5, y), label, fill=(0, 0, 0)); y += 16
    o.paste(a, (5, y)); o.paste(b, (15 + a.size[0], y)); y += a.size[1] + 6
o.save(sys.argv[1]); print('wrote', sys.argv[1], o.size, len(rows), 'rows')
