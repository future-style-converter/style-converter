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
#       [T] (with a base run) reads the same two zones (on ios carved since the pre-landing regeneration: U3-ios's `tnum` repaints
#       the whole run, see below — the round-2 sentence "U3-ios changes the digit's glyph, never the row's other ink" was false);
#   [I] per line the first marker blob's stroke count equals the ref's (1 / 2) and its ink rows equal the BASE capture's ±1
#       (Android's marker sits 2-4 px below the ref on base and fixed alike; U2i moves nothing vertically) — line 1's
#       ideograph replaced by line 2's, or the marker 3 px low, is WRONG.
# Plan-skeptic round 3 (verify M2 sweep): [C] (natives) and [I] (natives) also read `ink:` against the base capture — rows
# 5-6's marker zone / 005's item zone translated by one whole-px dx, 005's marker zone unmoved, XOR ≤ 2 (see INK_TOL).
# Plan-skeptic round 4 (plan-skeptic-round-4.verify.md M3 / M4 / §2.3; fix-r4.done.md): strictly-additive ink still passed
# [H] ios (rows 3-4 had no ink read: the zone copied 1 px down +11 px, 1 px right +8, a doubled-glyph sliver +3, whole-picture
# 1-px copies +7 … +33 in its zone) and [T] ios (ink added INSIDE the digit window: copied 1 px down +7, a 2×2 / 3×3 blob
# beside the '1' +4 / +9), and [I] summed 4 px of added ink as 1-2 per zone under a per-zone ±2. Round 4 adds (twins_of()):
#   [H] (natives) `ink:` rows 3-4's whole marker zone x0-63 = the zone's two glyphs (letter, period) as a native ALREADY paints
#       them in --base (iOS: its own — U1-ios reorders the glyphs, it never redraws them — or Android's: the ok-all model grafts
#       Android's rows, the bundled Noto Hebrew both natives share), each moved by its own whole-px offset, XOR ≤ INK_TOL in all;
#   [T] (natives) `ink:` the digit window x136-150 of row 9 = the tabular '1' as Android already paints it in --base (the
#       ok-all model's graft; inside the window U3-ios draws that glyph only), at the best whole-px offset, XOR ≤ INK_TOL;
#   [C] / [I] `ink:` reads the SUM over the key's zones against INK_TOL (round 3 tested each zone alone), [C]'s zone widens to
#       x0-63 and [I] (natives) also reads 005 outside its line windows (rows 0-94, 152-599, x0-199 of 95-151) = the base.
# Every `ink:` read (round 3's re-based too) compares the ink LEVEL — (min channel < 200) + (RGB sum < 300), two nested masks —
# so an antialiased edge darkened to a core pixel counts (see inkmask). Measured (fix-r4.measure.out.txt §3): ok-all reads 0 on
# every new read; every IN-ZONE fake of the round-4 verifier's sweep is rejected (probes-selfcheck.txt §T4). TWIN_RUN (None;
# `--twin-run <run>`) adds that run's same-platform capture as a twin: the stage-1 step-8 edit sets 'wave55-pre' once the
# device pictures are decided (PLAN §7, D39) — a device '1' / face drawn by another rasterizer is never matched by widening
# INK_TOL (never widened to pass a fake of probes-teeth-r4.py).
# Pre-landing regeneration (L5 fix-r1 R1 / R5; the lane's executed proposal, tools/titan/results/wave55-counter-suffix-rows/
# fix-r1.probe-proposal.{py,diff.txt,out.txt}; wave55-plan/regen.done.md): Font.monospacedDigit() turns on Inter's WHOLE `tnum`
# feature for the run (fix-r1.inter-tnum.out.txt: one → one.tf, two → two.tf, period → period.tf), so U3-ios also repaints the
# periods of rows 9-12 (x130-135) and row 10's '2' (x136-150) — Catalyst: 0 px of U3's delta outside these six run boxes
# (fix-r1.u3-boxes.measure.out.txt). ios's base-identity zones (ZONES['ios'], ROW9_OUT_IOS) skip the five boxes outside row 9's
# window and [T] ios reads them instead: each period box ONE dot at the ref's x ±1 / rows ±1, the '2' box ONE ≥ 7-px-tall blob
# at the ref's left and right ±1 / rows ±1 filling ≤ 60 % (u3_boxes_check), and its twin `ink:` read sums the five boxes with
# the digit window, each at its own best whole-px offset (t_ink). The standing replays re-graft the five boxes from Android's
# base capture as round 4 grafted the '1' (counter-suffix-rows-replay/<tag>/counter-suffix.ios.png; regen.replay-regraft.py).
# Every line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". The ref rows must print OK (exit 1 otherwise): the
# rule must describe the picture the test asks for. PIL (geometry_common) + numpy (round 4's twin reads), 8 PNGs (+6 with a
# base run, +2 with TWIN_RUN).
#
# Usage: python3 tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py <run-id> [base-run-id] [--twin-run <run>]
#        python3 … counter-suffix-rows.geometry.py replay:<tag> [base-run-id]   (reads counter-suffix-rows-replay/<tag>/)
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# Read-only probe: never write a __pycache__ beside the shared wave-53 helper (the tree may be under a running gate).
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(HERE, '..', 'wave53-plan'))
from geometry_common import paths, load, dark, bands, near  # noqa: E402  (shared helpers: refs path, PNG decode, bands)
import numpy as np  # noqa: E402  (round 4: the twin glyph reads)

