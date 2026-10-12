# show7.py <out.png> fake|set|cell [...] — fixed crop | fake crop | changed px, ×4, around each fake's change (+24 px)
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw
import fakes7 as F7
CX = {(f'{s[0]}-{s[1]}', c.cell): c for s in F7.contexts() for c in s[4].values()}
FAM = dict(F7.FAMILIES)
tiles = []
for spec in sys.argv[2:]:
    fake, st, cell = spec.split('|')
    cx = CX[(st, cell)]; o = np.clip(FAM[fake](cx), 0, 255).astype(np.uint8).astype(np.int16)
    d = (o != cx.F).any(axis=2); ys, xs = np.nonzero(d)
    x0, y0, x1, y1 = max(0, xs.min() - 24), max(0, ys.min() - 24), min(cx.W, xs.max() + 25), min(cx.H, ys.max() + 25)
    if x1 - x0 > 260: x1 = x0 + 260
    if y1 - y0 > 160: y1 = y0 + 160
    a, b = cx.F[y0:y1, x0:x1], o[y0:y1, x0:x1]; dv = np.where(d[y0:y1, x0:x1, None], np.array([255, 0, 0]), 255)
    t = np.concatenate([a, np.full((a.shape[0], 4, 3), 128), b, np.full((a.shape[0], 4, 3), 128), dv], axis=1)
    im = Image.fromarray(np.clip(t, 0, 255).astype(np.uint8)).resize(((t.shape[1]) * 3, t.shape[0] * 3), Image.NEAREST)
    lab = Image.new('RGB', (im.width, 16), (40, 40, 40)); ImageDraw.Draw(lab).text((2, 2), f'{fake} {cell.split("/")[-1]} @({x0},{y0}) changed {int(d.sum())}', fill=(255, 255, 255))
    tiles += [lab, im]
W = max(t.width for t in tiles); H = sum(t.height + 4 for t in tiles)
out = Image.new('RGB', (W, H), (60, 60, 60)); y = 0
for t in tiles:
    out.paste(t, (0, y)); y += t.height + 4
out.save(sys.argv[1])
