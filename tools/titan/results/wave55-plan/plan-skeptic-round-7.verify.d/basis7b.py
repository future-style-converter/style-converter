# basis7b.py — fix basis for MF2 (bidi: blue outside the probe's RTL bands): restrict the mask zone to blue3 ∩ the four RTL
# bands (y86-245 rows the probe reads) ∪ the windows, and re-read every bidi picture (fixed U1U2 replay, PLAIN) — and, for
# comparison, the same restriction idea on the other mask cells is not needed: their probes read the class colour everywhere.
import sys, os, copy
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
from PIL import Image
c = 'css-text/bidi/bidi-tab-001.html android'
spec = copy.deepcopy(RR.SPECS[c])
B = base_arr(c)
def read_restricted(C):
    band = np.zeros(C.shape[:2], bool)
    for y0 in (86, 106, 186, 226): band[y0:y0 + 20] = True
    # outside = d outside ((blue3 ∩ bands) on C ∪ on B ∪ windows)
    win = np.zeros_like(band)
    for x0, y0, x1, y1 in spec['windows']: win[y0:y1, x0:x1] = True
    zc = (RR.PRED['blue3'](C) & band) | win; zb = RR.PRED['blue3'](B) & band
    d = (C != B).any(axis=2)
    return int((d & ~(zc | zb)).sum()), int((d & ~band & (RR.PRED['blue3'](C) | RR.PRED['blue3'](B))).sum())
for lab, p in (('fixed U1U2', f'{PLAN}/positioned-parent-slot-replay/bidi-tab-001-android-U1U2.png'), ('PLAIN', f'{PLAN}/positioned-parent-slot-replay/bidi-tab-001-android-PLAIN.png')):
    C = A(Image.open(p)); o, v, n, _ = RR.read_arrays(C, B, spec); ro, rb = read_restricted(C)
    print(f'{lab:12} installed residual {o}/{v}/{n}; band-restricted outside {ro}; changed blue px outside the RTL bands {rb}')