args = sys.argv[1:]
TWIN_RUN_DEFAULT = 'wave55-pre'    # round 4 (D39): the stage-1 step-8 edit — the decided device pictures are an extra twin (PLAN §10 item 5)
TWIN_RUN = args[args.index('--twin-run') + 1] if '--twin-run' in args else TWIN_RUN_DEFAULT
args = [a for i, a in enumerate(args) if a != '--twin-run' and (i == 0 or args[i - 1] != '--twin-run')]
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


def twin_letters(label):
    """Stage 1 (PLAN §10 item 5): {3: profile, 4: profile} of TWIN_RUN's same-platform capture — the decided device glyphs, the
    letter-profile reference of the SAME rasterizer. Why: the round-2 `letter profile` read correlates a device letter's 14-column
    profile with Chromium's ref letter; across fonts it misjudges — the device's own alef reads 0.80 vs the ref's alef and 0.88
    vs the ref's bet (the look: alef then its period, bet then its period, the ref's order; ../wave55-gate/_note.md stage 1) —
    while against the device's own glyphs the margin is 1.00 vs 0.83. None for the ref / web rows or before stage 1."""
    if not TWIN_RUN or label in ('ref', 'web'):
        return None
    tim, tpx = load(dict(paths(TWIN_RUN, 'css-counter-styles', 'counter-suffix'))[label])
    if tim is None:
        return None
    keep = px_w[0]; px_w[0] = tim.size[0]
    out = {}
    for k, letters, _ in h_rows(tpx):
        if len(letters) == 1:
            out[k] = letter_profile(tpx, k, letters[0])
    px_w[0] = keep
    return out if set(out) == {3, 4} else None


DUP_TOL = 0.95   # stage 1 (PLAN §10 item 5): rows 3 and 4 of ONE capture must show different letters (same font: a duplicated
                 # counter value correlates ≈ 1.0; the pictures of record read 0.83 ios / lower elsewhere — never widened)


def h_check(rows, ref, lref=None):
    profs = {}
    for (k, letters, dots), (_, rl, rd) in zip(rows, ref):
        if len(dots) != 1 or len(letters) != 1:
            return f'row {k} marker blobs: {len(letters)} letter / {len(dots)} period (want 1 / 1)'
        L, D = letters[0], dots[0]
        if D[0] < L[1]:
            return f'row {k} period x{D[0]}-{D[1]} before the letter x{L[0]}-{L[1]} (suffix on the wrong side: first-strong RTL base direction)'
        n = ink_px(px_h[0], L)
        if n > 0.6 * (L[1] - L[0] + 1) * (L[3] - L[2] + 1):
            return f'row {k} letter x{L[0]}-{L[1]} fills {n} px of its {(L[1] - L[0] + 1) * (L[3] - L[2] + 1)}-px bbox (> 60 %: a block, not a glyph)'
        prof = letter_profile(px_h[0], k, L); profs[k] = prof
        if not ref_letter.get('done'):
            ref_letter[k] = prof
        elif ref_letter:
            # round 2: WHICH letter — a row whose letter is nearer the OTHER row's ref letter carries the wrong counter value.
            # Stage 1 (PLAN §10 item 5): a native row is ALSO accepted when it is nearer its own row's letter in TWIN_RUN's
            # same-platform capture (twin_letters: the same rasterizer); a swapped pair fails both (stage1-mutations.out.txt).
            c_same, c_other = corr(prof, ref_letter[k]), corr(prof, ref_letter[7 - k])
            t_same, t_other = (corr(prof, lref[k]), corr(prof, lref[7 - k])) if lref else (0.0, 1.0)
            if c_same <= c_other and t_same <= t_other:
                return (f'row {k} letter profile {c_same:.2f} vs its own ref letter, {c_other:.2f} vs row {7 - k}\'s (the counter value of another row)'
                        + (f'; {t_same:.2f} vs its own {TWIN_RUN} letter, {t_other:.2f} vs row {7 - k}\'s' if lref else ''))
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
    if ref_letter.get('done') and 3 in profs and 4 in profs and corr(profs[3], profs[4]) >= DUP_TOL:
        # stage 1 (PLAN §10 item 5): the same-font read the twin reference needs — one counter value on both rows
        return f'rows 3 and 4 letter profiles correlate {corr(profs[3], profs[4]):.2f} ≥ {DUP_TOL} (the same counter value on both rows)'
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


