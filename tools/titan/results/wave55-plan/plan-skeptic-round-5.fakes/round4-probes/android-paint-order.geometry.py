#!/usr/bin/env python3
# tools/titan/results/wave55-plan/android-paint-order.geometry.py — GEOMETRY probe for wave-55 lane L6
# (android-paint-order; BACKLOG obligation 0(k)). One line per (key, platform), each ending in the exact verdict
# "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)", read by geometry-gate.py through `lanes.L6-*.geometryProbes`.
#
# Why: the lane's units change WHICH paint wins where a parent's border band and a descendant overlap (CSS 2.1
# Appendix E: a box's borders paint before its descendants — in-flow ones at steps 4/7, positioned ones at step 8 —
# so the descendant wins). Compose paints every border band AFTER drawContent() (androidx foundation 1.11.4
# BorderModifierNode: every outline branch's onDrawWithContent lambda calls drawContent() first; BorderSideApplier's
# slow path likewise), so the parent's band overpaints its children. SSIM barely sees it (416 px on clip-rect,
# +0.0015 in the replay), so these keys gate on the pixels themselves.
#
# Keys (platforms ref / web / ios / android; the ref row is the self-check and must print OK, exit 1 otherwise):
#   [PO] backdrop-filter-clip-rect / -zoom — the 416 overlap pixels (rows 166-167 and 218-219, x163-266: the red
#        .menu2 bottom border and .menu top border crossing the blue .navbar border) must be red (>= 400) with
#        <= 8 blue; guards: the navbar band OUTSIDE the menus (same rows, x120-159 / x270-309) keeps >= 312/320 blue
#        (a "fix" that drops or moves the parent border fails), the backdrop inversion stays 10000 +-100 pink px in
#        x116-315 y168-217, and the red bbox is (163,134,266,371) +-1 with 1552 +-16 red px (menus neither dropped
#        nor overdrawn into the navbar's interior; round 3: pink and red ±2 — added red beside a menu passed ±16).
#        Plan-skeptic round 1 (instrument-teeth M6): the 320 sampled navbar
#        px are not the band — a picture with 531 of the band's 851 blue px erased passed. Added: the navbar's WHOLE blue
#        (+ the overlap rows' red, − the ref's 416 there) ≥ 0.95 × the ref's 866 blue px (U1 replay 851; the fake 320).
#   [FO] clip-path-filter-order — 0 reddish px below y115; green box bbox (16,128,215,327) +-1 with >= 39600 px; no
#        halo: min channel >= 250 in the 45-px strips right of and below the box. This is a CONTROL: a correct
#        filter→clip order and a DROPPED drop-shadow paint the same picture here (the ref shows no shadow anywhere:
#        the shadow lies under the opaque 400x400 box inside the 200x200 clip), so no pixel key on this test can
#        prove the shadow was rendered-then-clipped; the order is pinned in the JVM suite instead (brief §4 U3).
#   [X]  <legacy-route carrier> — "crossings": runs of 1..4 dark px (sum < 150) with the SAME saturated fill on both
#        ends (horizontal or vertical) = a dark parent band painted over a child's fill; must be <= 4, AND the
#        child-fill count (teal / yellow) >= 0.90 x the ref's (a dropped child cannot pass by having no crossings).
#        Round 1 (instrument-teeth M6) added three reads against the BASE capture of the same platform (U2 changes WHICH
#        paint wins, never where a box is): the child-fill bbox equals the base's ±1 (a child moved 3 px or scrambled is
#        WRONG), the child fill is ≥ 0.98 × the base's (a shortened child), and the dark band px are ≥ 0.9 × min(base,
#        ref) (an erased border passed with 0 crossings: the band must still be there; on the flex pair the base band is
#        twice the ref's — its crossings — so the floor is the smaller of the two).
#        Round 2 (instrument-teeth M5): the bbox is the UNION of 22-26 children (8×6 px each), so ONE child moved 3 px
#        inside it passed. Added a subset read against the base, pixel for pixel: `lost` = base child-fill px the capture
#        no longer fills ≤ 4, and `extra` = capture child-fill px outside dilate₁(base fill ∪ base band) ≤ 4 (U2 only
#        REVEALS fill where the band was). The six U2 replays read 0 / 0; one child moved 3 px 18-263 lost; round 1's
#        child +3x 359-1034, scrambled ≈ 900-11 400, shrunk 76-1369 (plan-skeptic-round-2.instrument-teeth.d/
#        fixcheck6.L5L6.out.txt). The bbox, fill, crossings and band reasons stay.
#        Round 4 (plan-skeptic-round-4.verify.md M2; fix-r4.done.md): `extra` read fill outside dilate₁(base fill ∪ base band), so
#        a child fill drawn a second time 1 px off (+24 … +500 px), a child smeared 1 px (+6 … +125) or a 1×4 bar of fill in
#        a gap (+4) read extra 0 on all five testable keys. `extra` now counts fill outside base fill ∪ base band ∪ the band's
#        own 1-px ring where the BASE is not white canvas (the band's antialiased edge, which U2 may also reveal) — never a
#        ring around the base FILL. Measured (fix-r4.measure.out.txt, l6-extra): the six U2 replays read 0; every round-4 fake
#        ≥ 4 except a 1-px dot of fill in the open (extra 1: the 1-px floor, ≤ 1 kept); a ring around the band alone (no AA
#        condition) still passed the child copied 1 px left (104 px) — rejected.
#   [ID] <raster control> — the capture is decoded-pixel IDENTICAL to the same capture in --base (the units must
#        not change a band nothing overlaps: the behind-content painter must rasterise exactly like androidx).
#
# Usage: python3 android-paint-order.geometry.py <run> [--base wave55-open]
#        python3 android-paint-order.geometry.py --fakes      (runs the rules on synthesized wrong pictures)
#        python3 android-paint-order.geometry.py --replays    (the planning replays must print OK on their keys)
import os
import sys
import numpy as np
from PIL import Image, ImageChops, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin')
DIRS = [('ref', None), ('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]


def path(run, section, test, label):
    """Frozen ref or composed capture of <section>/<test>.html."""
    flat = test.replace('/', '__')
    if label == 'ref':
        return f'{REFS}/{section}/{flat}.png'
    d = dict(DIRS)[label]
    return f'{ROOT}/tools/titan/runs/{run}/sections/{section}/{d}/wpt__{section}__{flat}.png'


def arr(p):
    """RGB int16 array, or None when the PNG is missing (printed as MISSING → WRONG)."""
    return np.asarray(Image.open(p).convert('RGB')).astype(np.int16) if os.path.exists(p) else None


red = lambda a: (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60)
blue = lambda a: (a[..., 2] >= 200) & (a[..., 0] <= 60) & (a[..., 1] <= 60)
pink = lambda a: (a[..., 0] >= 240) & (a[..., 1] >= 100) & (a[..., 1] <= 150) & (a[..., 2] >= 240)
green = lambda a: (a[..., 1] >= 100) & (a[..., 0] <= 40) & (a[..., 2] <= 40)
reddish = lambda a: (a[..., 0] > 150) & (a[..., 0] - np.maximum(a[..., 1], a[..., 2]) > 40)
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yellow = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
OVERLAP_ROWS = (166, 167, 218, 219)
REF_BLUE, REF_OVERLAP_RED = 866, 416      # the clip-rect ref: navbar blue px in the whole picture, menus' red in the overlap rows
dark_px = lambda a: a.sum(axis=2) < 150     # the [X] band ink (the crossings scan's own dark class)


def po(a):
    """[PO] measurements + reasons for one picture."""
    O = a[list(OVERLAP_ROWS), 163:267]
    r_o, b_o = int(red(O).sum()), int(blue(O).sum())
    G = np.concatenate([a[list(OVERLAP_ROWS), 120:160], a[list(OVERLAP_ROWS), 270:310]], axis=1)
    g_b = int(blue(G).sum())
    pk = int(pink(a[168:218, 116:316]).sum())
    navbar = int(blue(a).sum()) + r_o - REF_OVERLAP_RED      # the band's blue, counting the overlap px the menus now own
    ys, xs = np.nonzero(red(a))
    bb = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if len(xs) else None
    why = []
    if r_o < 400 or b_o > 8:
        why.append(f'paint order: overlap rows red {r_o} / blue {b_o} of 416 (parent border over positioned children, CSS 2.1 App. E)')
    if g_b < 312:
        why.append(f'navbar band outside the menus blue {g_b}/320 (parent border dropped or moved)')
    if navbar < 0.95 * REF_BLUE:
        why.append(f'navbar band {navbar} blue px vs ref {REF_BLUE} (< 95 %: the parent border partly erased)')
    # round 3 (verify M2 sweep): pink ±2 (was ±100) and red n ±2 (was ±16) — a 2×2 / 3×3 red blob beside a menu passed (1556 /
    # 1561); ref, web and iOS read pink 10000 / red 1552 on every run, the U1 replay 10000 / 1552, android today 10000 / 1136
    # (= 1552 − the 416 overlap px the blue band covers): 0 px apart
    if abs(pk - 10000) > 2:
        why.append(f'backdrop inversion {pk} pink px vs 10000 ±2')
    if bb is None or any(abs(u - v) > 1 for u, v in zip(bb, (163, 134, 266, 371))) or abs(len(xs) - 1552) > 2:
        why.append(f'menus: red bbox {bb} n {len(xs)} vs (163,134,266,371) n 1552 ±2')
    return f'overlap red {r_o} blue {b_o} | navbar band blue {g_b}/320 whole {navbar} | pink {pk} | red n {len(xs)}', why


def fo(a):
    """[FO] measurements + reasons for one picture."""
    h, w, _ = a.shape
    nred = int(reddish(a[115:]).sum())
    ys, xs = np.nonzero(green(a[115:]))
    why = []
    if not len(xs):
        return 'no green box', ['green box missing']
    bb = (int(xs.min()), int(ys.min()) + 115, int(xs.max()), int(ys.max()) + 115)
    x1, y1 = bb[2], bb[3]
    right = a[bb[1]:min(h, y1 + 46), x1 + 1:min(w, x1 + 46)]
    below = a[y1 + 1:min(h, y1 + 46), bb[0]:min(w, x1 + 46)]
    halo = int(min(right.min(), below.min()))
    if nred:
        why.append(f'{nred} reddish px (a shadow outside the clip: filter applied after clip-path)')
    if any(abs(u - v) > 1 for u, v in zip(bb, (16, 128, 215, 327))) or len(xs) < 39600:
        why.append(f'green box {bb} n {len(xs)} vs (16,128,215,327) n 40000')
    if halo < 250:
        why.append(f'halo: min channel {halo} beside the box (blurred / unclipped edge)')
    return f'reddish {nred} | green {bb} n {len(xs)} | halo min {halo}', why


def crossings(a):
    """Dark 1..4-px runs with the same saturated fill on both ends, horizontal + vertical (the scan's rule)."""
    dark = a.sum(axis=2) < 150
    fill = (a.max(axis=2) - a.min(axis=2) > 60) & ~dark
    n = 0
    for A, D, F in ((a, dark, fill), (a.transpose(1, 0, 2), dark.T, fill.T)):
        W = A.shape[1]
        for k in range(1, 5):
            run_dark = np.ones((A.shape[0], W - k - 1), dtype=bool)
            for j in range(k):
                run_dark &= D[:, 1 + j:W - k + j]
            same = (np.abs(A[:, :W - k - 1] - A[:, k + 1:]).max(axis=2) <= 8) & F[:, :W - k - 1] & F[:, k + 1:]
            n += int((run_dark & same).sum()) * k
    return n


def fill_box(a, fill):
    """(x0, y0, x1, y1) of the child fill, or None."""
    ys, xs = np.nonzero(fill(a))
    return (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if len(xs) else None


def dilate1(m):
    """4-neighbour dilation by one pixel (round 2: where U2 may reveal fill — the base band's own pixels and their edge)."""
    o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]
    return o


def x_rule(a, fill, ref_fill, base=None, ref_dark=None):
    """[X] measurements + reasons; `base` is the same platform's capture in --base (round 1), `ref_dark` the ref's band px."""
    c = crossings(a)
    f = int(fill(a).sum())
    why = []
    if c > 4:
        why.append(f'{c} crossing px (a parent border painted over its child, CSS 2.1 App. E)')
    if f < 0.90 * ref_fill:
        why.append(f'child fill {f} px < 0.90 x ref {ref_fill}')
    meas = f'crossings {c} | child fill {f} (ref {ref_fill})'
    if base is not None:
        bb, ab = fill_box(base, fill), fill_box(a, fill)
        bf, d, bd = int(fill(base).sum()), int(dark_px(a).sum()), int(dark_px(base).sum())
        floor = min(bd, ref_dark if ref_dark is not None else bd)
        if bb is None or ab is None or any(abs(u - v) > 1 for u, v in zip(ab, bb)):
            why.append(f'child fill bbox {ab} vs base {bb} ±1 (U2 changes which paint wins, never where a box is)')
        if f < 0.98 * bf:
            why.append(f'child fill {f} px < 0.98 x base {bf}')
        if d < 0.9 * floor:
            why.append(f'band {d} dark px < 0.9 x min(base, ref) {floor} (the parent border dropped)')
        if a.shape != base.shape:
            why.append(f'canvas {a.shape[1]}x{a.shape[0]} vs base {base.shape[1]}x{base.shape[0]}')
            lost = extra = None
        else:
            # round 2 (M5): pixel subset against the base — every child keeps its pixels; new fill only where the band was
            fa, fb = fill(a), fill(base)
            lost = int((fb & ~fa).sum())
            # round 4 (M2): no dilation of the base FILL — only the band's 1-px antialiased ring (base not white) may turn to fill
            band = dark_px(base)
            extra = int((fa & ~(fb | band | (dilate1(band) & (base.min(axis=2) < 250)))).sum())
            if lost > 4:
                why.append(f'lost {lost} base child-fill px (> 4: a child moved / shrunk / erased — U2 never moves a box)')
            if extra > 1:
                # round 3 (verify M2 sweep): ≤ 1 (was 4) — a 2×2 blob of child fill beside a child read extra 4 and passed;
                # the six U2 replays and every base-identical picture read extra 0 (U2 adds fill only where the band was)
                why.append(f'extra {extra} child-fill px outside the base fill + band (> 1: a child drawn where none was)')
        meas += f' base {bf} | bbox {ab} | band {d} (floor {floor}) | lost {lost} extra {extra}'
    return meas, why


PO_CASES = [('filter-effects', 'backdrop-filter-clip-rect'), ('filter-effects', 'backdrop-filter-clip-rect-zoom')]
X_CASES = [
    ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001', yellow),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002', yellow),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001', teal),
    ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001', teal),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001', teal),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001', teal),
]
# Raster controls (android): U1's self-paint bands nothing overlaps; U2's legacy bands (solid fast path, dotted /
# dashed slow path, radius-clip legacy route) nothing overlaps — no child component and no own text.
ID_CASES = [
    ('css-break', 'borders-008'), ('css-masking', 'clip-path/clip-path-borderBox-1e'),
    ('css-masking', 'clip-path/clip-path-contentBox-1e'), ('css-masking', 'clip-path/clip-path-paddingBox-1e'),
    ('filter-effects', 'backdrop-filter-clip-rect-2'),
    ('css-backgrounds', 'background-clip-002'), ('CSS2', 'css21-errata/s-11-1-1b-001'),
    ('filter-effects', 'backdrop-filter-clip-radius-zoom'),
    # U2 controls INSIDE the probe sections: bordered positioned parents whose abspos children do NOT reach the band
    # (crossings 0 on ref / web / ios / android at wave54-final) — the band moves under them and must not change a px
    ('css-grid', 'abspos/grid-abspos-staticpos-align-items-center'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-003'),
]


def line(key, label, meas, why):
    """One verdict line; the key's tokens lead the line so geometry-gate.py's find_line matches it."""
    return f'{key} {label:<8} | {meas} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({"; ".join(why)})')


