#!/usr/bin/env python3
# tools/titan/results/wave55-plan/positioned-parent-slot.replay.py
#
# Wave-55 plan, lane L3-positioned-parent-slot. READ-ONLY replays built from the platform's OWN wave54-final capture
# pixels (never the ref's), one arm per unit combination, for the gate metric to score
# (positioned-parent-slot.replay-score.mjs). Every geometry below is the wire's (per-test IR) arithmetic, checked
# against the measured ink in the brief §1/§2:
#
#   dir-style-02a android (rows are 20 px bands from y=16; row k spans y 16+20(k-1) .. 16+20k):
#     row 3 (y56-76)  — RELATIVE bake root, Box branch: ".This element is rtl" painted RIGHT today (x17-153).
#     row 6 (y116-136), row 9 (y176-196) — RTL ABSOLUTE boxes __3__2 / __4__2: mirrored to x246-373, no dot.
#     row 7 (y136-156) — LTR ABSOLUTE box __4__0: text right (x22-243), dot missing.
#     U1     = row 7 gets its dot (the 2-px dot of row 3, x14-21) — LTR parents only, RTL excluded.
#     U1U2   = U1 + rows 6/9 := row 3's band (same string, same runs, same colour; U2's physical-left origin).
#     PLAIN  = the mount WITHOUT the RTL guard: rows 6/9 ink lands at x >= 374 (16 + 358 + 4.61) — past the box's
#              right edge, where today's capture already clips row 6 at x373 — so rows 6/9 go blank; + row 7 dot.
#   bidi-tab-001 android (bands from y=66, 20 px; RTL boxes on rows 2, 3, 7, 9 = bands y86, y106, y186, y226):
#     U1U2   = each RTL row's '0' moved to the wire's physical x (box Left + run Left): rows 2/7/9 glyph x127-135 →
#              x28-36 (Δ -99), row 3 glyph x210-219 → x114-123 (copied from row 1, whose LTR '0' sits at that x).
#     PLAIN  = the mount without the guard: every RTL '0' a further +12 px right (the 0-wide slot at the box END).
#   anchor-position-multicol-nested-001 android (U1): every 10×10 square of a stacked parent moves up by the heights
#     of the out-of-flow siblings before it (10 px each): (360,88)→(360,78), (271,100)→(271,90), (351,132)→(351,112),
#     (190,188)→(190,168), (360,198)→(360,168).
#   dynamic-offset-rtl-001 android (U2, host ACTIVE today): the magenta 10×10 at x186-195 → x86-95 (16 + left 70).
# Usage: python3 positioned-parent-slot.replay.py   (writes positioned-parent-slot-replay/*.png)
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '../../../..'))
RUN = os.path.join(ROOT, 'tools/titan/runs/wave54-final/sections')
OUT = os.path.join(HERE, 'positioned-parent-slot-replay')
os.makedirs(OUT, exist_ok=True)


def cap(sec, stem):
    # The Android composed capture of one test at wave54-final.
    return Image.open(os.path.join(RUN, sec, 'android-screenshots', f'wpt__{sec}__{stem}.png')).convert('RGB')


def band(im, y0, y1):
    # A full-width horizontal band [y0, y1).
    return im.crop((0, y0, im.width, y1))


def fill(im, box, rgb):
    # Paint a rectangle a flat colour (the vacated background).
    im.paste(Image.new('RGB', (box[2] - box[0], box[3] - box[1]), rgb), box[:2])


# ── dir-style-02a ──
a = cap('selectors', 'dir-style-02a')
row = lambda k: (16 + 20 * (k - 1), 16 + 20 * k)
r3 = band(a, *row(3))
dot = a.crop((14, row(3)[0], 22, row(3)[1]))
u1 = a.copy()
u1.paste(dot, (14, row(7)[0]))
u1.save(os.path.join(OUT, 'dir-style-02a-android-U1.png'))
u12 = u1.copy()
for k in (6, 9):
    u12.paste(r3, (0, row(k)[0]))
