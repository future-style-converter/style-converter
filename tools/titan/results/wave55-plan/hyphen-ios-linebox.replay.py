#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.replay.py — wave-55 L1 (BACKLOG 0(f)) planning replay.
#
# Why: the unit proposed for 0(f) changes WHERE the iOS lines of hyphenate-character-001/-003/-004 sit and nothing
# else (same glyphs, same breaks, same strings). So the iOS capture's own line bands, re-placed at the predicted rows,
# are a faithful model of the post-unit picture. Every line of these captures sits an INTEGER number of pixels off the
# ref (hyphen-ios-linebox.pitch.out.txt: centroid offsets within ±0.07 of an integer), so a row re-placement is exact.
# Arms (source = the iOS capture named; target = per-group line offset vs the ref, in px):
#   X   source wave54-ab-drop-U3b, target = wave54-final's own measured per-line offsets  (method check: must score
#       ≈ the real wave54-final capture, i.e. re-placing lines reproduces a device picture)
#   Rw  source wave54-final, target [0,0,0,+1] per group, every line of a group the same (the unit's prediction: the
#       run advances Blink's 19 px line box, the 19.2 px U3b brs accumulate +0.2/+0.4/+0.6 — what web paints today)
#   R0  source wave54-final, target 0 everywhere (the ceiling: every line exactly on the ref)
#   Rd  source wave54-final, target [0,+1,+2,+3] per group (the unit WITHOUT U3b: 20 px brs, +1 per group — what web
#       and Android paint in the drop-U3b arm; tells whether the unit alone would keep a later U3b revert safe)
#   R1  source wave54-final, target Rw + 1 everywhere (the RISK arm: the 0.1875 px half-leading pad is NOT rounded away
#       by SwiftUI's layout and CoreText ceils the 15.04 px baseline to 16 — every line one row low)
#   Rm  the MODEL arm, applied to every test INCLUDING -002 (whose emergency-broken groups [5,4,3,4] have no ref twin
#       line by line): shift(g, k) = Δorigin(g) + inrun(k), with the measured iOS model of today —
#       group origin = round(Σ ceil(18.625·N) + br) and line k baseline = ceil(14.8516 + 18.625·k) — against the unit's
#       group origin = round(Σ 19·N + br) and line k baseline = 15 + 19·k (br = 19.2, U3b on). For 001/003/004 Rm must
#       equal Rw (asserted below): the model reproduces the measured offsets before it is trusted on -002.
# Output: hyphen-ios-linebox-replay/<t>-ios-<arm>.png. Scored by hyphen-ios-linebox.replay-score.mjs.
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = f'{HERE}/hyphen-ios-linebox-replay'
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text')
CAP = lambda run, t: f'{ROOT}/tools/titan/runs/{run}/sections/css-text/ios-screenshots/wpt__css-text__hyphens__hyphenate-character-{t}.png'
DIV0 = {'001': 100, '003': 80, '004': 80}


def core(p):
    return max(p[0], p[1], p[2]) < 110


def bands(im, y0):
    px = im.load(); w, h = im.size
    out, cur, blank = [], None, 0
    for y in range(y0, h):
        if any(core(px[x, y]) for x in range(0, min(w, 140))):
            cur = [y, y] if cur is None else [cur[0], y]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(tuple(cur)); cur = None; blank = 0
    if cur is not None:
        out.append(tuple(cur))
    return out


def centroid(im, top, bot):
    px = im.load(); w, _ = im.size
    num = den = 0.0
    for y in range(top - 3, bot + 4):
        for x in range(0, min(w, 140)):
            p = px[x, y]; wgt = 255 - max(p[0], p[1], p[2])
            if wgt > 8:
                num += wgt * y; den += wgt
    return num / den


