#!/usr/bin/env python3
# tools/titan/results/wave55-plan/ua-heading-margin.geometry.py — post-gate GEOMETRY probe for wave-55 lane L4
# (ua-heading-margin, BACKLOG obligation 0(h)). Extends tools/titan/results/wave54-plan/ua-heading-face.geometry.py
# (which stays as it is) with the MARGIN axis that probe could not separate from the face.
#
# Why: the folded <h1>s of css-text-decor/text-decoration-inset-005/-006/-011/-014 are `position: absolute` inside a
# `position: relative` <div>, with no author margin. Chromium's html.css gives them `margin-block: .67em` of their own
# 2em face (HTML §15.3.7; css-values-4 §6.1.1): 21.44 px (32 px face) / 17.42 px (014's 26 px monospace face). CSS 2.1
# §10.6.4 puts the box's top MARGIN edge at the static position, so the margin offsets the ink; §8.3.1 says an abspos
# box's margins never collapse. Neither native gives a NESTED out-of-flow box any UA margin (the in-flow collapse plan
# skips it; wave-52 T6 covers canvas-hoisted ROOTS only), so the heading sits ~21 px high on both.
#
# Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". Dark-ink row bands, rows merged across ≤2 blank
# rows (geometry_common.bands). Three key families:
#   [M] margin (natives of 005/006/014/011) — FACE-AWARE so one rule gates U1 alone AND U1+U2. Plan-skeptic round 1
#       (instrument-teeth M4) generalised the 011 translation rule to the other three: the draft read band 1's TOP only,
#       so a heading 12 px right, a missing line 2 or a line 1 cut to 40 % printed OK.
#       005/006/014, band 1 at the 2em face (height ≥ 30 px; 014 ≥ 20 px) → the heading's bands equal the REF's: tops and
#       bottoms ±1, left edges ±3, line-1 right edge ±12 (web's own face measures Δ8 on 005 / 014 line 1: font advance,
#       not position; a truncation moves it ≥ 190), same band count. 005 / 006 ANDROID are compared on band 1 only:
#       Compose does not wrap the unbounded abspos heading (the plan's arm D, R2 — "fox" stays on line 1 clipped at x390:
#       a wrap defect of another mechanism, read by the [F] report row; this key gates the margin).
#       005/006/014, band 1 at today's 16/13 px face → EVERY band = its wave54-final band shifted by the margin (21 / 17 px)
#       ±1 with the x-extent unchanged ±1, same count (U1 alone moves the block; nothing else may change).
#       011 (its fold bails, so the face never applies): EVERY band = its wave54-final band shifted +21 ±1 px with the
#       x-extent unchanged ±1 px — the margin is a pure translation, nothing else may move.
#       ref/web rows: the face-present rule against the ref (self-check; 011: band-1 top ±3).
#   [F] face (005/006/014) — the wave-54 verdict_inset (2 heading bands, band-1 ≥ 30/20 px, band-2 right edge ±4/±6 px),
#       scanned from y96, with round 1's band rule (plan-skeptic round 1 S4: the draft's ±3 tops let a heading 3 px low
#       pass, and [F] 005 / 006 ios are the keys adjudicate.mjs R6 RETIRES a degenerate gain on): every band's top and
#       bottom ±1 of the ref, its left edge ±3 and line 1's right edge ±12 (a line 1 cut to 40 % is WRONG).
#   [C] controls that must not move: the declared-margin abspos anchors inset-001/-002/-003/-004/-009/-015/-016/-024
#       (bands EXACTLY wave54-final's — the rule defers to an author margin, per edge), block-in-inline-015-print
#       (in-flow leaf <h1>s: the wave-54 verdict_015 vs the ref), CSS2 s-11-1-1b-005 (an abspos <p>: out of this
#       lane's heading scope — bands EXACTLY wave54-final's).
# Plan-skeptic round 2 (instrument-teeth M4): every read above is a list of row bands with x-extents, so a count-preserving
# rearrangement — line 1 mirrored, its first and last thirds swapped, 011's band 1 mirrored — passed all 12 gating keys
# (46 / 46 reads), and R6 would RETIRE a mirrored 005 / 006 ios heading as a gain. Two glyph reads were added, measured on
# the fixed pictures and the fakes (plan-skeptic-round-2.instrument-teeth.d/fixcheck4.L4.out.txt, l4_011.out.txt):
#   `crop:`    today's-face branch and 011 — each band's ink crop (rows t−3 … b+3, ink weight 255 − min channel) equals the
#              BASE capture's same band translated by the margin, normalised correlation ≥ 0.97 (U1 alone: 1.000; the fakes
#              0.387-0.646). The base bands are READ from the base capture (`--base`, default wave54-final = wave55-open
#              byte-identical), not typed: BASE_BANDS / BASE_011 below stay as the wave-54 record they were measured from.
#   `profile:` face-present branch and [F] — line 1's dark-ink column profile correlates with the ref's at its best shift
#              |s| ≤ 12 at ≥ 0.95 (web 0.999; mirrored ≤ 0.771; thirds swapped ≤ 0.844). On the Compose no-wrap rows the
#              profile is read over the ref's line-1 columns only ("fox" stays on line 1 past them).
# Round 2 (instrument-teeth S1): X1_TOL ±12 rests on WEB's face (Δx1 −8 / +2 / −8); no device face has met it (the C arms
# are the ref's own pixels). Every face-present line prints `Δx1`; stage 1 measures it per platform and test from the
# device captures and re-sets X1_TOL = max |Δ| + 2 (PLAN §7 stage-1 L4 decision, recorded in §10). Until then a 10-px clip
# of line 1's last glyph passes (probes-teeth.py names it: a known in-tolerance blind spot, PLAN §9 D33).
# The ref rows must print OK (exit 1 otherwise).
#
# Usage: python3 ua-heading-margin.geometry.py [run-id=wave54-final] [--base <run>] [--arm TAG]
#   --arm TAG reads the NATIVE captures of 005/006/014/011 from ./ua-heading-margin-replay/<test>-<plat>-<TAG>.png
#   (the replay / fake pictures; a missing arm file prints MISSING) — the self-test of the rules.
import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, dark, bands  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
args = sys.argv[1:]
arm = args[args.index('--arm') + 1] if '--arm' in args else None
base_run = args[args.index('--base') + 1] if '--base' in args else 'wave54-final'     # round 2: the crop / base-band source
run = next((a for i, a in enumerate(args) if not a.startswith('--') and (i == 0 or args[i - 1] not in ('--arm', '--base'))), 'wave54-final')
ref_failed = False
CROP_MIN, PROF_MIN, PROF_SPAN = 0.97, 0.95, 12           # round 2 (M4): measured — fixed 1.000 / ≥ 0.999, fakes ≤ 0.646 / ≤ 0.844