u12.save(os.path.join(OUT, 'dir-style-02a-android-U1U2.png'))
plain = u1.copy()
for k in (6, 9):
    fill(plain, (0, row(k)[0], a.width, row(k)[1]), (255, 255, 255))
plain.save(os.path.join(OUT, 'dir-style-02a-android-PLAIN.png'))

# ── bidi-tab-001 ──
b = cap('css-text', 'bidi__bidi-tab-001')
YEL = b.getpixel((60, 96))  # the yellow root background behind the glyphs
u12b = b.copy()
plainb = b.copy()
for y0 in (86, 186, 226):  # rows 2, 7, 9: glyph x127-135 → x28-36
    g = b.crop((126, y0, 137, y0 + 20))
    fill(u12b, (126, y0, 137, y0 + 20), YEL)
    u12b.paste(g, (126 - 99, y0))
    fill(plainb, (126, y0, 137, y0 + 20), YEL)
    plainb.paste(g, (126 + 12, y0))
# row 3: the stray glyph at x210-219 (on white) vacates; the row-1 glyph (x114-123, LTR, right x) is copied down.
fill(u12b, (208, 106, 222, 126), (255, 255, 255))
u12b.paste(b.crop((112, 66, 124, 86)), (112, 106))
gs = b.crop((208, 106, 222, 126))
fill(plainb, (208, 106, 222, 126), (255, 255, 255))
plainb.paste(gs, (208 + 12, 106))
u12b.save(os.path.join(OUT, 'bidi-tab-001-android-U1U2.png'))
plainb.save(os.path.join(OUT, 'bidi-tab-001-android-PLAIN.png'))

# ── anchor-position-multicol-nested-001 ──
c = cap('css-anchor-position', 'anchor-position-multicol-nested-001')
GREEN = c.getpixel((195, 83))
u1c = c.copy()
for (x, y), (nx, ny) in [((360, 88), (360, 78)), ((271, 100), (271, 90)), ((351, 132), (351, 112)),
                         ((190, 188), (190, 168)), ((360, 198), (360, 168))]:
    fill(u1c, (x, y, x + 10, y + 10), (255, 255, 255))
for (x, y), (nx, ny) in [((360, 88), (360, 78)), ((271, 100), (271, 90)), ((351, 132), (351, 112)),
                         ((190, 188), (190, 168)), ((360, 198), (360, 168))]:
    fill(u1c, (nx, ny, nx + 10, ny + 10), GREEN)
u1c.save(os.path.join(OUT, 'anchor-position-multicol-nested-001-android-U1.png'))

# ── dynamic-offset-rtl-001 (host ACTIVE: U2 only) ──
d = cap('css-writing-modes', 'dynamic-offset-rtl-001')
MAG = d.getpixel((190, 30))
YEL2 = d.getpixel((50, 50))
u2d = d.copy()
fill(u2d, (186, 26, 196, 36), (255, 255, 255))
fill(u2d, (86, 26, 96, 36), MAG)
u2d.save(os.path.join(OUT, 'dynamic-offset-rtl-001-android-U2.png'))
# ── the three other host-ACTIVE RTL documents (U2 only): the magenta box to 16 + its wire `left` ──
for stem, (x, y), nx in (('dynamic-offset-vrl-rtl-001', (186, 26), 86), ('dynamic-offset-rtl-002', (126, 26), 26),
                         ('dynamic-offset-vrl-rtl-002', (186, 96), 86)):
    e = cap('css-writing-modes', stem)
    m = e.getpixel((x + 4, y + 4))
    fill(e, (x, y, x + 10, y + 10), (255, 255, 255))
    fill(e, (nx, y, nx + 10, y + 10), m)
    e.save(os.path.join(OUT, f'{stem}-android-U2.png'))
print('replays written to', OUT, '| sampled colours: yellow', YEL, 'green', GREEN, 'magenta', MAG, 'yellow2', YEL2)
