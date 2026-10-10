#!/usr/bin/env python3
# Skeptic (wave 54 L5) — synthetic "after U1-android / U2-ios" pictures for inset-005/-006, built from
# TODAY's native capture (prose, canvas) with the heading replaced by the frozen ref's own 2em-bold
# heading glyphs, under three geometries:
#   A  face, NO wrap (Compose: RenderAbsoluteChild → absposOverflowMeasure measures Constraints()
#      unbounded, so "fox" stays on line 1, ink clipped at x390), NO UA margin (the abspos <h1> gets
#      no .67em margin on either native: the wire carries none, unlike inset-001…004's baked 21.44)
#   B  face + wrap, NO UA margin (heading lifted 21 px)
#   C  face + wrap + margin (the ref's heading rows verbatim — the ceiling)
# A replay ESTIMATE of the score's SIGN, not a capture; scored by score.mjs with diffWebVsRef.
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, *(['..'] * 5)))
REF = os.path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor')
RUN = os.path.join(ROOT, 'tools/titan/runs/wave53-final/sections/css-text-decor')
OUT = os.path.join(HERE, 'png'); os.makedirs(OUT, exist_ok=True)
LIFT, PITCH = 21, 40  # .67em × 32 = 21.44 px margin; 32 px × the pinned 1.25 line-height
for t in ('text-decoration-inset-005', 'text-decoration-inset-006'):
    ref = Image.open(os.path.join(REF, t + '.png')).convert('RGB'); W, H = ref.size
    for plat, d in (('android', 'android-screenshots'), ('ios', 'ios-screenshots')):
        cap = Image.open(os.path.join(RUN, d, f'wpt__css-text-decor__{t}.png')).convert('RGB')
        assert cap.size == ref.size, (cap.size, ref.size)
        def base():
            im = cap.copy(); im.paste((255, 255, 255), (0, 104, W, H)); return im  # erase today's heading
        line1 = ref.crop((0, 128, W, 178)); fox = ref.crop((10, 182, 72, 216))
        a = base(); a.paste(line1, (0, 128 - LIFT)); a.paste(fox, (345 - 6, 182 - PITCH - LIFT))
        b = base(); b.paste(ref.crop((0, 110, W, H)), (0, 110 - LIFT))
        c = base(); c.paste(ref.crop((0, 110, W, H)), (0, 110))
        for k, im in (('A-nowrap-nomargin', a), ('B-wrap-nomargin', b), ('C-wrap-margin', c)):
            im.save(os.path.join(OUT, f'synth-{t}-{plat}-{k}.png'))
print('ok')
