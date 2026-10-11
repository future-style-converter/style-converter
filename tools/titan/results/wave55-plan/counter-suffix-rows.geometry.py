#!/usr/bin/env python3
# tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py — the wave-55 L5 (counter-suffix rows) GEOMETRY probe.
#
# Why a new probe: wave53-plan/lists-bakes.geometry.py and wave54-plan/rtl-marker-bake.geometry.py read ONLY the four
# dir=rtl rows (y205-303) of css-counter-styles/counter-suffix, so their counter-suffix ios key printed GEOMETRY OK at
# wave54-final (wave54-gate/geometry-final.out.txt:11, class "report") on a capture whose rows 3-4 read ".א foo" /
# ".ב bar" — the suffix on the wrong side of the counter — and whose rows 5-6 start the CJK marker 4 px left of the ref.
# This probe reads the rows those two never look at, one key per (row family, platform):
#   [H] rows 3-4  LTR `hebrew` list: the suffix "." must FOLLOW the letter in the item's (ltr) direction
#                 (css-lists-3 §3.1.1 `::marker { unicode-bidi: isolate }` + css-writing-modes-4 §2.2: the isolate's
#                 base direction is the box's `direction`; UAX #9 N1/N2 then resolve the '.' to L) — period ink right
#                 of the letter ink, both at the ref's x ±2.
#   [C] rows 5-6  `cjk-decimal`: suffix "、" (U+3001, css-counter-styles-3 §6.1) has NO trailing space, so the marker's
#                 advance ends flush at the item's content edge (x64) — marker ink left at the ref's x33 ±1, its right
#                 edge (the comma) at the ref's ±2, a separate comma blob present, item text unmoved.
#   [T] row 9     the RTL `1` run carries FontVariantNumeric TABULAR_NUMS on the wire (css-fonts-4 font-variant-numeric):
#                 the tabular '1' (Inter one.tf, the foot serif) is ≥ 7 px of ink, the proportional '1' 4 px.
#   [L] row 1     the LTR `1.` marker's '1' (UA `::marker { font-variant-numeric: tabular-nums }`, css-lists-3 §3.1.1) —
#                 REPORT only: no wave-55 unit targets it (it is queued; the census shows its corpus radius).
#   [I] cjk-decimal/css3-counter-styles-005: an INSIDE "一、" marker is flush with the item text that follows it
#                 (same suffix, inline placement) — item text left at the ref's x ±1, marker left unmoved.
#   [K] with a base run: the must-not-move zones of counter-suffix, per platform, pixel-identical to base (web: the
#       whole capture; android: y0-111 + y160-599, i.e. all but rows 5-6; ios: y0-63 + y160-207 + y232-599, i.e. all but
#       rows 3-6 and row 9). Replaces the wave-54 "rows 0-207 identical" crop, which L5's units break by design.
# Every line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". The ref rows must print OK (exit 1 otherwise): the
# rule must describe the picture the test asks for. Pure PIL (geometry_common), 8 PNGs (+6 with a base run).
#
# Usage: python3 tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py <run-id> [base-run-id]
#        python3 … counter-suffix-rows.geometry.py replay:<tag> [base-run-id]   (reads counter-suffix-rows-replay/<tag>/)
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# Read-only probe: never write a __pycache__ beside the shared wave-53 helper (the tree may be under a running gate).
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(HERE, '..', 'wave53-plan'))
from geometry_common import paths, load, dark, bands, near  # noqa: E402  (shared helpers: refs path, PNG decode, bands)

args = sys.argv[1:]
run = args[0] if args else 'wave54-final'
base = args[1] if len(args) > 1 else None
ref_failed = False


def capture_paths(run_id, section, test):
    """paths() for a gate run, or the replay folder's <label>.png for `replay:<tag>` (ref always from the frozen refs)."""
    if not run_id.startswith('replay:'):
        return paths(run_id, section, test)
    tag = run_id.split(':', 1)[1]
    flat = test.replace('/', '__')
    out = []
    for label, p in paths('wave54-final', section, test):
        out.append((label, p if label == 'ref' else f'{HERE}/counter-suffix-rows-replay/{tag}/{flat}.{label}.png'))
    return out


