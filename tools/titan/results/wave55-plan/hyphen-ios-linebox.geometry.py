#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.geometry.py — wave-55 L1 (BACKLOG 0(f)) post-gate GEOMETRY probe.
#
# Why: tools/titan/results/wave54-plan/hyphenate-character.geometry.py reads line tops at ±4 px, so it prints GEOMETRY
# OK on BOTH wave54-final (iOS groups 2-4 one to two rows HIGH) and wave54-ab-drop-U3b (iOS lines 4-5 of a group one
# row high): the 0(f) defect is invisible to it. This probe reads each line's ink CENTROID (darkness-weighted row mean
# over x < 140) against the same line of the frozen ref, rounds it to whole rows (every capture's offsets sit within
# ±0.07 of an integer: hyphen-ios-linebox.pitch.out.txt), and judges the LINE BOXES:
#   anchor — the first line of group 1 sits exactly on the ref (offset 0): the box top and its half-leading placement;
#   pitch  — inside a group every line has ONE offset, i.e. the run advances the ref's 19 px line box (Blink's
#            round(14.85) + round(3.77) for 16 px Menlo / DejaVu Sans Mono);
#   drift  — the group offsets are non-decreasing and each is 0 or +1 (the U3b <br> is 19.2 px against the ref's 19 px
#            blank line, so three of them accumulate at most +0.6 px by group 4);
#   lines  — the per-group line counts equal the ref's (gating rows only; -002's emergency-broken groups are compared on
#            glyph-identical lines only — ink width within ±2 px — and are a report row).
# Each row ends in "→ LINEBOX OK" or "→ LINEBOX WRONG (<reasons>)". A second family of rows reads the unit's radius on two
# other faces, where Blink ROUNDS DOWN: the stacked boxes of boundary-shaping-001…008 (three one-line runs of Linux
# Libertine 36 px: iOS box ceil(41.04) = 42 today, Blink 41 — what web and Android stack) and of text-decoration-dotted-
# 001/-002 (three one-line divs the iOS leading split resolves to Inter 92 px: ceil(111.32) = 112 today, Blink 111). The
# prediction is a DISPLACEMENT: box k's ink top moves up by exactly k−1 rows against the frozen wave54-final iOS capture
# (each box above it is one row shorter): "→ STACK OK" / "→ STACK WRONG (displacement … want …)".
#
# Usage: python3 hyphen-ios-linebox.geometry.py <run>             (rows for ref/web/ios/android of 001/002/003/004 + stack)
#        python3 hyphen-ios-linebox.geometry.py --replay <arm>    (ios rows read hyphen-ios-linebox-replay/<t>-ios-<arm>.png;
#                                                                  arm U = the radius replay, stack rows only)
#        python3 hyphen-ios-linebox.geometry.py --fakes           (the rejection set; every fake row must print WRONG)
# Self-check: every ref row must print LINEBOX OK (exit 1 otherwise); under --fakes every fake must print WRONG (exit 1).
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin')
DIRS = [('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]
DIV0 = {'001': 100, '002': 100, '003': 80, '004': 80}   # first row below the <p> preamble


def core(p):
    # Glyph cores (max-channel < 110): the segmentation of the wave-54 probe (anti-aliased descender rows would merge).
    return max(p[0], p[1], p[2]) < 110


def lines(im, y0):
    """[(top, bottom, xmin, xmax, centroid)] of the <div>'s ink lines (x < 140), i-dots merged (gap <= 2 rows)."""
    px = im.load(); w, h = im.size
    out, cur, blank = [], None, 0
    for y in range(y0, h):
        xs = [x for x in range(0, min(w, 140)) if core(px[x, y])]
        if xs:
            cur = [y, y, min(xs), max(xs)] if cur is None else [cur[0], y, min(cur[2], min(xs)), max(cur[3], max(xs))]
            blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    res = []
    for top, bot, x0, x1 in out:
        num = den = 0.0
        for y in range(top - 3, bot + 4):          # the line's anti-aliasing rows ride along
            for x in range(0, min(w, 140)):
                wgt = 255 - max(px[x, y])
                if wgt > 8:
                    num += wgt * y; den += wgt
        res.append((top, bot, x0, x1, num / den))
    return res


def groups(ls):
    """Word groups at a blank band >= 18 px (a blank line is 19-20 px; an in-group line gap <= 14)."""
    gs, cur = [], []
    for ln in ls:
        if cur and ln[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(ln)
    if cur:
        gs.append(cur)
    return gs


def verdict(ref_g, got_g, strict):
    """(summary, reasons): per-group offsets vs the ref (whole rows) and the four rules in the banner."""
    why = {}
    if not got_g:
        return 'no ink', {'lines': 'no line found'}
    if strict and [len(g) for g in got_g] != [len(g) for g in ref_g]:
        why['lines'] = f'groups {[len(g) for g in got_g]} vs ref {[len(g) for g in ref_g]}'
    offs = []
    for rg, cg in zip(ref_g, got_g):
        # Pair line i with line i; on a report row keep only glyph-identical pairs (ink width within ±2 px).
        pairs = [(r, c) for r, c in zip(rg, cg) if strict or abs((r[3] - r[2]) - (c[3] - c[2])) <= 2]
        offs.append([round(c[4] - r[4]) for r, c in pairs])
    firsts = [o[0] if o else None for o in offs]
    if offs and offs[0] and offs[0][0] != 0:
        why['anchor'] = f'group 1 line 1 offset {offs[0][0]} (want 0)'
    spread = [(max(o) - min(o)) if o else 0 for o in offs]
    for gi, o in enumerate(offs):
        if o and max(o) != min(o):
            k = next(i for i, v in enumerate(o) if v != o[0])
            why.setdefault('pitch', f'group {gi + 1} line {k + 1} offset {o[k]} vs its line 1 {o[0]} (run pitch ≠ the ref line box)')
    fs = [f for f in firsts if f is not None]
    if any(f not in (0, 1) for f in fs) or any(b < a for a, b in zip(fs, fs[1:])):
        why['drift'] = f'group-first offsets {firsts} (want non-decreasing, each 0 or +1)'
    return f'group-first {firsts} in-group spread {spread}', why


def row(key, ref_im, cap_im, y0, strict):
    s, why = verdict(groups(lines(ref_im, y0)), groups(lines(cap_im, y0)), strict)
    v = 'LINEBOX OK' if not why else 'LINEBOX WRONG (' + '; '.join(f'{k}: {m}' for k, m in why.items()) + ')'
    print(f'{key} {s} → {v}')
    return not why


def stack_tops(im, y0, gap):
    """Tops of the stacked boxes' ink bands (black cores; blank runs > gap rows separate two boxes)."""
    px = im.load(); w, h = im.size
    rows = [y for y in range(y0, h) if any(core(px[x, y]) for x in range(w))]
    tops, last = [], None
    for y in rows:
        if last is None or y - last > gap:
            tops.append(y)
        last = y
    return tops


STACKS = [('css-text', f'boundary-shaping__boundary-shaping-00{i}', 15, 8) for i in range(1, 9)] + \
         [('css-text-decor', f'text-decoration-dotted-00{i}', 75, 30) for i in (1, 2)]

args = sys.argv[1:]
mode = 'fakes' if args[:1] == ['--fakes'] else 'replay' if args[:1] == ['--replay'] else 'run'
arm = args[1] if mode == 'replay' else None
run = 'wave54-final' if mode != 'run' or not args else args[0]
ref_fail = fake_fail = 0

if mode == 'fakes':
    # The rejection set, built from the REF's own pixels so only geometry differs (plus a white capture).
    def shifted(im, y0, f):
        """Re-place each ref line by f(group, line) rows (whole-row regions split at the gap midpoints)."""
        gl = groups(lines(im, y0)); flat = [(gi, li, ln) for gi, g in enumerate(gl) for li, ln in enumerate(g)]
        out = Image.new('RGB', im.size, (255, 255, 255)); out.paste(im.crop((0, 0, im.size[0], y0)), (0, 0))
        for i, (gi, li, ln) in enumerate(flat):
            a = y0 if i == 0 else (flat[i - 1][2][1] + ln[0]) // 2 + 1
            z = im.size[1] if i == len(flat) - 1 else (ln[1] + flat[i + 1][2][0]) // 2 + 1
            out.paste(im.crop((0, a, im.size[0], z)), (0, a + f(gi, li)))
        return out
    FAKES = {
        'white': lambda im, y0: Image.new('RGB', im.size, (255, 255, 255)),
        'whole+1': lambda im, y0: shifted(im, y0, lambda g, l: 1),                     # every line a row low (R1 shape)
        'u3b-today': lambda im, y0: shifted(im, y0, lambda g, l: [0, -1, -2, -2][g] - (1 if l >= 3 else 0)),
        'drop-u3b': lambda im, y0: shifted(im, y0, lambda g, l: -1 if l >= 3 else 0),  # the arm's in-run pitch only
        'no-u3b': lambda im, y0: shifted(im, y0, lambda g, l: g),                      # 20 px brs: +1 per group
        'g4+2': lambda im, y0: shifted(im, y0, lambda g, l: 2 if g == 3 else 0),
    }
    for t in ('001', '003', '004'):
        ref = Image.open(f'{REFS}/css-text/hyphens__hyphenate-character-{t}.png').convert('RGB')
        if not row(f'hyphen-ios-linebox {t} ref', ref, ref, DIV0[t], True):
            ref_fail += 1
        for name, make in FAKES.items():
            if row(f'hyphen-ios-linebox {t} fake:{name}', ref, make(ref, DIV0[t]), DIV0[t], True):
                fake_fail += 1
    if ref_fail or fake_fail:
        print(f'SELF-CHECK FAILED (ref rows not OK: {ref_fail}; fakes accepted: {fake_fail})'); sys.exit(1)
    print('fakes: every fake rejected, every ref row OK'); sys.exit(0)

for t in ('001', '002', '003', '004'):
    ref = Image.open(f'{REFS}/css-text/hyphens__hyphenate-character-{t}.png').convert('RGB')
    strict = t != '002'
    if not row(f'hyphen-ios-linebox {t} ref', ref, ref, DIV0[t], strict):
        ref_fail += 1
    for label, d in DIRS:
        p = (f'{HERE}/hyphen-ios-linebox-replay/{t}-ios-{arm}.png' if mode == 'replay' and label == 'ios'
             else f'{ROOT}/tools/titan/runs/{run}/sections/css-text/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png')
        if not os.path.exists(p):
            print(f'hyphen-ios-linebox {t} {label} MISSING → LINEBOX WRONG (no capture)'); continue
        row(f'hyphen-ios-linebox {t} {label}', ref, Image.open(p).convert('RGB'), DIV0[t], strict)

if mode == 'run' or arm == 'U':
    for sec, stem, y0, gap in STACKS:
        cap = lambda r: f'{ROOT}/tools/titan/runs/{r}/sections/{sec}/ios-screenshots/wpt__{sec}__{stem}.png'
        # Under test: the run's iOS capture, or (arm U) the radius replay hyphen-ios-linebox.radius-replay.py wrote.
        under = f'{HERE}/hyphen-ios-linebox-replay/radius-{stem}-ios-U.png' if arm == 'U' else cap(run)
        if not os.path.exists(under):
            print(f'hyphen-ios-linebox stack {stem} ios MISSING → STACK WRONG (no capture)'); continue
        it = stack_tops(Image.open(under).convert('RGB'), y0, gap)
        bt = stack_tops(Image.open(cap('wave54-final')).convert('RGB'), y0, gap)   # the frozen pre-unit picture
        disp, want = [a - b for a, b in zip(it, bt)], [-k for k in range(len(bt))]
        v = 'STACK OK' if len(it) == len(bt) and disp == want else f'STACK WRONG (displacement {disp} vs wave54-final, want {want})'
        print(f'hyphen-ios-linebox stack {stem} ios tops {it} → {v}')

if ref_fail:
    print('SELF-CHECK FAILED (a ref row is not LINEBOX OK)'); sys.exit(1)
