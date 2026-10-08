#!/usr/bin/env python3
# skeptic/geom-fakes.py — can the plan's [M] geometry rule (wave54-plan/rtl-marker-bake.geometry.py) FAIL on the
# realistic Android failure mode "one run of a split marker is not painted"? Builds fake runs in a scratch root
# from wave53-probe's counter-suffix captures (web = the right picture), runs the UNMODIFIED probe against them by
# re-pointing geometry_common.ROOT (the refs path stays the real frozen corpus). Fakes, all three platforms:
#   ctl      — wave53-probe as captured (web/ios right, android the reverted li-owned shape);
#   nodot    — web/ios: the '.' of `.1` / `.2` (x132-136) whitened on rows 1-2 (the digit runs kept);
#   nodigit  — web/ios: the '1' / '2' (x137-146) whitened on rows 1-2 (the '.' runs kept).
# Both gating probes of the [M] key family are run: rtl-marker-bake.geometry.py and wave53-plan/lists-bakes.geometry.py.
# Usage: geom-fakes.py <scratch-dir>
import os, sys, shutil, runpy, io, contextlib
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
SCR = os.path.abspath(sys.argv[1])
SRC = f'{REPO}/tools/titan/runs/wave53-probe/sections/css-counter-styles'
NAME = 'wpt__css-counter-styles__counter-suffix.png'
ROWS = [(212, 227), (236, 251)]                      # the two decimal RTL rows (text bands y214-225 / y238-249)
def build(run, x0, x1):
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        out = f'{SCR}/tools/titan/runs/{run}/sections/css-counter-styles/{d}'
        os.makedirs(out, exist_ok=True)
        im = Image.open(f'{SRC}/{d}/{NAME}').convert('RGB')
        if x0 is not None and d != 'android-screenshots':
            px = im.load()
            for y0, y1 in ROWS:
                for y in range(y0, y1):
                    for x in range(x0, x1): px[x, y] = (255, 255, 255)
        im.save(f'{out}/{NAME}')
sys.path.insert(0, f'{REPO}/tools/titan/results/wave53-plan')
import geometry_common
geometry_common.ROOT = SCR                            # captures from the scratch root; REFS already bound to the real corpus
for run, x0, x1 in (('ctl', None, None), ('nodot', 132, 137), ('nodigit', 137, 147)):
    build(run, x0, x1)
    buf = io.StringIO(); sys.argv = ['probe', run]
    with contextlib.redirect_stdout(buf):
        try: runpy.run_path(f'{REPO}/tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py', run_name='__main__')
        except SystemExit: pass
    print(f'--- fake {run} (rtl-marker-bake.geometry.py)'); print('\n'.join(l for l in buf.getvalue().splitlines() if l.startswith('[M]')))
    # The second gating probe on the same fakes (wave53-plan/lists-bakes.geometry.py; counter-suffix lines only).
    buf = io.StringIO(); sys.argv = ['probe', run]
    with contextlib.redirect_stdout(buf):
        try: runpy.run_path(f'{REPO}/tools/titan/results/wave53-plan/lists-bakes.geometry.py', run_name='__main__')
        except SystemExit: pass
    print(f'--- fake {run} (lists-bakes.geometry.py)'); print('\n'.join(l for l in buf.getvalue().splitlines() if 'counter-suffix' in l))