def main(run, base):
    ref_failed = False
    for sec, test in PO_CASES:
        for label, _ in DIRS:
            if label == 'ref' and test.endswith('-zoom'):
                # the -zoom ref is rendered at `html { zoom: 5 }` while the composed captures are not (the cell is
                # scoreEligible false); its rows are read against the unzoomed geometry, so no ref self-check here
                print(f'[PO] {test} ref      | n/a (ref rendered at zoom 5; captures unzoomed, unscored) → GEOMETRY OK')
                continue
            a = arr(path(run, sec, test, label))
            if a is None:
                print(f'[PO] {test} {label:<8} MISSING → GEOMETRY WRONG (no capture)'); continue
            meas, why = po(a)
            ref_failed |= label == 'ref' and bool(why)
            print(line(f'[PO] {test}', label, meas, why))
    for label, _ in DIRS:
        a = arr(path(run, 'css-masking', 'clip-path/clip-path-filter-order', label))
        if a is None:
            print(f'[FO] clip-path-filter-order {label:<8} MISSING → GEOMETRY WRONG (no capture)'); continue
        meas, why = fo(a)
        ref_failed |= label == 'ref' and bool(why)
        print(line('[FO] clip-path-filter-order', label, meas, why))
    for sec, test, fill in X_CASES:
        r = arr(path(run, sec, test, 'ref'))
        ref_fill, ref_dark = int(fill(r).sum()), int(dark_px(r).sum())
        name = test.split('/')[-1]
        for label, _ in DIRS:
            a = arr(path(run, sec, test, label))
            if a is None:
                print(f'[X] {name} {label:<8} MISSING → GEOMETRY WRONG (no capture)'); continue
            b = r if label == 'ref' else arr(path(base, sec, test, label))
            if b is None:
                print(f'[X] {name} {label:<8} MISSING → GEOMETRY WRONG (no {label} capture in base {base})'); continue
            meas, why = x_rule(a, fill, ref_fill, b, ref_dark)
            ref_failed |= label == 'ref' and bool(why)
            print(line(f'[X] {name}', label, meas, why))
    for sec, test in ID_CASES:
        name = test.split('/')[-1]
        p, q = path(run, sec, test, 'android'), path(base, sec, test, 'android')
        if not (os.path.exists(p) and os.path.exists(q)):
            print(f'[ID] {name} android  MISSING → GEOMETRY WRONG (no capture in {run} or {base})'); continue
        A, B = Image.open(p).convert('RGB'), Image.open(q).convert('RGB')
        bb = 'size' if A.size != B.size else ImageChops.difference(A, B).getbbox()
        print(line(f'[ID] {name}', 'android', f'identical to {base}' if bb is None else f'vs {base}',
                   [] if bb is None else [f'differs from {base} in {bb}']))
    return 1 if ref_failed else 0


