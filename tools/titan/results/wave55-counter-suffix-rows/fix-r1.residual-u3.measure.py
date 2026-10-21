#!/usr/bin/env python3
# fix-r1.residual-u3.measure.py — wave-55 L5 FIX lane (fix-r1): defect 1 meets round 6's RESIDUAL read (PLAN D40, fix-r6.done.md).
# geometry-gate.py now applies residual-read.py to every gating / tally key: outside the cell's DECLARED change zone the capture
# must equal the base pixel for pixel, or every key of the cell FAILs with `residual:`. The counter-suffix ios zone
# (residual-read.py SPECS) is rows 3-4 / 5-6 x0-63 and row 9's digit window x136-150 only — the same one-glyph model of U3
# the probe's zones carry. This script reads, with residual-read.py's OWN read_arrays (imported read-only from the shared plan
# dir; no file written there), the correct-U3 pictures against their bases under (a) the installed SPEC and (b) the SPEC
# extended by the U3 run boxes the lane requests (rows 9-12 x130-135, row 10 x136-150).
# Pictures: skmodel (the device capture + U3's Catalyst delta, skeptic-model-u3.py) vs wave54-final ios; the Catalyst pair
# skeptic-catalyst-u3-with / -without (one rasterizer: U3 on / off); the Catalyst pair fix-r1.catalyst-full / -noU1 (U1 on / off:
# the residual must stay 0 there — U1 moves ink only inside rows 3-4 x0-63); the plan's ok-all replay re-grafted with Android's
# five boxes (fix-r1.probe-proposal.py §R) vs wave54-final ios.
# Usage: /opt/homebrew/bin/python3.12 -B fix-r1.residual-u3.measure.py   (exit 0; prints outside / vacated / non-class per read)
import copy, hashlib, importlib.util, os, sys
import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RR = f'{ROOT}/tools/titan/results/wave55-plan/residual-read.py'
spec_ = importlib.util.spec_from_file_location('residual_read', RR)
rr = importlib.util.module_from_spec(spec_); spec_.loader.exec_module(rr)
CELL = 'css-counter-styles/counter-suffix.html ios'
SPEC = rr.SPECS[CELL]
EXT = copy.deepcopy(SPEC)
EXT['zone'] = EXT['zone'] + [('rect', (130, 208, 136, 304)), ('rect', (136, 232, 151, 256))]   # the lane's requested U3 boxes
SEC = f'{ROOT}/tools/titan/runs/wave54-final/sections/css-counter-styles'
ld = lambda p: np.asarray(Image.open(p).convert('RGB')).astype(np.int16)
dev, andr = ld(f'{SEC}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png'), ld(f'{SEC}/android-screenshots/wpt__css-counter-styles__counter-suffix.png')
cw, co = ld(f'{HERE}/skeptic-catalyst-u3-with.png'), ld(f'{HERE}/skeptic-catalyst-u3-without.png')
cf, cn = ld(f'{HERE}/fix-r1.catalyst-full.png'), ld(f'{HERE}/fix-r1.catalyst-noU1.png')
skmodel = np.where((cw != co).any(axis=2)[..., None], cw, dev)
okall = ld(f'{ROOT}/tools/titan/results/wave55-plan/counter-suffix-rows-replay/ok-all/counter-suffix.ios.png')
okall_rg = okall.copy()
for x0, y0, x1, y1 in ((130, 208, 136, 232), (130, 232, 151, 256), (130, 256, 136, 280), (130, 280, 136, 304)):
    okall_rg[y0:y1, x0:x1] = andr[y0:y1, x0:x1]
print(f'# fix-r1.residual-u3.measure.out.txt — residual-read.py sha256 {hashlib.sha256(open(RR, "rb").read()).hexdigest()}, '
      f'cell {CELL}: installed zone {SPEC["zone"]}')
for name, C, B in (('skmodel (correct U3, device + Catalyst delta) vs wave54-final', skmodel, dev),
                   ('Catalyst U3 on vs U3 off (one rasterizer)', cw, co),
                   ('Catalyst FULL vs U1 reverted (one rasterizer)', cf, cn),
                   ('ok-all as committed vs wave54-final', okall, dev),
                   ('ok-all re-grafted (Android boxes) vs wave54-final', okall_rg, dev)):
    a = rr.read_arrays(C, B, SPEC)[:3]
    b = rr.read_arrays(C, B, EXT)[:3]
    d = (C != B).any(axis=2); ys, xs = np.nonzero(d)
    print(f'{name}: differing px {int(d.sum())}' + (f' (x{xs.min()}-{xs.max()} y{ys.min()}-{ys.max()})' if len(xs) else '') +
          f' → installed SPEC outside / vacated / non-class {a[0]} / {a[1]} / {a[2]}'
          f' ({"OK" if a == (0, 0, 0) else "FAIL: every key of the cell"}); + the U3 boxes {b[0]} / {b[1]} / {b[2]} ({"OK" if b == (0, 0, 0) else "FAIL"})')
