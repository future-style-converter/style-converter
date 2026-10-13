import os, sys, importlib.util
sys.dont_write_bytecode = True
import numpy as np
from PIL import Image
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
def corr2(a, b):
    a = a.astype(float).ravel(); b = b.astype(float).ravel(); a -= a.mean(); b -= b.mean(); d = np.sqrt((a * a).sum() * (b * b).sum()); return round(float((a * b).sum() / d), 3) if d else 0
def best2(a, b, span=3):
    """(peak 2-D correlation, (dx, dy)) of mask a against mask b translated by -span..span."""
    best = (-1, (0, 0))
    for dy in range(-span, span + 1):
        for dx in range(-span, span + 1):
            best = max(best, (corr2(np.roll(np.roll(a, dy, 0), dx, 1), b), (dx, dy)))
    return best
say('## L3 bidi — per RTL band blue mask: 2-D correlation at (0,0) and its best translation vs the ref')
blue = pt._blue; rb = ref('css-text', 'bidi/bidi-tab-001'); B = pt.l3_fixed('bidi-tab-001')
rows = [('web', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'web')), ('ios', pt.cap('wave54-final', 'css-text', 'bidi/bidi-tab-001', 'ios')), ('fixed-android', B)]
rows += [(f, fk(f'L3-bidi-{f}', 'android', 'bidi/bidi-tab-001')) for f in ('rtl-run-order-mirrored', 'rtl-runs+2y')]
rows += [(f'r1:{f}', pt.A(Image.open(f'{SCR}/teeth-rerun/fakeruns/L3-bidi-{f}/sections/css-text/android-screenshots/wpt__css-text__bidi__bidi-tab-001.png'))) for f in ('gut', 'rtl+2x', 'rtl-3x')]
for l, a in rows:
    say(f'  {l:24} ' + ' '.join(f'y{y0}: {corr2(blue(a[y0-2:y0+22]), blue(rb[y0-2:y0+22]))}/{best2(blue(a[y0-2:y0+22]), blue(rb[y0-2:y0+22]))}' for y0 in (86, 106, 186, 226)))
