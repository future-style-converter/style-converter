#!/usr/bin/env python3
# tools/titan/results/wave54-plan/rtl-marker-bake.look.py
# The PNG measurements behind the rtl-marker-bake brief §2/§3, as one re-runnable look (pure PIL, frozen runs only):
#   A. counter-suffix ANDROID at wave53-probe (U2 under the <li>): every ink blob below y205, so the "+20 px" and the
#      "digit runs never painted" readings are lookups. Ref marker ink for comparison.
#   B. selectors/dir-style-02a ANDROID at wave53-final: the same host-inactive multi-run-box shape already in the corpus
#      (box Height 20, runs "This element is rtl" + "."): is the "." painted anywhere?
#   C. anchor-center-safe-rtl ANDROID at wave53-final: the host-ACTIVE control (a 4-run box, runs at Top 0/20/40/60).
#   D. counter-suffix LTR rows 1-8 marker/text extents per platform at wave53-final (the rows no unit here touches).
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import load, dark, bands, paths  # noqa: E402
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
RUNS = f'{ROOT}/tools/titan/runs'


def blobs(px, w, h, y0, pred):
    # 8-connected ink components at y >= y0: (top, bottom, left, right, pixels).
    seen, out = set(), []
    for y in range(y0, h):
        for x in range(w):
            if (x, y) in seen or not pred(px[x, y]):
                continue
            stack, pts = [(x, y)], []
            seen.add((x, y))
            while stack:
                cx, cy = stack.pop(); pts.append((cx, cy))
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and y0 <= ny < h and (nx, ny) not in seen and pred(px[nx, ny]):
                            seen.add((nx, ny)); stack.append((nx, ny))
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            out.append((min(ys), max(ys), min(xs), max(xs), len(pts)))
    return sorted(out)


print('A. counter-suffix, marker column x129-165, y205-340 (ink bands)')
for label, p in paths('wave53-probe', 'css-counter-styles', 'counter-suffix'):
    im, px = load(p)
    print(f'   {label:8}', ' '.join(f'y{b[0]}-{b[1]}:x{b[2]}-{b[3]}' for b in bands(px, 129, 165, 205, 340, dark)))
im, px = load(dict(paths('wave53-probe', 'css-counter-styles', 'counter-suffix'))['android'])
print('   android wave53-probe blobs at x>=129, y>=205:', ' '.join(f'y{b[0]}-{b[1]}:x{b[2]}-{b[3]}(n{b[4]})'
      for b in blobs(px, im.size[0], im.size[1], 205, dark) if b[2] >= 129))

print('B. dir-style-02a wave53-final, leading "." column x16-20 (ink bands) and row 7 extent')
ink = lambda q: q[0] + q[1] + q[2] < 600
for label, p in paths('wave53-final', 'selectors', 'dir-style-02a'):
    im, px = load(p)
    rows = bands(px, 0, 390, 10, 240, ink)
    print(f'   {label:8} dot rows', ' '.join(f'y{b[0]}-{b[1]}' for b in bands(px, 16, 21, 10, 240, ink)),
          '| row 7', ' '.join(f'y{r[0]}-{r[1]}:x{r[2]}-{r[3]}' for r in rows if 138 <= r[0] <= 142))

print('C. anchor-center-safe-rtl wave53-final android, the 4-run box (x17-111) ink rows')
im, px = load(f'{RUNS}/wave53-final/sections/css-anchor-position/android-screenshots/wpt__css-anchor-position__anchor-center-safe-rtl.png')
print('   ', ' '.join(f'y{b[0]}-{b[1]}:x{b[2]}-{b[3]}' for b in bands(px, 17, 111, 17, 160, lambda q: q[0] + q[1] + q[2] < 450)))

print('D. counter-suffix wave53-final LTR rows: marker ink (x<63) / text ink (x63-99)')
for label, p in paths('wave53-final', 'css-counter-styles', 'counter-suffix'):
    im, px = load(p)
    cells = []
    for r in bands(px, 0, 100, 14, 206, dark):
        m = [x for x in range(0, 63) for y in range(r[0], r[1] + 1) if dark(px[x, y])]
        cells.append(f'y{r[0]}-{r[1]} m x{min(m)}-{max(m)}' if m else f'y{r[0]}-{r[1]} m -')
    print(f'   {label:8}', ' | '.join(cells))
# The Hebrew row-3 marker order: the column of the period (2x2 dot at the baseline row y80-81) vs the aleph.
for label, p in paths('wave53-final', 'css-counter-styles', 'counter-suffix'):
    im, px = load(p)
    base = [x for x in range(40, 62) if dark(px[x, 81])]
    print(f'   {label:8} row 3 baseline (y81) ink x: {base}')
