import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
blue = pt._blue; rb = ref('css-text', 'bidi/bidi-tab-001')
for l, a in [('ref', rb), ('web', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'web')), ('fixed', pt.l3_fixed('bidi-tab-001')), ('+2y', fk('L3-bidi-rtl-runs+2y', 'android', 'bidi/bidi-tab-001'))]:
    out = []
    for y0 in (86, 106, 186, 226):
        ys, xs = np.nonzero(blue(a[y0:y0 + 22])); out.append((int(ys.min()) + y0, int(ys.max()) + y0, int(xs.min()), int(xs.max())))
    print(l, out)
