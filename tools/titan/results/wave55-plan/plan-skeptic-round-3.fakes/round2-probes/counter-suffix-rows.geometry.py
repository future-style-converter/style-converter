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
#       rows 3-6 and row 9) PLUS, on both natives, the item-text zone x64-389 × y64-159 of rows 3-6 (L5's units move only
#       the MARKERS left of the content edge x64; the ok-all replay differs from wave54-final there by 0 px). Replaces the
#       wave-54 "rows 0-207 identical" crop, which L5's units break by design.
# Plan-skeptic round 1 (instrument-teeth M5) gave every gating key a glyph read — the draft read blob POSITIONS only, so
# an erased "、", a solid block for the ideograph or the letter, a 1-px speck for 一 / 二 and a '1' drawn as a 7-px bar
# all printed OK:
#   [H] each row's letter ink fills ≤ 60 % of its bbox (a glyph, not a block: ref 0.44-0.46);
#   [C] the first marker blob (the ideograph) is the ref's width ±2 (13 px; a speck is 0-1);
#   [T] the digit's ink rows are the ref's ±1 and its ink fills ≤ 60 % of its bbox (ref 0.39; a bar is 1.0);
#   [I] each line holds ≥ 2 marker blobs left of the item (x < 255: the ideograph AND the "、") and the last one ends at
#       the ref's right edge ±2 (x249); the ideograph's ink fills its bbox no more than the ref's fill + 0.25 (二: ref
#       0.19, Android's lighter face 0.11, a solid block 1.0; 一 is a 2-px stroke, 0.98: a block cannot be told from it,
#       so line 2 carries this read).
# Plan-skeptic round 2 (instrument-teeth M3) found 14 / 16 plausible wrong pictures accepted — counter values swapped,
# glyphs 3 px off their line, row 9's item text erased — and added, each measured on the passing pictures and the fakes
# (plan-skeptic-round-2.instrument-teeth.d/fixcheck5.L5.out.txt, fixcheck6.L5L6.out.txt, fixcheck8.L5H.out.txt):
#   [C] per row 5 / 6 the ideograph's STROKE COUNT (row-runs of min-channel < 200 ink in its columns: 一 = 1, 二 = 2) equals
#       the ref's, and its ink rows equal the ref's ±1 (the ok-all ios / android pictures agree; rows 5 / 6 trading their
#       ideographs read (2, 1); the ideograph 3 px low reads rows 126 / 145);
#   [H] each row's letter column profile correlates best with the ref letter of the SAME row (ok natives row 3 [0.949 vs
#       0.795], row 4 [0.532 vs 0.852]; the letters swapped invert it), and the period's ink rows equal the ref's ±1;
#   [K] ios also reads row 9 OUTSIDE the digit window x136-150: zones x0-135 × y208-231 and x151- × y208-231 (the ok-all
#       replay differs from wave54-final in row 9 only at x137-145; row 9's item text erased / moved: 161 / 266 px) — and
#       [T] (with a base run) reads the same two zones: U3-ios changes the digit's glyph, never the row's other ink;
#   [I] per line the first marker blob's stroke count equals the ref's (1 / 2) and its ink rows equal the BASE capture's ±1
#       (Android's marker sits 2-4 px below the ref on base and fixed alike; U2i moves nothing vertically) — line 1's
#       ideograph replaced by line 2's, or the marker 3 px low, is WRONG.
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


def strokes(px, xa, xb, y0, y1):
    """(row-runs of min-channel < 200 ink in columns xa..xb of rows [y0, y1), (first, last ink row)) — 一 is 1 run, 二 is 2
    (round 2: the counter VALUE, not only its blob's extent)."""
    rows = [any(min(px[x, y]) < 200 for x in range(xa, xb + 1)) for y in range(y0, y1)]
    n = sum(1 for i, r in enumerate(rows) if r and (i == 0 or not rows[i - 1]))
    ys = [y0 + i for i, r in enumerate(rows) if r]
    return n, ((ys[0], ys[-1]) if ys else None)


def letter_profile(px, k, L):
    """[H] round 2: the letter's dark-ink column profile over 14 columns from its left edge, in row k's line box."""
    return [sum(1 for y in range(*ROW[k]) if dark(px[x, y])) if x < px_w[0] else 0 for x in range(L[0], L[0] + 14)]


def corr(p, q):
    den = (sum(a * a for a in p) * sum(b * b for b in q)) ** 0.5
    return sum(a * b for a, b in zip(p, q)) / den if den else 0.0


px_w = [0]             # the picture's width (letter_profile's bound)
ref_letter = {}        # [H] round 2: the ref's letter profile per row (3, 4), read on the ref row