# wave54-final native heading bands, scanned from y96 (== wave54-open: the opening gate measured every capture identical).
BASE_BANDS = {'text-decoration-inset-005': {'ios': [(109, 130, 16, 191)], 'android': [(107, 131, 17, 191)]},
              'text-decoration-inset-006': {'ios': [(109, 130, 16, 194)], 'android': [(107, 131, 17, 194)]},
              'text-decoration-inset-014': {'ios': [(111, 121, 17, 133), (128, 137, 16, 84)],
                                            'android': [(111, 122, 17, 132), (127, 138, 16, 84)]}}
# Compose's unbounded abspos heading does not wrap (R2): its band 1 is the whole heading; band 2 is not this key's.
NO_WRAP = {('text-decoration-inset-005', 'android'), ('text-decoration-inset-006', 'android')}
TOP_TOL, X0_TOL, X1_TOL = 1, 3, 12        # tops / bottoms ±1; left edge ±3; line-1 right edge ±12 (font advance)
REF_TOP = {'text-decoration-inset-005': 134, 'text-decoration-inset-006': 134, 'text-decoration-inset-014': 129,
           'text-decoration-inset-011': 144}
# whole-px UA margin: .67 × 32 = 21.44 → 21; .67 × 26 = 17.42 → 17 (Compose places whole px; iOS may AA one row).
MARGIN = {'text-decoration-inset-005': 21, 'text-decoration-inset-006': 21, 'text-decoration-inset-014': 17,
          'text-decoration-inset-011': 21}
