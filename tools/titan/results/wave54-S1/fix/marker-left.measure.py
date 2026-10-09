#!/usr/bin/env python3
# wave54-S1/fix/marker-left.measure.py — confirms the number the [M] left-edge rule pins (S1 should-fix 1): the
# marker-ink LEFT edge of each of counter-suffix's four dir=rtl rows, on the frozen ref and on the wave53-probe
# web / iOS captures (the pictures the rule must keep OK). Read-only; pure PIL via the wave-53 geometry helpers.
# Usage: python3 tools/titan/results/wave54-S1/fix/marker-left.measure.py
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'wave53-plan'))         # geometry_common: refs path + PNG decode
from geometry_common import paths, load, dark, bands                      # noqa: E402  (shared helpers)
TEXT_ROWS = ((214, 225), (238, 249), (262, 273), (286, 297))              # the ref's four RTL text bands (y)
for run in ('wave53-probe',):                                             # the reverted-M probe run: web / iOS are right
    for label, p in paths(run, 'css-counter-styles', 'counter-suffix'):   # ref + web + ios + android
        im, px = load(p)                                                  # None when the capture is missing
        if im is None or label == 'android':                              # android is the reverted shape: not a target
            continue
        cells = []
        for y0, y1 in TEXT_ROWS:                                          # same window the rtl-marker probe scans (±2/+3)
            # Two thresholds: sum < 300 is the probes' `dark` core; sum < 600 includes the antialiased edge.
            core = [x for x in range(129, 151) for y in range(y0 - 2, y1 + 3) if dark(px[x, y])]
            edge = [x for x in range(129, 151) for y in range(y0 - 2, y1 + 3) if sum(px[x, y]) < 600]
            cells.append(f'y{y0}-{y1} core x{min(core)}-{max(core)} edge x{min(edge)}-{max(edge)}')
        print(f'{run} {label:4} ' + ' | '.join(cells))