def h_rows(px):
    """[H] per row 3-4: (letter blob, period blob) in the marker column x30-63 — period = ≤3 px wide and ≤4 px tall."""
    out = []
    for k in (3, 4):
        bl = blobs(px, 30, CONTENT_X, *ROW[k])
        dots = [b for b in bl if b[1] - b[0] <= 2 and b[3] - b[2] <= 3]
        letters = [b for b in bl if b[1] - b[0] >= 4]
        out.append((k, letters, dots))
    return out


px_h = [None]          # the picture h_check reads (set beside each call: h_rows' blobs carry no pixels)


def ink_px(px, b):
    """Dark px inside blob b's bbox (xa, xb, ya, yb)."""
    return sum(1 for y in range(b[2], b[3] + 1) for x in range(b[0], b[1] + 1) if dark(px[x, y]))


def h_check(rows, ref):
    for (k, letters, dots), (_, rl, rd) in zip(rows, ref):
        if len(dots) != 1 or len(letters) != 1:
            return f'row {k} marker blobs: {len(letters)} letter / {len(dots)} period (want 1 / 1)'
        L, D = letters[0], dots[0]
        if D[0] < L[1]:
            return f'row {k} period x{D[0]}-{D[1]} before the letter x{L[0]}-{L[1]} (suffix on the wrong side: first-strong RTL base direction)'
        n = ink_px(px_h[0], L)
        if n > 0.6 * (L[1] - L[0] + 1) * (L[3] - L[2] + 1):
            return f'row {k} letter x{L[0]}-{L[1]} fills {n} px of its {(L[1] - L[0] + 1) * (L[3] - L[2] + 1)}-px bbox (> 60 %: a block, not a glyph)'
        prof = letter_profile(px_h[0], k, L)
        if not ref_letter.get('done'):
            ref_letter[k] = prof
        elif ref_letter:
            # round 2: WHICH letter — a row whose letter is nearer the OTHER row's ref letter carries the wrong counter value
            c_same, c_other = corr(prof, ref_letter[k]), corr(prof, ref_letter[7 - k])
            if c_same <= c_other:
                return f'row {k} letter profile {c_same:.2f} vs its own ref letter, {c_other:.2f} vs row {7 - k}\'s (the counter value of another row)'
        if rl and rd:
            if not near(L[0], rl[0][0], 2):
                return f'row {k} letter left x{L[0]} vs ref x{rl[0][0]}±2'
            if not (near(D[2], rd[0][2], 1) and near(D[3], rd[0][3], 1)):
                return f'row {k} period ink y{D[2]}-{D[3]} vs ref y{rd[0][2]}-{rd[0][3]}±1 (the suffix moved off the line)'   # round 2
            if not near(D[0], rd[0][0], 2):
                return f'row {k} period left x{D[0]} vs ref x{rd[0][0]}±2'
            # U1's risk is VERTICAL (a fallback face resolved for the LRI/PDI controls would grow the marker's line
            # box and move its baseline-aligned ink): the letter's ink rows must stay on the ref's ±1.
            if not (near(L[2], rl[0][2], 1) and near(L[3], rl[0][3], 1)):
                return f'row {k} letter ink y{L[2]}-{L[3]} vs ref y{rl[0][2]}-{rl[0][3]}±1 (marker baseline moved)'
    return None


def c_rows(px):
    """[C] per row 5-6: marker blobs in x20-63, the item text's first ink at x ≥ 60, and (round 2) the ideograph's strokes."""
    out = []
    for k in (5, 6):
        bl = blobs(px, 20, CONTENT_X - 4, *ROW[k])
        item = blobs(px, CONTENT_X - 4, 100, *ROW[k])
        out.append((k, bl, item[0][0] if item else None, strokes(px, bl[0][0], bl[0][1], *ROW[k]) if bl else (0, None)))
    return out


def c_check(rows, ref):
    for (k, bl, item, st), (_, rbl, ritem, rst) in zip(rows, ref):
        if st[0] != rst[0]:
            return f'row {k} ideograph {st[0]} stroke(s) vs ref {rst[0]} (another counter value: 一 / 二)'          # round 2
        if st[1] is None or not (near(st[1][0], rst[1][0], 1) and near(st[1][1], rst[1][1], 1)):
            return f'row {k} ideograph ink y{st[1]} vs ref y{rst[1]}±1 (the marker moved off the line)'            # round 2
        if len(bl) < 2:
            return f'row {k} marker blobs {len(bl)} (want the ideograph AND the "、" suffix)'
        comma = bl[-1]
        if comma[1] - comma[0] > 6:
            return f'row {k} last marker blob x{comma[0]}-{comma[1]} is not the "、" (too wide)'
        if item is None or not near(item, ritem, 1):
            return f'row {k} item text left x{item} vs ref x{ritem}±1'
        if not near(bl[0][0], rbl[0][0], 1):
            return f'row {k} marker left x{bl[0][0]} vs ref x{rbl[0][0]}±1 (suffix gap: the "、" must end flush at the content edge)'
        if not near(bl[0][1] - bl[0][0], rbl[0][1] - rbl[0][0], 2):
            return f'row {k} ideograph x{bl[0][0]}-{bl[0][1]} width {bl[0][1] - bl[0][0]} vs ref {rbl[0][1] - rbl[0][0]}±2 (the marker glyph itself)'
        if not near(comma[1], rbl[-1][1], 2):
            return f'row {k} marker right x{comma[1]} vs ref x{rbl[-1][1]}±2'
    return None