def grouped(im, y0):
    gs, cur = [], []
    for b in bands(im, y0):
        if cur and b[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(b)
    if cur:
        gs.append(cur)
    return gs


def offsets(im, ref_im, y0):
    """Per line: (band, integer offset vs the ref line's centroid)."""
    g, rg = grouped(im, y0), grouped(ref_im, y0)
    assert [len(x) for x in g] == [len(x) for x in rg], ([len(x) for x in g], [len(x) for x in rg])
    return [[(b, round(centroid(im, *b) - centroid(ref_im, *r))) for b, r in zip(gg, rr)] for gg, rr in zip(g, rg)]


def replace(src, y0, lines, targets):
    """Copy each line's row region to its target row (shift = target − current); rows above y0 copied verbatim."""
    w, h = src.size
    out = Image.new('RGB', (w, h), (255, 255, 255))
    out.paste(src.crop((0, 0, w, y0)), (0, 0))
    flat = [(b, cur, tgt) for gl, gt in zip(lines, targets) for (b, cur), tgt in zip(gl, gt)]
    for i, (b, cur, tgt) in enumerate(flat):
        a = y0 if i == 0 else (flat[i - 1][0][1] + b[0]) // 2 + 1
        z = h if i == len(flat) - 1 else (b[1] + flat[i + 1][0][0]) // 2 + 1
        s = tgt - cur
        region = src.crop((0, a, w, z))
        out.paste(region, (0, a + s))
    return out


os.makedirs(OUT, exist_ok=True)
for t, y0 in DIV0.items():
    ref = Image.open(f'{REFS}/hyphens__hyphenate-character-{t}.png').convert('RGB')
    fin = Image.open(CAP('wave54-final', t)).convert('RGB')
    drop = Image.open(CAP('wave54-ab-drop-U3b', t)).convert('RGB')
    fo, do = offsets(fin, ref, y0), offsets(drop, ref, y0)
    print(f'{t} final offsets {[[o for _, o in g] for g in fo]}  drop offsets {[[o for _, o in g] for g in do]}')
    arms = {
        'X': (drop, do, [[o for _, o in g] for g in fo]),
        'Rw': (fin, fo, [[[0, 0, 0, 1][gi]] * len(g) for gi, g in enumerate(fo)]),
        'R0': (fin, fo, [[0] * len(g) for g in fo]),
        'Rd': (fin, fo, [[[0, 1, 2, 3][gi]] * len(g) for gi, g in enumerate(fo)]),
        'R1': (fin, fo, [[[1, 1, 1, 2][gi]] * len(g) for gi, g in enumerate(fo)]),
    }
    for arm, (src, offs, tgt) in arms.items():
        img = replace(src, y0, offs, tgt)
        img.save(f'{OUT}/{t}-ios-{arm}.png')
        check = offsets(img, ref, y0)
        print(f'   {arm:3s} target {tgt}  re-measured {[[o for _, o in g] for g in check]}')


# ---- the model arm Rm (and its check against Rw), all four tests ----
import math
def model_shifts(counts, br=19.2):
    old_o, new_o, acc_old, acc_new = [], [], 0.0, 0.0
    for gi, n in enumerate(counts):
        old_o.append(round(acc_old)); new_o.append(round(acc_new))
        acc_old += math.ceil(18.625 * n - 1e-9) + br
        acc_new += 19 * n + br
    inrun = lambda k: 19 * k - (math.ceil(14.8516 + 18.625 * k) - 15)
    return [[(new_o[gi] - old_o[gi]) + inrun(k) for k in range(n)] for gi, n in enumerate(counts)]

DIV0M = {'001': 100, '002': 100, '003': 80, '004': 80}
for t, y0 in DIV0M.items():
    fin = Image.open(CAP('wave54-final', t)).convert('RGB')
    gl = grouped(fin, y0)
    counts = [len(g) for g in gl]
    sh = model_shifts(counts)
    lines = [[(b, 0) for b in g] for g in gl]           # current = 0, target = shift: a pure re-placement by `sh`
    img = replace(fin, y0, lines, sh)
    img.save(f'{OUT}/{t}-ios-Rm.png')
    # The risk arm on the model (every line one row lower than Rm) — -002's R1.
    replace(fin, y0, lines, [[v + 1 for v in g] for g in sh]).save(f'{OUT}/{t}-ios-Rm1.png')
    note = ''
    if t != '002':
        rw = Image.open(f'{OUT}/{t}-ios-Rw.png').convert('RGB')
        same = list(img.getdata()) == list(rw.getdata())
        note = f'  Rm == Rw pixel-identical: {same}'
    print(f'{t} Rm  groups {counts}  model shifts {sh}{note}')
