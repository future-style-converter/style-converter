#!/usr/bin/env python3
# Skeptic (wave 54 L5) — does ua-heading-face.geometry.py's inset rule see a VERTICAL offset?
# Fake: the frozen ref for inset-005/-006/-014 with every row from y110 down lifted by N px (the
# missing UA `margin-top: .67em` of an abspos <h1> that no native path supplies — 21.44 px at the
# 2em face), i.e. the right face + the right wrap at the wrong height. Runs the probe's OWN
# verdict_inset / bands code (exec'd from the probe file up to its CASES table) on the fake, and
# scores the fake against the ref with a plain per-pixel mismatch count for scale.
import os, sys
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, *(['..'] * 5)))
PROBE = os.path.join(ROOT, 'tools/titan/results/wave54-plan/ua-heading-face.geometry.py')
src = open(PROBE).read().split('CASES = [')[0]
ns = {'__file__': PROBE}; sys.argv = [PROBE, 'wave53-final']; exec(compile(src, PROBE, 'exec'), ns)
REF = os.path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor')
for test, (h1, h2, tol) in [('text-decoration-inset-005', (30, 18, 4)), ('text-decoration-inset-006', (30, 18, 4)), ('text-decoration-inset-014', (20, 20, 6))]:
    im = Image.open(os.path.join(REF, test + '.png')).convert('RGB')
    w, h = im.size
    _, rpx = ns['load'](os.path.join(REF, test + '.png'))
    rb = ns['bands'](rpx, 0, w, 110, min(h, 330), ns['dark'], gap=2)
    for lift in (0, 10, 21, 24):
        fake = Image.new('RGB', (w, h), (255, 255, 255))
        fake.paste(im.crop((0, 0, w, 110)), (0, 0))                       # prose above y110 untouched
        fake.paste(im.crop((0, 110, w, h)), (0, 110 - lift))               # heading rows lifted
        fake.paste(im.crop((0, 0, w, 110)).crop((0, 110 - lift, w, 110)) if False else Image.new('RGB', (w, 0)), (0, 0))
        p = os.path.join(HERE, 'png', f'fake-{test}-lift{lift}.png'); fake.save(p)
        _, fpx = ns['load'](p)
        fb = ns['bands'](fpx, 0, w, 110 - lift if lift else 110, min(h, 330), ns['dark'], gap=2)
        fb = [b for b in fb if b[0] >= 110 - lift]
        why = ns['verdict_inset'](fb, rb, h1, h2, tol)
        # the probe scans from y110: re-scan exactly as the probe would on a capture
        fb_probe = ns['bands'](fpx, 0, w, 110, min(h, 330), ns['dark'], gap=2)
        why_probe = ns['verdict_inset'](fb_probe, rb, h1, h2, tol)
        print(f'{test} lift {lift:>2}px: probe-scan bands {fb_probe} → ' + ('GEOMETRY OK' if not why_probe else f'GEOMETRY WRONG ({why_probe})') + f'   | ref bands {rb}')
