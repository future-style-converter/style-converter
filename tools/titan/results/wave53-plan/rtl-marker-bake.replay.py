#!/usr/bin/env python3
# tools/titan/results/wave53-plan/rtl-marker-bake.replay.py
# Wave-53 plan, family rtl-marker-bake: PNG replay of the predicted post-fix
# pictures for css-counter-styles/counter-suffix (wave52-ship captures vs the
# frozen ref). Pixel SIMULATION, not a capture: the RTL marker ink is the REF's
# own pixels pasted at the predicted place, so every predicted SSIM is an
# UPPER BOUND for the marker half (native glyphs differ from Chromium's).
# Writes PNGs to argv[1] (a scratch dir); score them with
# rtl-marker-bake.replay-score.mjs.
import sys, os
from PIL import Image
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
REF = f'{R}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-counter-styles/counter-suffix.png'
CAP = lambda d: f'{R}/tools/titan/runs/wave52-ship/sections/css-counter-styles/{d}/wpt__css-counter-styles__counter-suffix.png'
ref = Image.open(REF).convert('RGBA')
BAND = (208, 302)                     # the four dir=rtl rows (ref ink y213-297)
MARK = (129, 150)                     # ref marker ink x133-145, padded; li border box ends x128
def paste_ref_marker(img, dx=0):
    box = (MARK[0], BAND[0], MARK[1], BAND[1])
    img.paste(ref.crop(box), (MARK[0] + dx, BAND[0]))
def blank(img, x0, x1):
    img.paste((255, 255, 255, 255), (x0, BAND[0], x1, BAND[1]))
# iOS, hunk M: text already at x103-127; add the marker where the ref has it.
ios = Image.open(CAP('ios-screenshots')).convert('RGBA'); paste_ref_marker(ios); ios.save(f'{OUT}/ios-M.png')
# Android, hunk M alone: the marker run inherits the li's +48 px content-box offset.
a = Image.open(CAP('android-screenshots')).convert('RGBA'); paste_ref_marker(a, dx=48); a.save(f'{OUT}/android-M.png')
# Android, hunks M + P: the root padding zeroed → the RTL band moves 48 px left, marker at the ref's place.
a2 = Image.open(CAP('android-screenshots')).convert('RGBA')
band = a2.crop((140, BAND[0], 200, BAND[1])); blank(a2, 90, 210); a2.paste(band, (92, BAND[0])); paste_ref_marker(a2)
a2.save(f'{OUT}/android-MP.png')
# Android, hunk P alone (no marker run): text moves onto the ref's x103-127, still no marker.
a3 = Image.open(CAP('android-screenshots')).convert('RGBA')
band = a3.crop((140, BAND[0], 200, BAND[1])); blank(a3, 90, 210); a3.paste(band, (92, BAND[0])); a3.save(f'{OUT}/android-P.png')
# Web, hunk M: Blink's left-hanging ::marker (x46-58) gone (list-style-type none), marker run at the ref's place.
w = Image.open(CAP('screenshots')).convert('RGBA'); blank(w, 40, 63); paste_ref_marker(w); w.save(f'{OUT}/web-M.png')
print('wrote', OUT)
# ── Hunk P's other carriers: the two padded css-text bake roots on Android ──
# Measured (wave52-ship): Android paints every run inside these roots +4 px
# right of the ref/web/iOS (bidi-lines-002 x36 vs x32; -001 x33 vs x29) — the
# 0.5ch padding-left taken from the content box. Replay: shift the box
# interior (inside its 3 px border) 4 px left.
for stem, (y0, y1) in {'bidi__bidi-lines-002': (111, 311), 'bidi__bidi-lines-001': (111, 351)}.items():
    p = f'{R}/tools/titan/runs/wave52-ship/sections/css-text/android-screenshots/wpt__css-text__{stem}.png'
    im = Image.open(p).convert('RGBA')
    inner = im.crop((23, y0, 358, y1))
    im.paste((255, 255, 255, 255), (19, y0, 358, y1))
    im.paste(inner, (19, y0))
    im.save(f'{OUT}/android-P-{stem}.png')
