#!/usr/bin/env python3
# hyphenate-character.replay.py — wave-54 planning replay (read-only on the run dirs).
# Builds synthetic pictures that isolate the three defects SEEN in the
# hyphenate-character-00{1,3,4} captures (see hyphenate-character-compose.md §2):
#   G  the hyphenate string (glyph + the line breaks its width decides),
#   B  the extra blank line the extractor's br heights add before groups 3 and 4,
#   W  the lost <b> in the preamble <p> (extractor 'inline-run-merged', corpus-wide).
# Each picture starts from the frozen REF (G right by construction) and re-injects
# the defects that a candidate fix would leave behind, using the platform's own
# capture for W (its non-bold <p> rows) and its own measured shift for B
# (hyphenate-character.bands.py: web +22/+43, android +23/+44, ios +20/+40 px for
# groups 3/4). Scored by hyphenate-character.replay-score.mjs with the gate's
# composed metric. The web -004 capture (glyphs and breaks already right, B and W
# present) is the calibration: the model "G fixed, B+W left" must reproduce it.
import os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hyphenate-character-replay')
os.makedirs(OUT, exist_ok=True)
REF = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text'
SEC = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-text'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
SHIFT = {'web': (22, 43), 'android': (23, 44), 'ios': (20, 40)}
# Ref geometry from hyphenate-character.bands.py: (p rows end = div start,
# split g2|g3, split g3|g4) — the mid-points of the blank bands between groups.
GEOM = {'001': (100, 306, 382), '003': (80, 286, 362), '004': (80, 267, 362)}
S12 = {'001': 211, '003': 191, '004': 172}   # split g1|g2, the mid-point of the first blank band
DRIFT = {'web': (0, 1, 2, 3), 'ios': (0, 0, 0, 0), 'android': (0, 1, 2, 3)}
# GBn = the U3b risk arm for iOS: if the line-start br box shrinks 20 -> 19.2 px and iOS's zero drift was that 0.8 px
# compensating something else, iOS lands 0/-1/-2/-3 px (web/android get GB, drift 0, under U3b).
NEG = (0, -1, -2, -3)
def compose(t, plat, keep_bold, keep_br):
    ref = Image.open(f'{REF}/hyphens__hyphenate-character-{t}.png').convert('RGBA')
    cap = Image.open(f'{SEC}/{DIRS[plat]}/wpt__css-text__hyphens__hyphenate-character-{t}.png').convert('RGBA')
    div0, s23, s34 = GEOM[t]
    out = ref.copy()
    if not keep_bold:  # W left: the platform's own non-bold <p> rows
        out.paste(cap.crop((0, 0, 390, div0)), (0, 0))
    if not keep_br:    # B left: groups 3 and 4 pushed down by the platform's shift
        d3, d4 = SHIFT[plat]
        white = Image.new('RGBA', (390, 600 - s23), (255, 255, 255, 255))
        out.paste(white, (0, s23))
        out.paste(ref.crop((0, s23, 390, s34)), (0, s23 + d3))
        out.paste(ref.crop((0, s34, 390, 600 - d4)), (0, s34 + d4))
    return out
def drifted(t, plat, drift=None):
    # GB with the platform's surviving per-group drift: the ref's four word groups, each moved down by DRIFT px.
    drift = drift or DRIFT[plat]
    out = compose(t, plat, False, True)
    ref = Image.open(f'{REF}/hyphens__hyphenate-character-{t}.png').convert('RGBA')
    div0, s23, s34 = GEOM[t]
    cuts = [div0, S12[t], s23, s34, 600]
    base = out.copy()
    out.paste(Image.new('RGBA', (390, 600 - div0), (255, 255, 255, 255)), (0, div0))
    for (a, b), d in zip(zip(cuts, cuts[1:]), drift):
        out.paste(ref.crop((0, a - min(d, 0), 390, b - max(d, 0))), (0, a + max(d, 0)))
    return out
for t in GEOM:
    for plat in DIRS:
        for tag, kb, kbr in (('G', False, False), ('GB', False, True), ('GW', True, False)):
            compose(t, plat, kb, kbr).save(f'{OUT}/{t}-{plat}-{tag}.png')
        drifted(t, plat).save(f'{OUT}/{t}-{plat}-GBd.png')
        if plat == 'ios':
            drifted(t, plat, NEG).save(f'{OUT}/{t}-{plat}-GBn.png')
print('wrote', len(os.listdir(OUT)), 'pictures to', OUT)
