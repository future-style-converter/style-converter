import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
dk = pt._dk
def band1(a, t):
    _, _, b = pt.l4_region(a, t); return b
def colprof(a, top, bot, x0, x1):
    return dk(a[top:bot + 1, :]).sum(axis=0).astype(float)
def xc(p, q, span):
    best = (-1, 0)
    for s in range(-span, span + 1):
        qs = np.roll(q, s); den = np.sqrt((p * p).sum() * (qs * qs).sum()); c = (p * qs).sum() / den if den else 0
        best = max(best, (round(float(c), 3), s))
    return best
def crop_id(a, b, t, margin):
    """U1-alone: line-1 crop of a vs the wave54-final crop of b translated down by the margin: correlation of the ink weights."""
    bb = band1(b, t)[0]; top, bot = bb[0], bb[1]
    p = (255 - a[top + margin - 3:bot + margin + 4, :].min(axis=2)).astype(float).ravel(); q = (255 - b[top - 3:bot + 4, :].min(axis=2)).astype(float).ravel()
    return round(float((p * q).sum() / np.sqrt((p * p).sum() * (q * q).sum())), 3)
MARGIN = {'005': 21, '006': 21, '014': 17}
say('## L4 — line-1 right edge Δ vs ref; line-1 column-profile best corr vs ref (shift ±12); U1-alone crop identity vs wave54-final + margin')
for t in pt.L4_T[:3]:
    r = ref('css-text-decor', t); rb = band1(r, t); rp = colprof(r, *rb[0])
    rows = [('web', pt.cap('wave54-final', 'css-text-decor', t, 'web'), None)]
    for p in ('ios', 'android'):
        rows.append((f'C-{p}', pt.png(f'ua-heading-margin-replay/{t}-{p}-C.png'), None))
        for f in ('line1-clipped-10px', 'line1-mirrored', 'line1-thirds-swapped'):
            rows.append((f'C-{p}:{f}', fk(f'L4-C-{f}', p, t), None))
        rows.append((f'U1-{p}', pt.png(f'ua-heading-margin-replay/{t}-{p}-U1.png'), p))
        for f in ('line1-mirrored', 'line1-thirds-swapped'):
            rows.append((f'U1-{p}:{f}', fk(f'L4-U1-{f}', p, t), p))
    say(f'  {t} ref band1 {rb[0]}')
    for l, a, base_p in rows:
        b = band1(a, t); s = f'    {l:32} band1 {tuple(int(v) for v in b[0])} Δx1 {int(b[0][3]) - int(rb[0][3]):+d}'
        if base_p is None: s += f'  colprof {xc(colprof(a, *b[0]), rp, 12)}'
        else: s += f'  crop-id {crop_id(a, pt.cap("wave54-final", "css-text-decor", t, base_p), t, MARGIN[t[-3:]])}'
        say(s)