FACE_MIN = {'text-decoration-inset-005': 30, 'text-decoration-inset-006': 30, 'text-decoration-inset-014': 20}
# wave54-final native bands of inset-011, scanned from y112 (below its 4-line native explanation).
BASE_011 = {
    'ios': [(137, 151, 57, 184), (169, 186, 85, 183), (208, 208, 170, 185), (265, 282, 39, 184), (297, 314, 147, 184)],
    'android': [(136, 155, 57, 184), (168, 187, 80, 184), (209, 209, 166, 180), (265, 283, 41, 184), (299, 314, 148, 184)],
}
# wave54-final native bands of the controls (scan window in the key).
CONTROL = {
    'text-decoration-inset-001': {'ios': [(117, 146, 16, 323)], 'android': [(116, 145, 16, 323)]},
    'text-decoration-inset-002': {'ios': [(117, 146, 16, 323)], 'android': [(116, 145, 16, 323)]},
    'text-decoration-inset-003': {'ios': [(117, 146, 16, 323)], 'android': [(116, 145, 16, 323)]},
    'text-decoration-inset-004': {'ios': [(137, 166, 16, 246), (177, 200, 18, 169), (204, 206, 16, 171)],
                                  'android': [(136, 165, 16, 246), (176, 203, 16, 171)]},
    'text-decoration-inset-009': {'ios': [(96, 110, 17, 271), (157, 186, 16, 353), (197, 220, 16, 63), (224, 226, 16, 65)],
                                  'android': [(97, 110, 17, 271), (156, 185, 16, 373)]},
    'text-decoration-inset-015': {'ios': [(117, 146, 16, 323)], 'android': [(116, 145, 16, 323)]},
    'text-decoration-inset-016': {'ios': [(117, 146, 16, 323)], 'android': [(116, 145, 16, 323)]},
    'text-decoration-inset-024': {'ios': [(117, 146, 56, 363)], 'android': [(116, 145, 16, 363)]},
}
CONTROL_S11 = {'ios': [(5, 16, 25, 40)], 'android': [(20, 34, 20, 346)]}


def ink_rows(px, w, y0, y1):
    """Ink weights (255 − min channel) of rows [y0, y1) across the picture's width, flattened (round 2: the crop read)."""
    return [255 - min(px[x, y]) for y in range(y0, y1) for x in range(w)]


def cos(p, q):
    """Normalised (cosine) correlation of two equal-length weight vectors."""
    den = (sum(a * a for a in p) * sum(b * b for b in q)) ** 0.5
    return sum(a * b for a, b in zip(p, q)) / den if den else 0.0


def crop_check(px, w, h, bpx, bw, bh, b, base_b, margin):
    """None when every band's ink crop equals the base capture's band translated down by `margin` (correlation ≥ CROP_MIN),
    else why. The margin moves the block; it can neither redraw nor reorder its glyphs (round 2, M4)."""
    if w != bw:
        return f'crop: canvas width {w} vs base {bw}'
    cs = []
    for k, (t0, b0, _, _) in enumerate(base_b):
        ya, yb = t0 - 3, b0 + 4
        if ya + margin < 0 or yb + margin > h or ya < 0 or yb > bh:
            return f'crop: band {k + 1} outside the canvas'
        cs.append(cos(ink_rows(px, w, ya + margin, yb + margin), ink_rows(bpx, bw, ya, yb)))
    CROP_SEEN.append(min(cs) if cs else 0.0)
    for k, c in enumerate(cs):
        if c < CROP_MIN:
            return f'crop: band {k + 1} ink {c:.3f} vs {base_run} + {margin} px (want ≥ {CROP_MIN}: the glyphs changed, not only the block position)'
    return None