def fakes():
    """Synthesized wrong pictures: every rule must print WRONG on its fakes (and the documented blind spot OK)."""
    out = os.path.join(HERE, 'android-paint-order-fakes')
    os.makedirs(out, exist_ok=True)
    rows = []
    ref = Image.open(path('wave54-final', 'filter-effects', 'backdrop-filter-clip-rect', 'ref')).convert('RGB')
    and_ = Image.open(path('wave54-final', 'filter-effects', 'backdrop-filter-clip-rect', 'android')).convert('RGB')
    f = {}
    f['PO-today-android'] = and_
    im = ref.copy(); px = im.load()                       # parent border dropped where the menus cross it
    for y in OVERLAP_ROWS:
        for x in list(range(66, 163)) + list(range(267, 370)):
            if px[x, y][2] >= 200 and px[x, y][0] <= 60:
                px[x, y] = (255, 128, 255) if y in (167, 218) else (0, 128, 0)
    f['PO-parent-border-dropped'] = im
    im = ref.copy(); px = im.load()                       # menus dropped: their red → what lies under them
    for y in range(600):
        for x in range(390):
            if px[x, y][0] >= 200 and px[x, y][1] <= 60 and px[x, y][2] <= 60:
                px[x, y] = (0, 0, 255) if y in OVERLAP_ROWS else (0, 128, 0) if 116 <= x <= 315 and 116 <= y <= 315 else (255, 255, 255)
    f['PO-menus-dropped'] = im
    im = ref.copy(); px = im.load()                       # menus' side borders drawn through the navbar interior
    for y in range(168, 218):
        for x in (163, 164, 265, 266):
            px[x, y] = (255, 0, 0)
    f['PO-menus-through-navbar'] = im
    im = ref.copy(); px = im.load()                       # backdrop inversion lost (green instead of pink)
    for y in range(168, 218):
        for x in range(116, 316):
            if px[x, y][0] >= 240 and px[x, y][2] >= 240:
                px[x, y] = (0, 128, 0)
    f['PO-backdrop-lost'] = im
    for tag, im in f.items():
        im.save(f'{out}/{tag}.png')
        meas, why = po(np.asarray(im).astype(np.int16))
        rows.append(line(f'[PO] fake {tag}', 'fake', meas, why))
    fo_ref = Image.open(path('wave54-final', 'css-masking', 'clip-path/clip-path-filter-order', 'ref')).convert('RGB')
    g = {}
    # filter AFTER clip: the clipped 200x200 square casts drop-shadow(16px 16px 20px red) (sigma = r/2 = 10) under it
    sh = Image.new('L', fo_ref.size, 0)
    sh.paste(255, (16 + 16, 128 + 16, 216 + 16, 328 + 16))
    sh = sh.filter(ImageFilter.GaussianBlur(10))
    im = Image.composite(Image.new('RGB', fo_ref.size, (255, 0, 0)), fo_ref, sh)
    im.paste((0, 128, 0), (16, 128, 216, 328))
    g['FO-filter-after-clip'] = im
    # the box's edge blurred (sigma 2) below the text block: an unclipped / softened edge
    blurred = fo_ref.copy(); box = fo_ref.crop((0, 115, 390, 600)).filter(ImageFilter.GaussianBlur(2)); blurred.paste(box, (0, 115))
    g['FO-blurred-edge'] = blurred
    shifted = Image.new('RGB', fo_ref.size, (255, 255, 255)); shifted.paste(fo_ref.crop((0, 0, 390, 115)), (0, 0))
    shifted.paste((0, 128, 0), (16, 148, 216, 348))
    g['FO-box-20px-low'] = shifted
    g['FO-shadow-dropped-(blind-spot:expect OK)'] = fo_ref
    for tag, im in g.items():
        im.save(f'{out}/{tag.split("(")[0].rstrip("-")}.png')
        meas, why = fo(np.asarray(im).astype(np.int16))
        rows.append(line(f'[FO] fake {tag}', 'fake', meas, why))
    xr = Image.open(path('wave54-final', 'css-grid', 'abspos/grid-abspos-staticpos-align-self-001', 'ref')).convert('RGB')
    rf = int(teal(np.asarray(xr).astype(np.int16)).sum())
    h = {}
    im = xr.copy(); px = im.load()                        # a 1-px black line drawn across every teal child
    a = np.asarray(xr).astype(np.int16)
    ys, xs = np.nonzero(teal(a))
    for y, x in zip(ys, xs):
        if (y % 7) == 3:
            px[int(x), int(y)] = (0, 0, 0)
    h['X-border-across-children'] = im
    im = xr.copy(); px = im.load()                        # children dropped: no crossings, but no fill either
    for y, x in zip(ys, xs):
        px[int(x), int(y)] = (255, 255, 255)
    h['X-children-dropped'] = im
    h['X-today-android'] = Image.open(path('wave54-final', 'css-grid', 'abspos/grid-abspos-staticpos-align-self-001', 'android')).convert('RGB')
    xa = np.asarray(xr).astype(np.int16)                 # the fakes are built on the ref's pixels: the ref is their base
    for tag, im in h.items():
        im.save(f'{out}/{tag}.png')
        meas, why = x_rule(np.asarray(im).astype(np.int16), teal, rf, xa, int(dark_px(xa).sum()))
        rows.append(line(f'[X] fake {tag}', 'fake', meas, why))
    print('\n'.join(rows))
    return 0


