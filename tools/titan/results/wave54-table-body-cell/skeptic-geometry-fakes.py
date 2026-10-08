#!/usr/bin/env python3
# skeptic-geometry-fakes.py — can the L2 gating probe tell the fixed picture from the plausible WRONG device outcomes?
# Builds synthetic 006 android pictures from the REAL wave53-probe capture (white-out x24-63 rows 52-80, then repaint)
# and feeds each to compose-table-body-cell.geometry.py's own verdict() (its source exec'd up to the run loop).
# Writes the fakes under skeptic-fakes/ (regenerable) and prints one line per fake.
import os, sys
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
PROBE = os.path.join(ROOT, 'tools/titan/results/wave54-plan/compose-table-body-cell.geometry.py')
src = open(PROBE).read(); src = src[:src.index('runs = sys.argv')]
g = {'__file__': PROBE}; exec(compile(src, PROBE, 'exec'), g)
base = Image.open(os.path.join(ROOT, 'tools/titan/runs/wave53-probe/sections/CSS2/android-screenshots/wpt__CSS2__css21-errata__s-11-1-1b-006.png')).convert('RGB')
out = os.path.join(HERE, 'skeptic-fakes'); os.makedirs(out, exist_ok=True)
def fake(name, draw):
    im = base.copy(); d = ImageDraw.Draw(im); d.rectangle((20, 52, 70, 80), fill='white'); draw(d)
    p = os.path.join(out, name + '.png'); im.save(p); line, ok = g['verdict'](p); print(f'{name:34s} {"OK " if ok else "BAD"} {line}')
fake('fix', lambda d: d.rectangle((24, 56, 43, 75), fill='black'))
fake('d1-only_square-x44', lambda d: d.rectangle((44, 56, 63, 75), fill='black'))
fake('hug-ok_stroke-kept_0w-cell', lambda d: (d.rectangle((24, 56, 43, 75), fill='black'), d.line((23, 56, 23, 75), fill='black')))
fake('square-1px-right', lambda d: d.rectangle((25, 56, 44, 75), fill='black'))
fake('square-1px-low', lambda d: d.rectangle((24, 57, 43, 76), fill='black'))
fake('square-19x20', lambda d: d.rectangle((24, 56, 42, 75), fill='black'))
fake('fix+grey-antialias-edge', lambda d: (d.rectangle((24, 56, 43, 75), fill='black'), d.line((44, 56, 44, 75), fill=(200, 200, 200))))