# ── round 3 (plan-skeptic-round-3.verify.md M2 sweep; fix-r3.done.md): ADDED ink ─────────────────────────────────────────────
# A 2×2 / 3×3 blob beside a marker, a doubled glyph or the whole picture painted again 1-2 px off kept every read above on
# [I] (css3-counter-styles-005, both natives) and on [C] android (rows 5-6, outside [K]'s zones). U2o / U2i MOVE ink, they add
# none: rows 5-6's marker zone (x20-63) is the BASE capture's translated by one whole-px dx (ok-all: +4), 005's marker zone
# (x200-253) is the base's unmoved and its item zone (x254-389) the base's translated (ok-all: −4). `ink:` compares ink masks
# (min channel < 200) after the best dx in −8 … +8: XOR ≤ INK_TOL px. Measured: the ok-all natives read 0 on every zone;
# the added-ink fakes ≥ 4 (fix-r3.done.md §M2). A device picture whose glyphs land at another sub-pixel offset would read
# `ink:` with every position read intact — a stage-1 tolerance read (PLAN D33's precedent), never widened to pass a fake.
INK_TOL = 2


def inkmask(px, box):
    """[[int]] the ink LEVEL over box (x0, y0, x1, y1), x1 / y1 exclusive: (min channel < 200) + (RGB sum < 300). Round 4: two
    nested masks — an antialiased pixel darkened to a core pixel (a 1-px copy over the glyph's own edge) changes the level
    even where the min-channel mask alone does not (plan-skeptic-round-4.verify.d fakes4 "any mask")."""
    x0, y0, x1, y1 = box
    return [[(min(px[x, y]) < 200) + (sum(px[x, y]) < 300) for x in range(x0, x1)] for y in range(y0, y1)]


def xor_shift(m, b, dx):
    """Σ |m − (b shifted right by dx, blank-filled)| over m's box (= the XOR of both nested masks, round 4)."""
    w = len(m[0]) if m else 0
    return sum(abs(r[x] - (rb[x - dx] if 0 <= x - dx < w else 0)) for r, rb in zip(m, b) for x in range(w))


def ink_sum(px, bpx, zones, moves):
    """(Σ best XOR, [(name, xor, dx)]) of every zone against the base (moves=True: each at its best whole-px dx in −8 … +8).
    Round 4 (verify §2.3): the key's zones are summed — 4 px of added ink spread 1-2 per zone passed a per-zone ±2."""
    out = []
    for name, box in zones:
        m, b = inkmask(px, box), inkmask(bpx, box)
        best = min((xor_shift(m, b, dx), dx) for dx in (range(-8, 9) if moves else (0,)))
        out.append((name, best[0], best[1]))
    return sum(n for _, n, _ in out), out


def ink_check(px, bpx, zone_sets):
    """None, or why the key's zones (a list of (zones, moves)) differ from the base by more than INK_TOL px IN ALL."""
    tot, det = 0, []
    for zones, moves in zone_sets:
        n, d = ink_sum(px, bpx, zones, moves); tot += n; det += d
    if tot > INK_TOL:
        worst = max(det, key=lambda t: t[1])
        return (f'ink: {len(det)} zone(s) differ from {base}\'s by {tot} px in all (worst {worst[0]} {worst[1]} at dx {worst[2]:+d}) '
                f'(> {INK_TOL}: added / lost ink — the unit moves ink, it adds none)')
    return None


def inkarr(im):
    """Round 4: the ink LEVEL (min channel < 200) + (RGB sum < 300) of a whole picture as an int array [y, x] (see inkmask)."""
    a = np.asarray(im.convert('RGB'), dtype=np.int16)
    return (a.min(axis=2) < 200).astype(np.int8) + (a.sum(axis=2) < 300).astype(np.int8)


