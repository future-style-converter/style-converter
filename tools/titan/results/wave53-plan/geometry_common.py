# tools/titan/results/wave53-plan/geometry_common.py
#
# Shared helpers for the wave-53 post-gate GEOMETRY probes (plan-skeptic must-fix 2): lists-bakes.geometry.py,
# soft-hyphen.geometry.py, canvas-root.geometry.py, float-avoid.geometry.py — siblings of display-table-body.geometry.py.
# Each probe takes a run id, decodes the frozen ref plus the three composed captures of a handful of tests, and prints
# one line per (test, platform) that ENDS in an exact verdict string. PLAN.md §6 R6 and expectations.json
# `lanes.*.geometryProbe` cite those strings; the ref row of every probe must print the OK verdict (self-check: the
# rule describes the picture the test asks for), and the wave52-ship rows print the pre-fix WRONG verdict (the rule can
# fail). Pure PIL; keep each probe to a handful of PNGs (the host may be gating).
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
# The frozen ref corpus every wave-52/53 score is computed against (same path display-table-body.geometry.py reads).
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin')
# Composed-capture folders inside a gate section, per platform (section-runner layout).
DIRS = [('ref', None), ('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]


def paths(run, section, test):
    """[(label, png path)] for the ref and the three platform captures of `<section>/<test>.html` in `run`."""
    flat = test.replace('/', '__')
    out = []
    for label, d in DIRS:
        if d is None:
            out.append((label, f'{REFS}/{section}/{flat}.png'))
        else:
            out.append((label, f'{ROOT}/tools/titan/runs/{run}/sections/{section}/{d}/wpt__{section}__{flat}.png'))
    return out


def load(path):
    """RGB image + pixel accessor, or (None, None) when the capture is missing (printed as MISSING by the caller)."""
    if not os.path.exists(path):
        return None, None
    im = Image.open(path).convert('RGB')
    return im, im.load()


def dark(p):
    """Text ink: every glyph in these tests is black/near-black on white (sum < 300 keeps antialiased cores)."""
    return p[0] + p[1] + p[2] < 300


def orange(p):
    """CSS `orange` (255,165,0) as the briefs measured it: r>200, 120<g<200, b<80."""
    return p[0] > 200 and 120 < p[1] < 200 and p[2] < 80


def bands(px, x0, x1, y0, y1, pred, gap=1):
    """Vertical bands of rows holding at least one pixel matching `pred` inside [x0,x1)×[y0,y1).
    Rows closer than `gap` blank rows are merged. Returns [(top, bottom, xmin, xmax)]."""
    out, cur = [], None
    blank = 0
    for y in range(y0, y1):
        xs = [x for x in range(x0, x1) if pred(px[x, y])]
        if xs:
            if cur is None:
                cur = [y, y, min(xs), max(xs)]
            else:
                cur[1] = y; cur[2] = min(cur[2], min(xs)); cur[3] = max(cur[3], max(xs))
            blank = 0
        elif cur is not None:
            blank += 1
            if blank > gap:
                out.append(tuple(cur)); cur = None; blank = 0
    if cur is not None:
        out.append(tuple(cur))
    return out


def bbox(px, w, h, pred, region=None):
    """Bounding box (x0, y0, x1, y1) and count of pixels matching `pred`, optionally inside region (x0,y0,x1,y1)."""
    rx0, ry0, rx1, ry1 = region or (0, 0, w, h)
    xs0 = ys0 = 10 ** 9; xs1 = ys1 = -1; n = 0
    for y in range(ry0, ry1):
        for x in range(rx0, rx1):
            if pred(px[x, y]):
                n += 1
                xs0 = min(xs0, x); xs1 = max(xs1, x); ys0 = min(ys0, y); ys1 = max(ys1, y)
    return (None if n == 0 else (xs0, ys0, xs1, ys1)), n


def near(a, b, tol):
    """|a-b| <= tol for scalars, element-wise for equal-length sequences."""
    if isinstance(a, (list, tuple)):
        return len(a) == len(b) and all(abs(x - y) <= tol for x, y in zip(a, b))
    return abs(a - b) <= tol
