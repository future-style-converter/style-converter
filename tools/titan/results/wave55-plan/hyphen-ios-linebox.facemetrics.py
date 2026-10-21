#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.facemetrics.py — wave-55 L1 planning read-out (read-only).
# Why: the unit gives a declared-`normal` iOS run Blink's line spacing, round(asc)+round(desc)+round(gap) of the
# rendered face (simple_font_data.cc SimpleFontData::PlatformInit, via FontMetrics::AscentDescentWithHacks), in place
# of UIFont.lineHeight = asc+desc+gap unrounded, whose N-line Text height SwiftUI ceils. This prints both, per face
# and used size of the census radius (hyphen-ios-linebox.census.out.txt), from the face FILES with fontTools (hhea —
# the table CoreText reads), so every "box moves / box stays" sentence in the brief is arithmetic, not a guess.
import os
from fontTools.ttLib import TTFont
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
FACES = {
    'DejaVu Sans Mono (iOS monospace pin == Menlo)': f'{ROOT}/tools/titan/fonts/mono-pin/DejaVuSansMono.ttf',
    'Inter (iOS default design)': f'{ROOT}/apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf',
    'Linux Libertine (boundary-shaping "test" @font-face)': f'{ROOT}/tools/wpt/css/css-text/boundary-shaping/resources/LinLibertine_Re-4.7.5.woff.otf',
}
SIZES = {'DejaVu Sans Mono (iOS monospace pin == Menlo)': [13, 16, 20],
         'Inter (iOS default design)': [16, 36, 92],
         'Linux Libertine (boundary-shaping "test" @font-face)': [36]}
for name, path in FACES.items():
    f = TTFont(path, lazy=True)
    upem = f['head'].unitsPerEm; hh = f['hhea']
    asc, desc, gap = hh.ascent, -hh.descent, hh.lineGap
    print(f'{name}: upem {upem} hhea ascent {asc} descent {-desc} lineGap {gap}')
    for px in SIZES[name]:
        a, d, g = asc * px / upem, desc * px / upem, gap * px / upem
        raw = a + d + g
        blink = round(a) + round(d) + round(g)   # Python round is banker's at .5; flag exact halves below
        halves = [v for v in (a, d, g) if abs(v - int(v) - 0.5) < 1e-9]
        ceil1 = -(-raw // 1)
        print(f'   {px:>3}px  asc {a:.4f} desc {d:.4f} gap {g:.4f}  raw (UIFont.lineHeight) {raw:.4f}  ceil(raw) {ceil1:.0f}'
              f'  Blink round-each {blink}{"  (exact .5 present: lroundf rounds UP)" if halves else ""}'
              f'  per-line Δ {blink - raw:+.4f}  N=1..6 box today ceil(N·raw) {[int(-(-(n * raw) // 1)) for n in range(1, 7)]}'
              f' vs Blink N·L {[n * blink for n in range(1, 7)]}')
