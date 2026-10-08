#!/usr/bin/env python3
# tools/titan/results/wave54-plan/rtl-marker-bake.replay.py
#
# Wave-54 plan, family rtl-marker-bake: PNG replays of counter-suffix ANDROID for the two re-do units, built from the
# DEVICE's own pictures (wave53-final = no P, no M; wave53-probe = the reverted M + P under the <li>). Pixel SIMULATION,
# not a capture — the wave-53 lesson (BACKLOG "An upstream bake that positions runs needs a device probe before the
# gate"): a replay never runs the Compose layout, so it bounds a score and never labels a picture. Score with
# rtl-marker-bake.replay-score.mjs. Writes PNGs to argv[1] (a scratch dir).
#   android-P.png      unit P alone: wave53-final's RTL text band (x140-200, the +48 px content-box shift) moved to
#                      x92 — the wave-53 recipe, device-calibrated on bidi-lines (replay 0.9818 / 0.9629 = wave53-probe).
#   android-Mp-dev.png unit M' + P, device glyphs: wave53-probe's marker ink (x129-150), which Compose stacked ONE RUN
#                      HEIGHT (20 px) low, moved up 20 px; the '1' / '2' digit runs Compose never painted (0-px slot)
#                      taken from the REF (the only source of a tabular '1' with its foot).
#   android-Mp-ref.png unit M' + P, ref marker: wave53-probe with the marker column blanked and the REF's marker
#                      pixels pasted (the wave-53 upper bound, now on the P-corrected device text).
import os
import sys
from PIL import Image

R = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
REF = (f'{R}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'css-counter-styles/counter-suffix.png')
CAP = lambda run: f'{R}/tools/titan/runs/{run}/sections/css-counter-styles/android-screenshots/wpt__css-counter-styles__counter-suffix.png'
ref = Image.open(REF).convert('RGBA')
BAND = (208, 302)        # the four dir=rtl rows (ref ink y214-297)
MARK = (129, 151)        # ref marker ink x133-145, padded; the li border box ends at x128


def blank(img, x0, y0, x1, y1):
    # Paint the canvas white over [x0,x1)×[y0,y1) (the capture background is pure white).
    img.paste((255, 255, 255, 255), (x0, y0, x1, y1))


# Unit P alone (no marker run): Android's RTL text x152-174 → the ref's x104-126.
a = Image.open(CAP('wave53-final')).convert('RGBA')
band = a.crop((140, BAND[0], 200, BAND[1])); blank(a, 90, BAND[0], 210, BAND[1]); a.paste(band, (92, BAND[0]))
a.save(f'{OUT}/android-P.png')

# Unit M' + P with the device's own marker glyphs: lift the stacked marker ink by one run height (20 px).
b = Image.open(CAP('wave53-probe')).convert('RGBA')
ink = b.crop((MARK[0], BAND[0] + 20, MARK[1], BAND[1] + 30))     # y228-332: '.' ×2, 'א.', 'ב.' drawn 20 px low
blank(b, MARK[0], BAND[0], MARK[1], BAND[1] + 30)
b.paste(ink, (MARK[0], BAND[0]))
# The digit runs '1' / '2' (ref ink x136-146 on rows 1-2, y212-227 / y236-251) were never painted on the device.
for y0 in (212, 236):
    b.paste(ref.crop((136, y0, 147, y0 + 16)), (136, y0))
b.save(f'{OUT}/android-Mp-dev.png')

# Unit M' + P with the ref's marker pixels (upper bound on the marker half).
c = Image.open(CAP('wave53-probe')).convert('RGBA')
blank(c, MARK[0], BAND[0], MARK[1], BAND[1] + 30)
c.paste(ref.crop((MARK[0], BAND[0], MARK[1], BAND[1])), (MARK[0], BAND[0]))
c.save(f'{OUT}/android-Mp-ref.png')
print('wrote', OUT)
