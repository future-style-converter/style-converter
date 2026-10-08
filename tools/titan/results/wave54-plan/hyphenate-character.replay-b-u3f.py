#!/usr/bin/env python3
# hyphenate-character.replay-b-u3f.py — fix r2 (plan-skeptic round 2, R2-S4): replay B ON THEIR OWN PIXELS for the 12
# U3-radius rows the plan wrote "up or unchanged" / "up; a flip is possible" without a replay (the 9 cells of
# css-masking/clip-path/clip-path-filter-order, css-multicol/balance-grid-container and css-multicol/column-height-009,
# plus filter-effects/backdrop-filter-clip-rect / -edge-clipping / -paint-order android).
#
# Model (as hyphenate-character.replay-b-radius.py): U3 removes the 20 px a stray <br> adds after text that ends a line
# opened by text interleaved between children; where everything below that blank line is IN FLOW, cutting 20 rows out
# of the first blank band >= 20 px (one per affected br, census brHeight[].n) is a faithful picture of "U3 landed".
# Where it is not, the cell is printed NOT REPLAYABLE with the reason, and PLAN §2 L3 marks its row `undirected`:
#   - column-height-009 (all three): the 15 brs feed a multicol balance (web: 3 balanced columns; natives: one column);
#     removing them re-balances the columns, which no row cut models (skeptic-r2/look-column-height-009-…png);
#   - balance-grid-container ios / android: the natives paint the address on ONE line (no blank band exists to cut);
#     the 20 px br height changes that line box's height, which a row cut does not model;
#   - backdrop-filter-clip-rect / -edge-clipping / -paint-order android: the stray blank line sits UNDER the absolutely
#     positioned green box / pill, which do not move with the flow — a row cut would move them too
#     (fix-r2/look-backdrop-filter-*-ref-web-ios-android.png).
# Output: hyphenate-character-replay/<stem>-<plat>-B.png for the replayable cells; prints the cuts.
import os, json
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '../../../..'))
OUT = os.path.join(HERE, 'hyphenate-character-replay')
RUN = f'{ROOT}/tools/titan/runs/wave53-final/sections'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
census = json.load(open(os.path.join(HERE, 'hyphenate-character.census.json')))
n_by = {x['test']: x['n'] for x in census['brHeight']}
NOT_REPLAYABLE = {
    ('css-multicol/column-height-009.html', p): 'multicol re-balance (15 brs feed the column balance)' for p in DIRS}
NOT_REPLAYABLE |= {('css-multicol/balance-grid-container.html', p): 'no blank band: the native paints the address on one line (the br changes a line-box height)' for p in ('ios', 'android')}
NOT_REPLAYABLE |= {(f'filter-effects/backdrop-filter-{t}.html', 'android'): 'the stray line sits under abspos boxes that do not move with the flow'
                   for t in ('clip-rect', 'edge-clipping', 'paint-order')}
CELLS = [(t, p) for t in ('css-masking/clip-path/clip-path-filter-order.html', 'css-multicol/balance-grid-container.html',
                          'css-multicol/column-height-009.html') for p in DIRS] \
        + [(f'filter-effects/backdrop-filter-{t}.html', 'android') for t in ('clip-rect', 'edge-clipping', 'paint-order')]
assert len(CELLS) == 12
for test, plat in CELLS:
    sec, rest = test.split('/', 1)
    stem = rest[:-len('.html')].replace('/', '__')
    im = Image.open(f'{RUN}/{sec}/{DIRS[plat]}/wpt__{sec}__{stem}.png').convert('RGB')
    g = im.convert('L'); px = g.load(); w, h = g.size
    ink = [any(px[x, y] < 200 for x in range(w)) for y in range(h)]
    first = next(y for y in range(h) if ink[y]); last = max(y for y in range(h) if ink[y])
    gaps, start = [], None
    for y in range(first, last + 1):
        if not ink[y] and start is None: start = y
        if ink[y] and start is not None:
            if y - start >= 20: gaps.append((start, y))
            start = None
    if (test, plat) in NOT_REPLAYABLE:
        print(f'{test} {plat:8} NOT REPLAYABLE ({NOT_REPLAYABLE[(test, plat)]}); blank bands >= 20 px: {gaps}')
        continue
    n = n_by[test]
    cuts = [a for a, b in gaps[:n]]
    assert len(cuts) == n, (test, plat, gaps)
    out = Image.new('RGB', (w, h), (255, 255, 255)); src, dst = 0, 0
    for s in cuts:
        out.paste(im.crop((0, src, w, s)), (0, dst)); dst += s - src; src = s + 20
    out.paste(im.crop((0, src, w, h)), (0, dst))
    out.save(f'{OUT}/{sec}__{stem}-{plat}-B.png')
    print(f'{test} {plat:8} affected brs {n}; blank bands >= 20 px {gaps}; cut 20 rows at {cuts}')
