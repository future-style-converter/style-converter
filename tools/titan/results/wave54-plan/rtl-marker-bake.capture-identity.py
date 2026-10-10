#!/usr/bin/env python3
# tools/titan/results/wave54-plan/rtl-marker-bake.capture-identity.py
# Hunk P's web/iOS invariance, MEASURED rather than argued: wave53-probe carried U2 (M + P); on bidi-lines-001/-002 and
# anchor-center-safe-rtl only hunk P changed the wire (rtl-marker-bake.ir-diff.out.txt). This prints a decoded-pixel
# sha1 per capture for wave53-open / wave53-probe / wave53-final, so "P moved only Android" is a lookup, not a claim.
# Pure PIL. Usage: python3 tools/titan/results/wave54-plan/rtl-marker-bake.capture-identity.py
import hashlib, os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
DOCS = [('css-text', 'css-text__bidi__bidi-lines-001'), ('css-text', 'css-text__bidi__bidi-lines-002'),
        ('css-anchor-position', 'css-anchor-position__anchor-center-safe-rtl'),
        ('css-counter-styles', 'css-counter-styles__counter-suffix'), ('selectors', 'selectors__dir-style-02a')]


def h(p):
    # sha1 of the decoded RGBA pixels (PNG re-encodes do not count as a change).
    return hashlib.sha1(Image.open(p).convert('RGBA').tobytes()).hexdigest()[:12] if os.path.exists(p) else 'MISSING'


for sec, stem in DOCS:
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        o, pr, f = (h(f'{ROOT}/tools/titan/runs/{r}/sections/{sec}/{d}/wpt__{stem}.png') for r in ('wave53-open', 'wave53-probe', 'wave53-final'))
        print(f'{stem:52} {d:20} open {o} probe {pr} final {f}  probe {"==" if o == pr else "!="} open')
