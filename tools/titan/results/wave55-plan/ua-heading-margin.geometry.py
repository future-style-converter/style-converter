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
#   [M] margin (natives of 005/006/014/011) — FACE-AWARE so one rule gates U1 alone AND U1+U2:
#       005/006/014: band 1 at the 2em face (height ≥ 30 px; 014 ≥ 20 px) → its top must be the REF's top ±3 px;
#       band 1 at today's 16/13 px face → its top must be the wave54-final top + the margin (21 / 17 px) ±2 px.
#       011 (its fold bails, so the face never applies): EVERY band = its wave54-final band shifted +21 ±1 px with the
#       x-extent unchanged ±1 px — the margin is a pure translation, nothing else may move.
#       ref/web rows: band-1 top = the ref's ±3 (self-check).
#   [F] face (005/006/014) — the wave-54 verdict_inset (2 heading bands, band-1 ≥ 30/20 px, band-2 right edge ±4/±6 px,
#       every band top ±3 px of the ref), scanned from y96.
#   [C] controls that must not move: the declared-margin abspos anchors inset-001/-002/-003/-004/-009/-015/-016/-024
#       (bands EXACTLY wave54-final's — the rule defers to an author margin, per edge), block-in-inline-015-print
#       (in-flow leaf <h1>s: the wave-54 verdict_015 vs the ref), CSS2 s-11-1-1b-005 (an abspos <p>: out of this
#       lane's heading scope — bands EXACTLY wave54-final's).
# The ref rows must print OK (exit 1 otherwise).
#
# Usage: python3 ua-heading-margin.geometry.py [run-id=wave54-final] [--arm TAG]
#   --arm TAG reads the NATIVE captures of 005/006/014/011 from ./ua-heading-margin-replay/<test>-<plat>-<TAG>.png
#   (the replay / fake pictures; a missing arm file prints MISSING) — the self-test of the rules.
import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, dark, bands  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
args = sys.argv[1:]
arm = args[args.index('--arm') + 1] if '--arm' in args else None
run = next((a for i, a in enumerate(args) if not a.startswith('--') and (i == 0 or args[i - 1] != '--arm')), 'wave54-final')
ref_failed = False

# wave54-final first heading-band TOP, scanned from y96 (== wave54-open; the IR is byte-identical since wave53-final).
BASE_TOP = {'text-decoration-inset-005': {'ios': 109, 'android': 107},
            'text-decoration-inset-006': {'ios': 109, 'android': 107},
            'text-decoration-inset-014': {'ios': 111, 'android': 111}}
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


def native_path(test, label, default):
    """The capture to read: the run's, or (with --arm) the replay/fake picture for a native target row."""
    if arm and label in ('ios', 'android') and test in REF_TOP:
        return os.path.join(HERE, 'ua-heading-margin-replay', f'{test}-{label}-{arm}.png')
    return default


def verdict_margin(test, label, b):
    if not b:
        return 'no heading band'
    t, bt, _, _ = b[0]
    if label in ('ref', 'web'):
        return None if abs(t - REF_TOP[test]) <= 3 else f'heading top y{t} vs ref y{REF_TOP[test]}'
    if bt - t + 1 >= FACE_MIN[test]:  # the 2em face is present → the ref's own position
        if abs(t - REF_TOP[test]) > 3:
            return f'heading top y{t} vs ref y{REF_TOP[test]} (2em face present: the UA margin is missing or wrong)'
        return None
    want = BASE_TOP[test][label] + MARGIN[test]  # today's face → today's top + the UA margin
    if abs(t - want) > 2:
        return f'heading top y{t} vs y{want}±2 (16px face: wave54-final y{BASE_TOP[test][label]} + the {MARGIN[test]} px UA margin)'
    return None


def verdict_011(label, b):
    if label in ('ref', 'web'):
        return None if b and abs(b[0][0] - REF_TOP['text-decoration-inset-011']) <= 3 else f'heading top {b[:1]} vs ref y144'
    base = BASE_011[label]
    if len(b) != len(base):
        return f'{len(b)} bands vs {len(base)} (the margin must only translate the stacked runs)'
    for k, ((t, bt, x0, x1), (t0, b0, x00, x10)) in enumerate(zip(b, base)):
        if abs(t - (t0 + 21)) > 1 or abs(bt - (b0 + 21)) > 1:
            return f'band {k + 1} y{t}–{bt} vs y{t0 + 21}–{b0 + 21}±1 (wave54-final + the 21 px UA margin)'
        if abs(x0 - x00) > 1 or abs(x1 - x10) > 1:
            return f'band {k + 1} x{x0}–{x1} vs x{x00}–{x10} (only the block position may change)'
    return None


def verdict_face(test, b, rb):
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
    for (t, _, _, _), (rt, _, _, _) in zip(b, rb):
        if abs(t - rt) > 3:
            return f'band top y{t} vs ref y{rt} (the abspos heading\'s UA margin)'
    return None


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


def emit(key, label, b, why):
    global ref_failed
    if label == 'ref' and why:
        ref_failed = True
    print(f'{key:<40} {label:<8} bands {b[:4]} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({why})'))


# [M] and [F] — the four target documents.
for test in ('text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014', 'text-decoration-inset-011'):
    y0 = 112 if test.endswith('011') else 96
    rb = None
    for label, p in paths(run, 'css-text-decor', test):
        im, px = load(native_path(test, label, p))
        if im is None:
            print(f'[M] {test:<36} {label:<8} MISSING → GEOMETRY WRONG (no capture)')
            continue
        w, h = im.size
        b = bands(px, 0, w, y0 if label in ('ios', 'android') or not test.endswith('011') else 96, min(h, 400), dark, gap=2)
        if label == 'ref':
            rb = b
        if test.endswith('011'):
            emit(f'[M] {test}', label, b, verdict_011(label, b))
        else:
            emit(f'[M] {test}', label, b, verdict_margin(test, label, b))
            emit(f'[F] {test}', label, b, verdict_face(test, b, rb))

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