def blobs(px, x0, x1, y0, y1):
    """Column-connected dark-ink blobs inside [x0,x1)×[y0,y1): [(xa, xb, ya, yb)] left to right."""
    cols = [x for x in range(x0, x1) if any(dark(px[x, y]) for y in range(y0, y1))]
    out, cur = [], None
    for x in cols:
        if cur and x == cur[1] + 1:
            cur[1] = x
        else:
            if cur:
                out.append(cur)
            cur = [x, x]
    if cur:
        out.append(cur)
    res = []
    for xa, xb in out:
        ys = [y for y in range(y0, y1) for x in range(xa, xb + 1) if dark(px[x, y])]
        res.append((xa, xb, min(ys), max(ys)))
    return res


# Line boxes of counter-suffix: 24-px rows (line-height 150% of 16 px) from y16 (ref ink row 1 y21-33 … row 12 y286-297).
ROW = {k: (16 + 24 * (k - 1), 16 + 24 * k) for k in range(1, 13)}
CONTENT_X = 64          # the LTR items' content edge: 'foo' / 'bar' ink starts x64/65 on all four pictures.


def h_rows(px):
    """[H] per row 3-4: (letter blob, period blob) in the marker column x30-63 — period = ≤3 px wide and ≤4 px tall."""
    out = []
    for k in (3, 4):
        bl = blobs(px, 30, CONTENT_X, *ROW[k])
        dots = [b for b in bl if b[1] - b[0] <= 2 and b[3] - b[2] <= 3]
        letters = [b for b in bl if b[1] - b[0] >= 4]
        out.append((k, letters, dots))
    return out


def h_check(rows, ref):
    for (k, letters, dots), (_, rl, rd) in zip(rows, ref):
        if len(dots) != 1 or len(letters) != 1:
            return f'row {k} marker blobs: {len(letters)} letter / {len(dots)} period (want 1 / 1)'
        L, D = letters[0], dots[0]
        if D[0] < L[1]:
            return f'row {k} period x{D[0]}-{D[1]} before the letter x{L[0]}-{L[1]} (suffix on the wrong side: first-strong RTL base direction)'
        if rl and rd:
            if not near(L[0], rl[0][0], 2):
                return f'row {k} letter left x{L[0]} vs ref x{rl[0][0]}±2'
            if not near(D[0], rd[0][0], 2):
                return f'row {k} period left x{D[0]} vs ref x{rd[0][0]}±2'
            # U1's risk is VERTICAL (a fallback face resolved for the LRI/PDI controls would grow the marker's line
            # box and move its baseline-aligned ink): the letter's ink rows must stay on the ref's ±1.
            if not (near(L[2], rl[0][2], 1) and near(L[3], rl[0][3], 1)):
                return f'row {k} letter ink y{L[2]}-{L[3]} vs ref y{rl[0][2]}-{rl[0][3]}±1 (marker baseline moved)'
    return None


def c_rows(px):
    """[C] per row 5-6: marker blobs in x20-63, and the item text's first ink at x ≥ 60."""
    out = []
    for k in (5, 6):
        bl = blobs(px, 20, CONTENT_X - 4, *ROW[k])
        item = blobs(px, CONTENT_X - 4, 100, *ROW[k])
        out.append((k, bl, item[0][0] if item else None))
    return out


def c_check(rows, ref):
    for (k, bl, item), (_, rbl, ritem) in zip(rows, ref):
        if len(bl) < 2:
            return f'row {k} marker blobs {len(bl)} (want the ideograph AND the "、" suffix)'
        comma = bl[-1]
        if comma[1] - comma[0] > 6:
            return f'row {k} last marker blob x{comma[0]}-{comma[1]} is not the "、" (too wide)'
        if item is None or not near(item, ritem, 1):
            return f'row {k} item text left x{item} vs ref x{ritem}±1'
        if not near(bl[0][0], rbl[0][0], 1):
            return f'row {k} marker left x{bl[0][0]} vs ref x{rbl[0][0]}±1 (suffix gap: the "、" must end flush at the content edge)'
        if not near(comma[1], rbl[-1][1], 2):
            return f'row {k} marker right x{comma[1]} vs ref x{rbl[-1][1]}±2'
    return None


def digit_ink(px, k, x0, x1):
    """Dark-ink x extent of the one digit glyph of row k inside [x0,x1) (None when blank)."""
    bl = [b for b in blobs(px, x0, x1, *ROW[k]) if b[3] - b[2] >= 6]   # a digit is ≥ 7 px tall; the '.' is not
    return (bl[0][0], bl[-1][1]) if bl else None


