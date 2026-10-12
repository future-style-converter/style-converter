# look7.py — render each cell's fixed picture | zone overlay (Z_C ∪ Z_B ∪ windows tinted) | diff vs base, one PNG per lane
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
from PIL import Image
for lane, setn, secs, repl in fixed_sets():
    tiles = []
    for (sec, plat, test), F in repl.items():
        c = cellkey(sec, plat, test)
        if c not in RR.SPECS: continue
        B = base_arr(c); spec = RR.SPECS[c]
        win = np.zeros(F.shape[:2], bool)
        for x0, y0, x1, y1 in spec.get('windows', []): win[y0:y1, x0:x1] = True
        zc, zb = RR.zone(F, spec['zone'], True), RR.zone(B, spec['zone'], False)
        ov = F.copy().astype(float)
        ov[zc] = ov[zc] * 0.6 + np.array([0, 0, 255]) * 0.4
        ov[zb & ~zc] = ov[zb & ~zc] * 0.6 + np.array([255, 0, 0]) * 0.4
        ov[win] = ov[win] * 0.5 + np.array([255, 160, 0]) * 0.5
        d = (F != B).any(axis=2); dv = np.full(F.shape, 255, np.int16); dv[d] = (255, 0, 0)
        h = max(F.shape[0], 1); t = np.full((h, F.shape[1] * 3 + 20, 3), 128, np.int16)
        t[:, :F.shape[1]] = F; t[:, F.shape[1] + 10:2 * F.shape[1] + 10] = ov.astype(np.int16); t[:, 2 * F.shape[1] + 20:] = dv
        tiles.append((c, t))
    if not tiles: continue
    W = max(t.shape[1] for _, t in tiles); H = sum(t.shape[0] + 10 for _, t in tiles)
    out = np.full((H, W, 3), 60, np.int16); y = 0
    for c, t in tiles:
        out[y:y + t.shape[0], :t.shape[1]] = t; y += t.shape[0] + 10
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(f'look7-{lane}-{setn}.png')
    print(lane, setn, [c for c, _ in tiles])