def digit_ink(px, k, x0, x1):
    """(x0, x1, y0, y1, ink px) of the one digit glyph of row k inside [x0,x1) (None when blank)."""
    bl = [b for b in blobs(px, x0, x1, *ROW[k]) if b[3] - b[2] >= 6]   # a digit is ≥ 7 px tall; the '.' is not
    if not bl:
        return None
    g = (bl[0][0], bl[-1][1], min(b[2] for b in bl), max(b[3] for b in bl))
    return g + (ink_px(px, g),)


def t_check(ink, ref, label):
    if ink is None:
        return f'no digit ink'
    if ink[1] - ink[0] + 1 < 7:
        return f"digit ink x{ink[0]}-{ink[1]} {ink[1] - ink[0] + 1} px wide vs ref {ref[1] - ref[0] + 1} (proportional '1': tabular-nums not applied)"
    if not near(ink[0], ref[0], 2):
        return f'digit left x{ink[0]} vs ref x{ref[0]}±2'
    if not (near(ink[2], ref[2], 1) and near(ink[3], ref[3], 1)):
        return f'digit ink y{ink[2]}-{ink[3]} vs ref y{ref[2]}-{ref[3]}±1 (the glyph moved off the line)'
    area = (ink[1] - ink[0] + 1) * (ink[3] - ink[2] + 1)
    if ink[4] > 0.6 * area:
        return f'digit fills {ink[4]} px of its {area}-px bbox (> 60 %: a bar, not a glyph)'
    return None


def i_rows(px):
    """[I] css3-counter-styles-005 (25 px, inside): per item line, the marker's left ink and the item text's left ink."""
    # Two fixed line windows (ref ink y102-114 / y125-145; Android y104-115 / y129-146): the CJK strokes split a band scan.
    out = []
    for y0, y1 in ((95, 121), (121, 152)):
        bl = blobs(px, 200, 330, y0, y1)
        mk = bl[0][0] if bl else None
        item = next((b[0] for b in bl if b[0] >= 255), None)
        marker = [b for b in bl if b[0] < 255]          # the marker's blobs: the ideograph and its "、" suffix
        fill = ink_px(px, marker[0]) / ((marker[0][1] - marker[0][0] + 1) * (marker[0][3] - marker[0][2] + 1)) if marker else 0.0
        st = strokes(px, marker[0][0], marker[0][1], y0, y1) if marker else (0, None)        # round 2: the ideograph's strokes
        out.append((y0, mk, item, len(marker), marker[-1][1] if marker else None, round(fill, 2), st))
    return out


def i_check(rows, ref, base_rows=None):
    for i, ((y, mk, item, nm, mr, fill, st), (_, rmk, ritem, _, rmr, rfill, rst)) in enumerate(zip(rows, ref)):
        if st[0] != rst[0]:
            return f'line y{y} ideograph {st[0]} stroke(s) vs ref {rst[0]} (another counter value: 一 / 二)'          # round 2
        bst = base_rows[i][6] if base_rows else None
        if bst is not None and (st[1] is None or bst[1] is None or not (near(st[1][0], bst[1][0], 1) and near(st[1][1], bst[1][1], 1))):
            # round 2: vertical — against the BASE capture (Android's marker sits 2-4 px below the ref on base and fixed alike)
            return f'line y{y} ideograph ink y{st[1]} vs {base} y{bst[1]}±1 (the marker moved off its line)'
        if mk is None or not near(mk, rmk, 1):
            return f'line y{y} marker left x{mk} vs ref x{rmk}±1'
        if item is None or not near(item, ritem, 1):
            return f'line y{y} item text left x{item} vs ref x{ritem}±1 (inside "、" marker not flush)'
        if nm < 2 or mr is None or not near(mr, rmr, 2):
            return f'line y{y} marker blobs {nm} ending x{mr} vs ref ≥ 2 ending x{rmr}±2 (the "、" suffix missing or moved)'
        if fill > rfill + 0.25:
            return f'line y{y} ideograph fills {fill:.2f} of its bbox vs ref {rfill:.2f} + 0.25 (a block, not the marker glyph)'
    return None


