#!/usr/bin/env python3
# hyphenate-character.drift.py — per-line vertical delta (capture top - ref top, core ink) for every line of the four
# word groups of hyphenate-character-00{1,3,4} in a run (default wave53-final), using hyphenate-character.geometry.py's
# own segmentation. Subtract 20 px per stray br (g3: 1, g4: 2; extractor br-height defect) to read the drift that a
# br fix leaves behind. Read-only.
import importlib.util, sys, io, contextlib, os
HERE = os.path.dirname(os.path.abspath(__file__))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
spec = importlib.util.spec_from_file_location('g', os.path.join(HERE, 'hyphenate-character.geometry.py'))
sys.argv = ['x', run]
with contextlib.redirect_stdout(io.StringIO()):
    g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
for t in ('001', '003', '004'):
    ref = g.groups(g.lines(f'{g.REFS}/hyphens__hyphenate-character-{t}.png', g.DIV0[t]))
    for plat, d in (('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')):
        got = g.groups(g.lines(f'{g.SEC}/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png', g.DIV0[t]))
        rows = [f'g{i + 1}:' + ','.join(f'{c[0] - r[0]:+d}' for r, c in zip(rg, cg)) for i, (rg, cg) in enumerate(zip(ref, got))]
        print(t, plat.ljust(8), ' '.join(rows))