def t_check(ink, ref, label):
    if ink is None:
        return f'no digit ink'
    if ink[1] - ink[0] + 1 < 7:
        return f"digit ink x{ink[0]}-{ink[1]} {ink[1] - ink[0] + 1} px wide vs ref {ref[1] - ref[0] + 1} (proportional '1': tabular-nums not applied)"
    if not near(ink[0], ref[0], 2):
        return f'digit left x{ink[0]} vs ref x{ref[0]}±2'
    return None


def i_rows(px):
    """[I] css3-counter-styles-005 (25 px, inside): per item line, the marker's left ink and the item text's left ink."""
    # Two fixed line windows (ref ink y102-114 / y125-145; Android y104-115 / y129-146): the CJK strokes split a band scan.
    out = []
    for y0, y1 in ((95, 121), (121, 152)):
        bl = blobs(px, 200, 330, y0, y1)
        mk = bl[0][0] if bl else None
        item = next((b[0] for b in bl if b[0] >= 255), None)
        out.append((y0, mk, item))
    return out


def i_check(rows, ref):
    for (y, mk, item), (_, rmk, ritem) in zip(rows, ref):
        if mk is None or not near(mk, rmk, 1):
            return f'line y{y} marker left x{mk} vs ref x{rmk}±1'
        if item is None or not near(item, ritem, 1):
            return f'line y{y} item text left x{item} vs ref x{ritem}±1 (inside "、" marker not flush)'
    return None


ZONES = {'web': [(0, 600)], 'android': [(0, 112), (160, 600)], 'ios': [(0, 64), (160, 208), (232, 600)]}


def zones_equal(a, b, zones):
    for y0, y1 in zones:
        ca, cb = a.crop((0, y0, a.size[0], y1)), b.crop((0, y0, b.size[0], y1))
        if list(ca.getdata()) != list(cb.getdata()):
            n = sum(1 for p, q in zip(ca.getdata(), cb.getdata()) if p != q)
            return f'zone y{y0}-{y1 - 1} differs from {base} ({n} px)'
    return None


def emit(key, label, summary, why):
    global ref_failed
    print(f'{key} {label:8} {summary} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
    if label == 'ref' and why is not None:
        ref_failed = True


refs = {}
base_paths = dict(paths(base, 'css-counter-styles', 'counter-suffix')) if base else {}
for label, p in capture_paths(run, 'css-counter-styles', 'counter-suffix'):
    im, px = load(p)
    if im is None:
        for key in ('[H]', '[C]', '[T]', '[L]') + (('[K]',) if base else ()):
            print(f'{key} counter-suffix {label:8} MISSING {p}')
        continue
    h, c = h_rows(px), c_rows(px)
    t, l1 = digit_ink(px, 9, 136, 151), digit_ink(px, 1, 40, 56)
    if label == 'ref':
        refs = {'h': h, 'c': c, 't': t, 'l': l1}
    emit('[H] counter-suffix', label,
         ' '.join(f"row{k}:" + ','.join(f'x{b[0]}-{b[1]}' for b in sorted(L + D)) for k, L, D in h), h_check(h, refs['h']))
    emit('[C] counter-suffix', label,
         ' '.join(f"row{k}:" + ','.join(f'x{b[0]}-{b[1]}' for b in bl) + f'|item x{it}' for k, bl, it in c), c_check(c, refs['c']))
    emit('[T] counter-suffix', label, f'row9 digit {t}', t_check(t, refs['t'], label))
    emit('[L] counter-suffix', label, f'row1 digit {l1}', t_check(l1, refs['l'], label))
    if base and label != 'ref':
        bim, _ = load(base_paths[label])
        why = 'base capture missing' if bim is None else zones_equal(bim, im, ZONES[label])
        zs = ','.join(f'y{a}-{b - 1}' for a, b in ZONES[label])
        emit('[K] counter-suffix', label, f'zones {zs} vs {base}', why)

iref = None
for label, p in capture_paths(run, 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005'):
    im, px = load(p)
    if im is None:
        print(f'[I] cjk-decimal/css3-counter-styles-005 {label:8} MISSING {p}'); continue
    rows = i_rows(px)
    if label == 'ref':
        iref = rows
    emit('[I] cjk-decimal/css3-counter-styles-005', label,
         ' '.join(f'y{y}:marker x{m} item x{i}' for y, m, i in rows), i_check(rows, iref))
sys.exit(1 if ref_failed else 0)