CROP_SEEN = []          # the last crop read's minimum correlation (printed on the line)


def col_profile(px, w, top, bot, x_hi=None):
    """Dark-ink px per column over rows [top, bot] (columns > x_hi zeroed: the Compose no-wrap rows)."""
    return [sum(1 for y in range(top, bot + 1) if dark(px[x, y])) if x_hi is None or x <= x_hi else 0 for x in range(w)]


def best_xcorr(p, q, span):
    """(peak correlation, shift) of p against q shifted by −span..+span (zero fill)."""
    best = (-1.0, 0)
    for s_ in range(-span, span + 1):
        qs = [q[x - s_] if 0 <= x - s_ < len(q) else 0 for x in range(len(p))]
        best = max(best, (round(cos(p, qs), 3), s_))
    return best


def profile_check(prof, rprof):
    """None when line 1's column profile matches the ref's at |shift| ≤ PROF_SPAN with correlation ≥ PROF_MIN (round 2)."""
    c, s_ = best_xcorr(prof, rprof, PROF_SPAN)
    return None if c >= PROF_MIN else f'profile: line 1 glyphs {c:.3f}@{s_:+d} vs the ref (want ≥ {PROF_MIN} at |shift| ≤ {PROF_SPAN}: reordered / mirrored glyphs)'


def native_path(test, label, default):
    """The capture to read: the run's, or (with --arm) the replay/fake picture for a native target row."""
    if arm and label in ('ios', 'android') and test in REF_TOP:
        return os.path.join(HERE, 'ua-heading-margin-replay', f'{test}-{label}-{arm}.png')
    return default


def bands_like_ref(b, rb):
    """None when bands b sit on the ref's bands rb (tops / bottoms ±1, left ±3, line-1 right ±12, same count), else why."""
    if len(b) != len(rb):
        return f'{len(b)} heading band(s) vs ref {len(rb)} (bands {b})'
    for k, ((t, bt, x0, x1), (rt, rbt, rx0, rx1)) in enumerate(zip(b, rb)):
        if abs(t - rt) > TOP_TOL or abs(bt - rbt) > TOP_TOL:
            return f'band {k + 1} y{t}–{bt} vs ref y{rt}–{rbt}±{TOP_TOL} (the abspos heading\'s UA margin / face)'
        if abs(x0 - rx0) > X0_TOL:
            return f'band {k + 1} left x{x0} vs ref x{rx0}±{X0_TOL}'
        if k == 0 and abs(x1 - rx1) > X1_TOL:
            return f'line-1 right edge x{x1} vs ref x{rx1}±{X1_TOL} (a cut or overflowing first line)'
    return None


def verdict_margin(test, label, b, rb, prof_why=None, crop=None):
    if not b:
        return 'no heading band'
    t, bt, _, _ = b[0]
    if label in ('ref', 'web') or bt - t + 1 >= FACE_MIN[test]:   # the 2em face is present → the ref's own bands
        if (test, label) in NO_WRAP:      # band 1 only: its top, bottom (the 2em face) and left edge (the position)
            (t1, b1, x0, _), (rt, rbt, rx0, _) = b[0], rb[0]
            if abs(t1 - rt) > TOP_TOL or abs(b1 - rbt) > TOP_TOL or abs(x0 - rx0) > X0_TOL:
                return f'band 1 {b[0]} vs ref {rb[0]} (top / bottom ±{TOP_TOL}, left ±{X0_TOL}; Compose does not wrap: band 1 only)'
            return prof_why               # round 2: the glyphs of line 1 over the ref's line-1 columns
        why = bands_like_ref(b, rb) or prof_why
        return why and f'{why} (2em face present)'
    base = crop[0] if crop else BASE_BANDS[test][label]   # today's face → every band = the BASE band + the UA margin
    if len(b) != len(base):
        return f'{len(b)} bands vs wave54-final {len(base)} (16px face: the margin must only translate the heading)'
    for k, ((t, bt, x0, x1), (t0, b0, x00, x10)) in enumerate(zip(b, base)):
        if abs(t - (t0 + MARGIN[test])) > 1 or abs(bt - (b0 + MARGIN[test])) > 1:
            return f'band {k + 1} y{t}–{bt} vs y{t0 + MARGIN[test]}–{b0 + MARGIN[test]}±1 (16px face: wave54-final + the {MARGIN[test]} px UA margin)'
        if abs(x0 - x00) > 1 or abs(x1 - x10) > 1:
            return f'band {k + 1} x{x0}–{x1} vs x{x00}–{x10}±1 (16px face: only the block position may change)'
    return crop[1]() if crop else None          # round 2: the glyphs inside every band are the base's