def moved(m, dx, dy):
    """Mask m translated by (dx, dy), blank-filled."""
    h, w = m.shape; o = np.zeros_like(m)
    o[max(0, dy):min(h, h + dy), max(0, dx):min(w, w + dx)] = m[max(0, -dy):min(h, h - dy), max(0, -dx):min(w, w - dx)]
    return o


def two_glyphs(px, k):
    """Row k's marker blobs (letter, period) in x30-63, sorted left to right, or None unless exactly one of each."""
    _, letters, dots = h_rows(px)[k - 3]
    return sorted(letters + dots) if len(letters) == 1 and len(dots) == 1 else None


def h_ink(cm, cpx, twins):
    """Round 4 [H]: (Σ XOR over rows 3-4, twin) — each row's zone x0-63 against a twin's two glyphs, each glyph moved by its own
    whole-px offset (dx: blob-aligned ±2, dy ±1). The zone's ink outside the two predicted glyphs counts in full."""
    best = None
    for tname, tm, tpx in twins:
        tot = 0
        for k in (3, 4):
            y0, y1 = ROW[k]; Z = np.zeros(cm.shape, bool); Z[y0:y1, 0:CONTENT_X] = True
            cg, tg = two_glyphs(cpx, k), two_glyphs(tpx, k)
            if cg is None or tg is None:
                tot = None; break
            # each twin glyph = the twin's zone ink on its side of the gap between its two blobs; matched by the glyph kind
            kind = lambda g: 'dot' if g[1] - g[0] <= 2 else 'letter'
            tsplit = (tg[0][1] + tg[1][0]) // 2 + 1
            parts = {}
            for i, g in enumerate(tg):
                side = np.zeros(cm.shape, bool); side[y0:y1, (0 if i == 0 else tsplit):(tsplit if i == 0 else CONTENT_X)] = True
                parts[kind(g)] = (np.where(side, tm, 0), g)
            cgk = {kind(g): g for g in cg}
            (lm, lg), (dm, dg) = parts['letter'], parts['dot']
            ldx0, ddx0 = cgk['letter'][0] - lg[0], cgk['dot'][0] - dg[0]
            cz = np.where(Z, cm, 0).astype(np.int16)
            lcands = [moved(lm, ldx0 + a, c) for a in (-2, -1, 0, 1, 2) for c in (-1, 0, 1)]
            dcands = [moved(dm, ddx0 + a, c) for a in (-2, -1, 0, 1, 2) for c in (-1, 0, 1)]
            tot += min(int(np.abs(cz - np.where(Z, np.maximum(l, d), 0)).sum()) for l in lcands for d in dcands)
        if tot is not None and (best is None or tot < best[0]):
            best = (tot, tname)
    return best


def t_ink(cm, twins, extra=()):
    """Round 4 [T]: (XOR, twin) of row 9's digit window x136-150 against a twin's window at its best whole-px offset (±2, ±1);
    L5 fix-r1: plus each `extra` box (x0, y0, x1, y1) at its own best offset, summed (round 4: zones are summed, never ±2 each)."""
    y0, y1 = ROW[9]
    boxes = [(136, y0, 151, y1)] + list(extra)
    best = None
    for tname, tm, _ in twins:
        n = 0
        for bx0, by0, bx1, by1 in boxes:
            W = np.zeros(cm.shape, bool); W[by0:by1, bx0:bx1] = True
            cw = np.where(W, cm, 0).astype(np.int16)
            n += min(int(np.abs(cw - np.where(W, moved(np.where(W, tm, 0), a, c), 0)).sum()) for a in (-2, -1, 0, 1, 2) for c in (-1, 0, 1))
        if best is None or n < best[0]:
            best = (n, tname)
    return best