def replays():
    """The planning replays (android-paint-order.replay.py) must print OK on the keys their unit gates: a rule
    that rejects the modelled fixed picture would gate the unit out even when it is right."""
    rd = os.path.join(HERE, 'android-paint-order-replay')
    a = arr(f'{rd}/backdrop-filter-clip-rect-android-U1.png')
    meas, why = po(a)
    print(line('[PO] replay backdrop-filter-clip-rect', 'U1', meas, why))
    for sec, test, fill in X_CASES:
        r = arr(path('wave54-final', sec, test, 'ref'))
        a = arr(f'{rd}/{test.replace("/", "__")}-android-U2.png')
        meas, why = x_rule(a, fill, int(fill(r).sum()), arr(path('wave54-final', sec, test, 'android')), int(dark_px(r).sum()))
        print(line(f'[X] replay {test.split("/")[-1]}', 'U2', meas, why))
    return 0


if __name__ == '__main__':
    argv = sys.argv[1:]
    if '--fakes' in argv:
        sys.exit(fakes())
    if '--replays' in argv:
        sys.exit(replays())
    b = argv[argv.index('--base') + 1] if '--base' in argv else 'wave55-open'
    pos = [x for i, x in enumerate(argv) if not x.startswith('--') and (i == 0 or argv[i - 1] != '--base')]
    sys.exit(main(pos[0] if pos else 'wave54-final', b))