# Zones as (x0, y0, x1, y1) boxes, x1 / y1 exclusive (x1 None = the capture's width). The natives' item-text box
# x64-389 × y64-159 (rows 3-6 right of the content edge) is round 1's: the markers move, the item text does not.
# Round 2 (M3): ios also reads row 9 outside U3-ios's digit window x136-150 (the zones skipped y208-231 entirely, so an
# erased or moved row-9 item text passed the tally's [K] ios).
ROW9_OUT = [(0, 208, 136, 232), (151, 208, None, 232)]     # row 9 outside the digit window x136-150 ([T] and [K] ios, round 2)
ZONES = {'web': [(0, 0, None, 600)], 'android': [(0, 0, None, 112), (0, 160, None, 600), (64, 64, 390, 160)],
         'ios': [(0, 0, None, 64), (0, 160, None, 208), (0, 232, None, 600), (64, 64, 390, 160), (0, 208, 136, 232), (151, 208, None, 232)]}


def zone_name(z):
    x0, y0, x1, y1 = z
    if x0 == 0 and x1 is None:
        return f'y{y0}-{y1 - 1}'
    return f'x{x0}-{"end" if x1 is None else x1 - 1}×y{y0}-{y1 - 1}'          # x1 None = to the capture's right edge (round 2)


def zones_equal(a, b, zones):
    for z in zones:
        box = (z[0], z[1], z[2] if z[2] is not None else a.size[0], z[3])
        ca, cb = a.crop(box), b.crop(box)
        if ca.tobytes() != cb.tobytes():          # raw bytes of one mode/size crop: equal iff every pixel is equal
            flat = lambda im: getattr(im, 'get_flattened_data', im.getdata)()     # Pillow ≥ 12 renamed getdata
            n = sum(1 for p, q in zip(flat(ca), flat(cb)) if p != q)
            return f'zone {zone_name(z)} differs from {base} ({n} px)'
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
    px_h[0], px_w[0] = px, im.size[0]
    h, c = h_rows(px), c_rows(px)
    t, l1 = digit_ink(px, 9, 136, 151), digit_ink(px, 1, 40, 56)
    if label == 'ref':
        refs = {'h': h, 'c': c, 't': t, 'l': l1}
    emit('[H] counter-suffix', label,
         ' '.join(f"row{k}:" + ','.join(f'x{b[0]}-{b[1]}' for b in sorted(L + D)) for k, L, D in h), h_check(h, refs['h']))
    if label == 'ref':
        ref_letter['done'] = True                  # round 2: the ref letters are read; every later row is compared to them
    emit('[C] counter-suffix', label,
         ' '.join(f"row{k}:" + ','.join(f'x{b[0]}-{b[1]}' for b in bl) + f'|item x{it}|strokes {st[0]} y{st[1]}' for k, bl, it, st in c), c_check(c, refs['c']))
    bim = None
    if base and label != 'ref':
        bim, _ = load(base_paths[label])
    t_why = t_check(t, refs['t'], label)
    if t_why is None and base and label != 'ref':
        # round 2 (M3): the row's other ink — item text erased or moved 3 px kept every digit read
        t_why = 'base capture missing' if bim is None else zones_equal(bim, im, ROW9_OUT)
    emit('[T] counter-suffix', label, f'row9 digit {t[:4] if t else None}', t_why)
    emit('[L] counter-suffix', label, f'row1 digit {l1[:4] if l1 else None}', t_check(l1, refs['l'], label))
    if base and label != 'ref':
        why = 'base capture missing' if bim is None else zones_equal(bim, im, ZONES[label])
        zs = ','.join(zone_name(z) for z in ZONES[label])
        emit('[K] counter-suffix', label, f'zones {zs} vs {base}', why)

iref = None
ibase_paths = dict(paths(base, 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005')) if base else {}
for label, p in capture_paths(run, 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005'):
    im, px = load(p)
    if im is None:
        print(f'[I] cjk-decimal/css3-counter-styles-005 {label:8} MISSING {p}'); continue
    rows = i_rows(px)
    if label == 'ref':
        iref = rows
    brows = None
    if base and label != 'ref':
        bim, bpx = load(ibase_paths[label])
        brows = i_rows(bpx) if bim is not None else None
    why = i_check(rows, iref, brows)
    if base and label != 'ref' and brows is None:
        why = why or f'base capture missing ({base})'
    emit('[I] cjk-decimal/css3-counter-styles-005', label,
         ' '.join(f'y{y}:marker x{m}-{mr} ({nm}, fill {f}, strokes {st[0]} y{st[1]}) item x{i}' for y, m, i, nm, mr, f, st in rows), why)
sys.exit(1 if ref_failed else 0)