def twins_of(label, own):
    """Round 4: [(name, ink mask, px)] of the twins a native row is read against: the base's same-platform capture (with `own`:
    [H] — U1-ios reorders iOS's own glyphs; not for [T] ios, whose base '1' is the proportional glyph U3-ios replaces), Android's
    base capture (the ok-all graft source on iOS; Android's own base on Android) and TWIN_RUN's same-platform capture (stage 1 on)."""
    out, want = [], ([(base, label)] if own or label == 'android' else []) + ([(base, 'android')] if label != 'android' else []) + ([(TWIN_RUN, label)] if TWIN_RUN else [])
    for rid, plat in want:
        nm = f'{rid} {plat}'
        tim, tpx = load(dict(paths(rid, 'css-counter-styles', 'counter-suffix'))[plat])
        if tim is not None:
            out.append((nm, inkarr(tim), tpx))
    return out


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
ROW9_OUT = [(0, 208, 136, 232), (151, 208, None, 232)]     # row 9 outside the digit window x136-150 ([T] web / android, round 2)
# L5 fix-r1 (skeptic defect 1): U3-ios's `tnum` repaints the WHOLE run — the periods of rows 9-12 (x130-135, period.tf) and
# row 10's '2' (x136-150, two.tf) besides row 9's '1' (Catalyst: 0 px of U3's delta outside these boxes) — so ios's base-
# identity zones skip them and [T] ios reads them against the ref's positions and a twin's ink (u3_boxes_check, t_ink).
U3_PERIODS = [(130, ROW[k][0], 136, ROW[k][1]) for k in (9, 10, 11, 12)]   # one period per row, ref x134
U3_TWO = (136, ROW[10][0], 151, ROW[10][1])                                 # row 10's '2' (ref x138-144)
ROW9_OUT_IOS = [(0, 208, 130, 232), (151, 208, None, 232)]                  # row 9 minus the period box + digit window
ZONES = {'web': [(0, 0, None, 600)], 'android': [(0, 0, None, 112), (0, 160, None, 600), (64, 64, 390, 160)],
         'ios': [(0, 0, None, 64), (0, 160, None, 208), (64, 64, 390, 160)] + ROW9_OUT_IOS +
                [(0, 232, 130, 256), (151, 232, None, 256), (0, 256, 130, 304), (137, 256, None, 304), (0, 304, None, 600)]}
# Stage 1 (PLAN §10 item 6): rows 11-12's base-identity zone starts at x137, not x136 — on the device U3-ios's run repaint reaches
# x136 (the periods' right anti-aliasing fringe: (136,272) 150→100, (136,273) 137→84, (136,296) 255→240, (136,297) 255→236,
# wave55-pre vs wave55-open), 1 px right of the Catalyst-measured period boxes x130-135 (fix-r1.u3-boxes.measure.out.txt);
# the column is read by the residual window recorded from the device capture (fix-r6.zones.py --also), not left unread.


def u3_boxes(px):
    """L5 fix-r1: [(name, blobs)] of the five U3 run boxes outside row 9's digit window (blobs(): column-connected dark ink)."""
    out = [(f'row{9 + i} period x130-135', blobs(px, b[0], b[2], b[1], b[3])) for i, b in enumerate(U3_PERIODS)]
    return out + [('row10 two x136-150', blobs(px, U3_TWO[0], U3_TWO[2], U3_TWO[1], U3_TWO[3]))]


def u3_boxes_check(px, boxes, ref):
    """None, or why a U3 box is not its glyph at the ref's place: a period = ONE dot (≤ 3 px wide, ≤ 4 tall) at the ref's x ±1 /
    rows ±1; the '2' = ONE ≥ 7-px-tall blob at the ref's left AND right ±1 / rows ±1 filling ≤ 60 % of its bbox."""
    for (name, bl), (_, rbl) in zip(boxes, ref):
        if len(bl) != 1 or len(rbl) != 1:
            return f'{name}: {len(bl)} ink blob(s) (want 1, the ref has {len(rbl)})'
        b, r = bl[0], rbl[0]
        two = name.startswith('row10 two')
        if not two and (b[1] - b[0] > 2 or b[3] - b[2] > 3):
            return f'{name}: blob x{b[0]}-{b[1]} y{b[2]}-{b[3]} is not a period (> 3 px wide or > 4 tall)'
        if not (near(b[0], r[0], 1) and near(b[2], r[2], 1) and near(b[3], r[3], 1)) or (two and not near(b[1], r[1], 1)):
            return f'{name}: glyph x{b[0]}-{b[1]} y{b[2]}-{b[3]} vs ref x{r[0]}-{r[1]} y{r[2]}-{r[3]} ±1 (moved)'
        if two and ink_px(px_h[0], b) > 0.6 * (b[1] - b[0] + 1) * (b[3] - b[2] + 1):
            return f'{name}: fills > 60 % of its bbox (a block, not the glyph)'
    return None