def verdict_011(label, b, crop=None):
    if label in ('ref', 'web'):
        return None if b and abs(b[0][0] - REF_TOP['text-decoration-inset-011']) <= 3 else f'heading top {b[:1]} vs ref y144'
    base = crop[0] if crop else BASE_011[label]
    if len(b) != len(base):
        return f'{len(b)} bands vs {len(base)} (the margin must only translate the stacked runs)'
    for k, ((t, bt, x0, x1), (t0, b0, x00, x10)) in enumerate(zip(b, base)):
        if abs(t - (t0 + 21)) > 1 or abs(bt - (b0 + 21)) > 1:
            return f'band {k + 1} y{t}–{bt} vs y{t0 + 21}–{b0 + 21}±1 (wave54-final + the 21 px UA margin)'
        if abs(x0 - x00) > 1 or abs(x1 - x10) > 1:
            return f'band {k + 1} x{x0}–{x1} vs x{x00}–{x10} (only the block position may change)'
    return crop[1]() if crop else None          # round 2: the glyphs inside every band are the base's


def verdict_face(test, b, rb, prof_why=None):
    # wave-54 ua-heading-face.geometry.py verdict_inset, with the band-top check (hunk-for-plan-1) — scanned from y96.
    min_h1, min_h2, tol2 = (30, 18, 4) if test != 'text-decoration-inset-014' else (20, 20, 6)
    if len(b) != 2:
        return f'{len(b)} heading line band(s) vs ref 2 (bands {b})'
    (t1, b1, _, _), (t2, b2, _, x2) = b
    if b1 - t1 + 1 < min_h1:
        return f'line-1 band {b1 - t1 + 1} px tall (< {min_h1}: the 2em face is missing)'
    if b2 - t2 + 1 < min_h2:
        return f'line-2 band {b2 - t2 + 1} px tall (< {min_h2})'
    if abs(x2 - rb[1][3]) > tol2:
        return f'line-2 right edge x{x2} vs ref x{rb[1][3]}'
    return bands_like_ref(b, rb) or prof_why   # round 1: tops / bottoms ±1, left ±3, line-1 right ±12; round 2: line 1's glyphs


def verdict_015(b, rb):
    # wave-54 ua-heading-face.geometry.py verdict_015 verbatim.
    if len(b) != 4:
        return f'{len(b)} bands vs ref 4'
    for (t, bt, _, x1), (rt, rbt, _, rx1) in zip(b, rb):
        if abs((bt - t) - (rbt - rt)) > 2:
            return f'band height {bt - t + 1} vs ref {rbt - rt + 1} (y{t})'
        if abs(x1 - rx1) > 3:
            return f'band right edge x{x1} vs ref x{rx1} (y{t})'
        if abs(t - rt) > 3:
            return f'band top y{t} vs ref y{rt} (line pitch / line-height)'
    return None


def emit(key, label, b, why, extra=''):
    global ref_failed
    if label == 'ref' and why:
        ref_failed = True
    print(f'{key:<40} {label:<8} bands {b[:4]}{extra} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({why})'))


