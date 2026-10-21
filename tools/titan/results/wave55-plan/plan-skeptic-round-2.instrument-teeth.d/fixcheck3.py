import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
def xc(p, q, span=4):
    best = (-1, 0)
    for s in range(-span, span + 1):
        qs = np.roll(q, s); den = np.sqrt((p * p).sum() * (qs * qs).sum()); c = (p * qs).sum() / den if den else 0
        best = max(best, (round(float(c), 3), s))
    return best
say('## L3 bidi — blue row-profile best shift vs ref (rows y0-4..y0+24), per RTL band, and the column-profile corr at 0')
blue = pt._blue; rb = ref('css-text', 'bidi/bidi-tab-001')
rows = [('web', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'web')), ('ios', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'ios')), ('fixed-android', pt.l3_fixed('bidi-tab-001'))]
rows += [(f, fk(f'L3-bidi-{f}', 'android', 'bidi/bidi-tab-001')) for f in ('rtl-run-order-mirrored', 'rtl-runs+2y', 'yellow-boxes-erased')]
for l, a in rows:
    rs = [xc(blue(a[y0 - 4:y0 + 24]).sum(axis=1).astype(float), blue(rb[y0 - 4:y0 + 24]).sum(axis=1).astype(float)) for y0 in (86, 106, 186, 226)]
    cc = []
    for y0 in (86, 106, 186, 226):
        p = blue(a[y0:y0 + 20]).sum(axis=0).astype(float); q = blue(rb[y0:y0 + 20]).sum(axis=0).astype(float); cc.append(round(float((p * q).sum() / np.sqrt((p * p).sum() * (q * q).sum())), 3))
    say(f'  {l:24} rowshift {[r[1] for r in rs]} (peak {[r[0] for r in rs]}) colcorr@0 {cc}')