I_OUT = [(0, 0, None, 95), (0, 152, None, 600), (0, 95, 200, 152)]     # round 4: 005 outside [I]'s line windows (natives = base)


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
        refs = {'h': h, 'c': c, 't': t, 'l': l1, 'u3': u3_boxes(px)}
    h_why = h_check(h, refs['h'], twin_letters(label))
    h_x = ''
    if h_why is None and base and label not in ('ref', 'web'):
        # round 4 (M3): rows 3-4's whole marker zone = a native's own two glyphs, moved (no added ink)
        hi = h_ink(inkarr(im), px, twins_of(label, True))
        h_why = ('ink: rows 3-4 marker zone: no twin with one letter + one period' if hi is None else
                 f'ink: rows 3-4 marker zone x0-63 differs from the glyphs of {hi[1]} (each moved by whole px) by {hi[0]} px '
                 f'(> {INK_TOL}: added / lost ink — U1 reorders the marker\'s glyphs, it draws none)' if hi[0] > INK_TOL else None)
        h_x = f' ink {hi[0]} vs {hi[1]}' if hi else ''
    emit('[H] counter-suffix', label,
         ' '.join(f"row{k}:" + ','.join(f'x{b[0]}-{b[1]}' for b in sorted(L + D)) for k, L, D in h) + h_x, h_why)
    if label == 'ref':
        ref_letter['done'] = True                  # round 2: the ref letters are read; every later row is compared to them
    bim = bpx = None
    if base and label != 'ref':
        bim, bpx = load(base_paths[label])
    c_why = c_check(c, refs['c'])
    if c_why is None and base and label not in ('ref', 'web'):
        # round 3: rows 5-6's marker zone is the base's, translated (no added ink beside the marker)
        # round 4: the zone widened to x0-63 and the two rows summed (verify §2.3)
        c_why = 'base capture missing' if bim is None else ink_check(px, bpx, [([(f'row{k} marker x0-63', (0, ROW[k][0], CONTENT_X, ROW[k][1])) for k in (5, 6)], True)])
    emit('[C] counter-suffix', label,
         ' '.join(f"row{k}:" + ','.join(f'x{b[0]}-{b[1]}' for b in bl) + f'|item x{it}|strokes {st[0]} y{st[1]}' for k, bl, it, st in c), c_why)
    t_why = t_check(t, refs['t'], label)
    if t_why is None and base and label != 'ref':
        # round 2 (M3): the row's other ink — item text erased or moved 3 px kept every digit read
        t_why = 'base capture missing' if bim is None else zones_equal(bim, im, ROW9_OUT_IOS if label == 'ios' else ROW9_OUT)
    if t_why is None and label == 'ios':
        t_why = u3_boxes_check(px, u3_boxes(px), refs['u3'])     # L5 fix-r1: the five other U3 run boxes, geometry first
    t_x = ''
    if t_why is None and base and label not in ('ref', 'web'):
        # round 4 (M4): the digit window holds the tabular '1' a native already paints, and nothing else
        ti = t_ink(inkarr(im), twins_of(label, False), U3_PERIODS + [U3_TWO] if label == 'ios' else [])
        t_why = ('ink: no twin capture' if ti is None else
                 f'ink: row 9 digit window x136-150' + (' + the five U3 run boxes' if label == 'ios' else '') +
                 f' differ from {ti[1]}\'s tabular glyphs by {ti[0]} px at their best whole-px offsets '
                 f'(> {INK_TOL}: added / lost ink in the window)' if ti[0] > INK_TOL else None)
        t_x = f' ink {ti[0]} vs {ti[1]}' if ti else ''
    emit('[T] counter-suffix', label, f'row9 digit {t[:4] if t else None}' + t_x, t_why)
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
    if why is None and base and label not in ('ref', 'web'):
        # round 3: the marker unmoved, the item text translated — per line window, no added ink; round 4: summed over the four
        # zones (verify §2.3), and the rest of the capture (the prose, x0-199, below the list) pixel-identical to the base
        why = (ink_check(px, bpx, [([(f'line y{y0} marker x200-253', (200, y0, 254, y1)) for y0, y1 in ((95, 121), (121, 152))], False),
                                   ([(f'line y{y0} item x254-389', (254, y0, 390, y1)) for y0, y1 in ((95, 121), (121, 152))], True)])
               or zones_equal(bim, im, I_OUT))
    emit('[I] cjk-decimal/css3-counter-styles-005', label,
         ' '.join(f'y{y}:marker x{m}-{mr} ({nm}, fill {f}, strokes {st[0]} y{st[1]}) item x{i}' for y, m, i, nm, mr, f, st in rows), why)
sys.exit(1 if ref_failed else 0)