# [M] and [F] — the four target documents.
for test in ('text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014', 'text-decoration-inset-011'):
    y0 = 112 if test.endswith('011') else 96
    rb = rprof = None
    base_paths = dict(paths(base_run, 'css-text-decor', test))
    for label, p in paths(run, 'css-text-decor', test):
        im, px = load(native_path(test, label, p))
        if im is None:
            print(f'[M] {test:<36} {label:<8} MISSING → GEOMETRY WRONG (no capture)')
            continue
        w, h = im.size
        b = bands(px, 0, w, y0 if label in ('ios', 'android') or not test.endswith('011') else 96, min(h, 400), dark, gap=2)
        if label == 'ref':
            rb = b
        crop = None
        if label in ('ios', 'android'):
            # round 2: the base capture's own bands and pixels (today's face: the margin translates them, nothing else)
            bim, bpx = load(base_paths[label])
            if bim is None:
                print(f'[M] {test:<36} {label:<8} MISSING base {base_run} → GEOMETRY WRONG (no base capture)')
                continue
            bb = bands(bpx, 0, bim.size[0], y0, min(bim.size[1], 400), dark, gap=2)
            m_ = MARGIN[test]
            crop = (bb, (lambda px_=px, w_=w, h_=h, bpx_=bpx, bim_=bim, bb_=bb, m__=m_: crop_check(px_, w_, h_, bpx_, bim_.size[0], bim_.size[1], bb_, bb_, m__)))
        if test.endswith('011'):
            CROP_SEEN.clear()
            why = verdict_011(label, b, crop)
            emit(f'[M] {test}', label, b, why, f' crop {CROP_SEEN[-1]:.3f}' if CROP_SEEN else '')
            continue
        # round 2: line 1's glyph signature against the ref's (the face-present branch and [F]); Δx1 printed for stage 1 (S1)
        prof_why, extra = None, ''
        if b and rb:
            x_hi = rb[0][3] if (test, label) in NO_WRAP else None
            prof = col_profile(px, w, b[0][0], b[0][1], x_hi)
            if label == 'ref':
                rprof = prof
            rp = rprof if x_hi is None else [v if x <= x_hi else 0 for x, v in enumerate(rprof)]
            prof_why = profile_check(prof, rp)
            extra = f' Δx1 {b[0][3] - rb[0][3]:+d} profile {best_xcorr(prof, rp, PROF_SPAN)[0]:.3f}'
        CROP_SEEN.clear()
        why_m = verdict_margin(test, label, b, rb, prof_why, crop)
        face = label in ('ref', 'web') or (b and b[0][1] - b[0][0] + 1 >= FACE_MIN[test])
        emit(f'[M] {test}', label, b, why_m, extra if face else (f' crop {CROP_SEEN[-1]:.3f}' if CROP_SEEN else ''))
        emit(f'[F] {test}', label, b, verdict_face(test, b, rb, prof_why), extra)

# [C] controls — run captures only (never an --arm picture).
for test, rows in CONTROL.items():
    for label, p in paths(run, 'css-text-decor', test):
        if label not in rows:
            continue
        im, px = load(p)
        b = bands(px, 0, im.size[0], 96, min(im.size[1], 400), dark, gap=2) if im else []
        emit(f'[C] {test}', label, b, None if b == rows[label] else f'control moved: {b}')
rb = None
for label, p in paths(run, 'css-break', 'block-in-inline-015-print'):
    im, px = load(p)
    b = bands(px, 0, im.size[0], 0, min(im.size[1], 330), dark, gap=2) if im else []
    if label == 'ref':
        rb = b
    if label in ('ref', 'ios', 'android'):
        emit('[C] block-in-inline-015-print', label, b, verdict_015(b, rb))
for label, p in paths(run, 'CSS2', 'css21-errata/s-11-1-1b-005'):
    if label not in CONTROL_S11:
        continue
    im, px = load(p)
    b = bands(px, 0, im.size[0], 0, 140, dark, gap=2) if im else []
    emit('[C] s-11-1-1b-005', label, b, None if b == CONTROL_S11[label] else f'control moved: {b}')
sys.exit(1 if ref_failed else 0)
