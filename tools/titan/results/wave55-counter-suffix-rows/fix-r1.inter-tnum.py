#!/usr/bin/env python3
# fix-r1.inter-tnum.py — wave-55 L5 FIX lane (fix-r1): an independent re-execution of the skeptic's defect-1 font fact.
# Question: which glyphs of the six U3-ios carrier runs (rows 9-12 of css-counter-styles/counter-suffix) does Inter's
# OpenType 'tnum' feature substitute? Font.monospacedDigit() asks CoreText for the face's tabular-figures feature on the
# WHOLE run, so every glyph the feature's GSUB lookups map changes — not only the digit row 9 was probed for.
# Reads the face the iOS harness registers (apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf) with fontTools
# (written independently of the skeptic's script, which is not in the lane dir: only its output is).
# Usage: python3 fix-r1.inter-tnum.py   (any python with fontTools; prints one line per carrier glyph, exit 0)
import os
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
f = TTFont(f'{ROOT}/apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf')
upem = f['head'].unitsPerEm                      # font units per em: px = units * 16 / upem at the 16-px carrier size
cmap = f.getBestCmap()                           # code point → glyph name
gs = f.getGlyphSet()
hmtx = f['hmtx']
gsub = f['GSUB'].table
# Collect every single substitution reachable from a 'tnum' feature record (LookupType 1, or 7 wrapping 1).
tnum = {}
for fr in gsub.FeatureList.FeatureRecord:
    if fr.FeatureTag != 'tnum':
        continue
    for li in fr.Feature.LookupListIndex:
        lk = gsub.LookupList.Lookup[li]
        for st in lk.SubTable:
            st = st.ExtSubTable if lk.LookupType == 7 else st
            for a, b in getattr(st, 'mapping', {}).items():
                tnum[a] = b


def ink(name):
    """(advance px, ink x-min px, ink x-max px) of a glyph at 16 px — the bounds pen over the CFF outline."""
    from fontTools.pens.boundsPen import BoundsPen
    bp = BoundsPen(gs)
    gs[name].draw(bp)
    s = 16 / upem
    adv = hmtx[name][0] * s
    if bp.bounds is None:
        return adv, None, None
    return adv, bp.bounds[0] * s, bp.bounds[2] * s


print(f'# fix-r1.inter-tnum.out.txt — fontTools {__import__("fontTools").version} over Inter-Regular.otf (upem {upem}); '
      f"'tnum' single substitutions in the face: {len(tnum)}")
for ch, where in (('1', 'row 9 digit run (__0__4__3)'), ('2', 'row 10 digit run (__0__4__5)'),
                  ('.', "rows 9-10 '.' runs (__0__4__2 / __0__4__4) and the period of rows 11-12 (__0__5__2 / __0__5__3)"),
                  ('א', 'row 11 letter'), ('ב', 'row 12 letter')):
    g = cmap.get(ord(ch))
    if g is None:
        print(f'{ch!r} [{where}]: not in this face (cmap None) — drawn by the fallback face, no Inter tnum')
        continue
    a = ink(g)
    t = tnum.get(g)
    if t is None:
        print(f'{ch!r} [{where}]: {g} adv {a[0]:.3f} px — NOT substituted by tnum')
        continue
    b = ink(t)
    print(f'{ch!r} [{where}]: {g} adv {a[0]:.3f} px, ink x {a[1]:.2f}..{a[2]:.2f}  --tnum-->  {t} adv {b[0]:.3f} px, ink x {b[1]:.2f}..{b[2]:.2f}')
