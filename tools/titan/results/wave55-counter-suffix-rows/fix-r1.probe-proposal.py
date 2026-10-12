#!/usr/bin/env python3
# fix-r1.probe-proposal.py — wave-55 L5 FIX lane (fix-r1), skeptic defect 1 (must-fix) + 2 (should-fix): an EXECUTED proposal
# for the plan regeneration the lane requests. It never writes a plan file: it copies the CURRENT shared-tree probe
# (tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py, sha256 printed) into a private temp mirror, applies the
# proposed edits there (each anchored on text that must occur exactly once; the unified diff is written beside this script as
# fix-r1.probe-proposal.diff.txt — a .txt, not a .patch: land-units.sh owns *.patch), and runs BOTH probes on the same pictures.
#
# The defect: Font.monospacedDigit() turns on Inter's whole `tnum` feature for a run (fix-r1.inter-tnum.out.txt), so U3-ios
# repaints the four periods of rows 9-12 (x130-135, period.tf) and row 10's '2' (x136-150, two.tf) besides row 9's '1'; ios's
# base-identity zones (ROW9_OUT, ZONES['ios']) contain those pixels, so [T] ios and [K] ios print WRONG on a CORRECT picture.
# The proposal: (1) ios's zones skip exactly those boxes (the Catalyst delta is 0 px outside them, fix-r1.u3-boxes.measure.out.txt);
# (2) [T] ios reads them instead — per period box one dot blob at the ref's x ±1 / rows ±1, the '2' box one digit blob at the
# ref's x ±1 (both edges) / rows ±1 with fill ≤ 60 % — and folds them into its round-4 twin `ink:` read (summed with the digit
# window, each box at its own best whole-px offset), so the only reason a correct picture can still print is `ink:` (twin-only:
# no same-rasterizer twin draws the tabular glyphs before stage 1), which PLAN §7 stage 1 step 8 / D39 decide by the recorded
# look, after which TWIN_RUN = 'wave55-pre' pins the boxes at INK_TOL.
#
# Pictures (all css-counter-styles/counter-suffix ios; web / android and css3-counter-styles-005 are wave54-final's):
#   base     = wave54-final (the pre-fix picture: [T] ios must stay WRONG on geometry, [K] ios OK — the self-test);
#   correct  = the skeptic's skmodel: the device capture with exactly U3's Catalyst delta grafted (skeptic-model-u3.py);
#   F1..F11  = wrong pictures built from `correct` (one per class: erased / moved / block / doubled / added ink / outside ink);
#   each read with base wave54-final, without and with --twin-run decided (decided = `correct`, standing in for the step-8 pin).
# Usage: /opt/homebrew/bin/python3.12 -B fix-r1.probe-proposal.py   (exit 0 iff every expectation below holds; prints a table)
import difflib
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
PROBE = f'{ROOT}/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py'
SEC = 'tools/titan/runs/wave54-final/sections/css-counter-styles'
TESTS = ('counter-suffix', 'cjk-decimal__css3-counter-styles-005')
PY = '/opt/homebrew/bin/python3.12'

src = open(PROBE).read()
EDITS = [
    # (1) ios-only zones that skip the six U3 run boxes (web / android keep theirs byte-identical)
    ("ROW9_OUT = [(0, 208, 136, 232), (151, 208, None, 232)]     # row 9 outside the digit window x136-150 ([T] and [K] ios, round 2)\n"
     "ZONES = {'web': [(0, 0, None, 600)], 'android': [(0, 0, None, 112), (0, 160, None, 600), (64, 64, 390, 160)],\n"
     "         'ios': [(0, 0, None, 64), (0, 160, None, 208), (0, 232, None, 600), (64, 64, 390, 160), (0, 208, 136, 232), (151, 208, None, 232)]}\n",
     "ROW9_OUT = [(0, 208, 136, 232), (151, 208, None, 232)]     # row 9 outside the digit window x136-150 ([T] web / android, round 2)\n"
     "# L5 fix-r1 (skeptic defect 1): U3-ios's `tnum` repaints the WHOLE run — the periods of rows 9-12 (x130-135, period.tf) and\n"
     "# row 10's '2' (x136-150, two.tf) besides row 9's '1' (Catalyst: 0 px of U3's delta outside these boxes) — so ios's base-\n"
     "# identity zones skip them and [T] ios reads them against the ref's positions and a twin's ink (u3_boxes_check, t_ink).\n"
     "U3_PERIODS = [(130, ROW[k][0], 136, ROW[k][1]) for k in (9, 10, 11, 12)]   # one period per row, ref x134\n"
     "U3_TWO = (136, ROW[10][0], 151, ROW[10][1])                                 # row 10's '2' (ref x138-144)\n"
     "ROW9_OUT_IOS = [(0, 208, 130, 232), (151, 208, None, 232)]                  # row 9 minus the period box + digit window\n"
     "ZONES = {'web': [(0, 0, None, 600)], 'android': [(0, 0, None, 112), (0, 160, None, 600), (64, 64, 390, 160)],\n"
     "         'ios': [(0, 0, None, 64), (0, 160, None, 208), (64, 64, 390, 160)] + ROW9_OUT_IOS +\n"
     "                [(0, 232, 130, 256), (151, 232, None, 256), (0, 256, 130, 304), (136, 256, None, 304), (0, 304, None, 600)]}\n"
     "\n"
     "\n"
     "def u3_boxes(px):\n"
     "    \"\"\"L5 fix-r1: [(name, blobs)] of the five U3 run boxes outside row 9's digit window (blobs(): column-connected dark ink).\"\"\"\n"
     "    out = [(f'row{9 + i} period x130-135', blobs(px, b[0], b[2], b[1], b[3])) for i, b in enumerate(U3_PERIODS)]\n"
     "    return out + [('row10 two x136-150', blobs(px, U3_TWO[0], U3_TWO[2], U3_TWO[1], U3_TWO[3]))]\n"
     "\n"
     "\n"
     "def u3_boxes_check(px, boxes, ref):\n"
     "    \"\"\"None, or why a U3 box is not its glyph at the ref's place: a period = ONE dot (≤ 3 px wide, ≤ 4 tall) at the ref's x ±1 /\n"
     "    rows ±1; the '2' = ONE ≥ 7-px-tall blob at the ref's left AND right ±1 / rows ±1 filling ≤ 60 % of its bbox.\"\"\"\n"
     "    for (name, bl), (_, rbl) in zip(boxes, ref):\n"
     "        if len(bl) != 1 or len(rbl) != 1:\n"
     "            return f'{name}: {len(bl)} ink blob(s) (want 1, the ref has {len(rbl)})'\n"
     "        b, r = bl[0], rbl[0]\n"
     "        two = name.startswith('row10 two')\n"
     "        if not two and (b[1] - b[0] > 2 or b[3] - b[2] > 3):\n"
     "            return f'{name}: blob x{b[0]}-{b[1]} y{b[2]}-{b[3]} is not a period (> 3 px wide or > 4 tall)'\n"
     "        if not (near(b[0], r[0], 1) and near(b[2], r[2], 1) and near(b[3], r[3], 1)) or (two and not near(b[1], r[1], 1)):\n"
     "            return f'{name}: glyph x{b[0]}-{b[1]} y{b[2]}-{b[3]} vs ref x{r[0]}-{r[1]} y{r[2]}-{r[3]} ±1 (moved)'\n"
     "        if two and ink_px(px_h[0], b) > 0.6 * (b[1] - b[0] + 1) * (b[3] - b[2] + 1):\n"
     "            return f'{name}: fills > 60 % of its bbox (a block, not the glyph)'\n"
     "    return None\n"),
    # (2) [T] ios: the carved row-9 zones, then the box geometry
    ("        t_why = 'base capture missing' if bim is None else zones_equal(bim, im, ROW9_OUT)\n",
     "        t_why = 'base capture missing' if bim is None else zones_equal(bim, im, ROW9_OUT_IOS if label == 'ios' else ROW9_OUT)\n"
     "    if t_why is None and label == 'ios':\n"
     "        t_why = u3_boxes_check(px, u3_boxes(px), refs['u3'])     # L5 fix-r1: the five other U3 run boxes, geometry first\n"),
    # (3) the ref's boxes are read once, beside the other ref reads
    ("        refs = {'h': h, 'c': c, 't': t, 'l': l1}\n",
     "        refs = {'h': h, 'c': c, 't': t, 'l': l1, 'u3': u3_boxes(px)}\n"),
    # (4) [T] ios's twin read covers the boxes too (each at its own best whole-px offset, summed with the digit window)
    ("        ti = t_ink(inkarr(im), twins_of(label, False))\n",
     "        ti = t_ink(inkarr(im), twins_of(label, False), U3_PERIODS + [U3_TWO] if label == 'ios' else [])\n"),
    ("def t_ink(cm, twins):\n"
     "    \"\"\"Round 4 [T]: (XOR, twin) of row 9's digit window x136-150 against a twin's window at its best whole-px offset (±2, ±1).\"\"\"\n"
     "    y0, y1 = ROW[9]; W = np.zeros(cm.shape, bool); W[y0:y1, 136:151] = True\n"
     "    best = None\n"
     "    cw = np.where(W, cm, 0).astype(np.int16)\n"
     "    for tname, tm, _ in twins:\n"
     "        n = min(int(np.abs(cw - np.where(W, moved(np.where(W, tm, 0), a, c), 0)).sum()) for a in (-2, -1, 0, 1, 2) for c in (-1, 0, 1))\n"
     "        if best is None or n < best[0]:\n"
     "            best = (n, tname)\n"
     "    return best\n",
     "def t_ink(cm, twins, extra=()):\n"
     "    \"\"\"Round 4 [T]: (XOR, twin) of row 9's digit window x136-150 against a twin's window at its best whole-px offset (±2, ±1);\n"
     "    L5 fix-r1: plus each `extra` box (x0, y0, x1, y1) at its own best offset, summed (round 4: zones are summed, never ±2 each).\"\"\"\n"
     "    y0, y1 = ROW[9]\n"
     "    boxes = [(136, y0, 151, y1)] + list(extra)\n"
     "    best = None\n"
     "    for tname, tm, _ in twins:\n"
     "        n = 0\n"
     "        for bx0, by0, bx1, by1 in boxes:\n"
     "            W = np.zeros(cm.shape, bool); W[by0:by1, bx0:bx1] = True\n"
     "            cw = np.where(W, cm, 0).astype(np.int16)\n"
     "            n += min(int(np.abs(cw - np.where(W, moved(np.where(W, tm, 0), a, c), 0)).sum()) for a in (-2, -1, 0, 1, 2) for c in (-1, 0, 1))\n"
     "        if best is None or n < best[0]:\n"
     "            best = (n, tname)\n"
     "    return best\n"),
    ("        t_why = ('ink: no twin capture' if ti is None else\n"
     "                 f'ink: row 9 digit window x136-150 differs from {ti[1]}\\'s tabular \\'1\\' by {ti[0]} px at its best whole-px offset '\n",
     "        t_why = ('ink: no twin capture' if ti is None else\n"
     "                 f'ink: row 9 digit window x136-150' + (' + the five U3 run boxes' if label == 'ios' else '') +\n"
     "                 f' differ from {ti[1]}\\'s tabular glyphs by {ti[0]} px at their best whole-px offsets '\n"),
]
prop = src
for old, new in EDITS:
    assert prop.count(old) == 1, f'anchor not unique / missing: {old[:80]!r}'
    prop = prop.replace(old, new)
diff = ''.join(difflib.unified_diff(src.splitlines(True), prop.splitlines(True),
                                    'a/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py',
                                    'b/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py'))
open(f'{HERE}/fix-r1.probe-proposal.diff.txt', 'w').write(
    f'# fix-r1.probe-proposal.diff.txt — the L5 fix lane\'s PROPOSED edit of the plan probe (never applied by the lane; the\n'
    f'# orchestrator regenerates). Base: counter-suffix-rows.geometry.py sha256 {hashlib.sha256(src.encode()).hexdigest()}.\n'
    f'# Written and executed by fix-r1.probe-proposal.py (output fix-r1.probe-proposal.out.txt).\n' + diff)

# ── the private mirror (no plan file is written) ───────────────────────────────────────────────────────────────────────────────
M = tempfile.mkdtemp(prefix='fixL5-proposal-')
for d in ('wave55-plan', 'wave53-plan'):
    os.makedirs(f'{M}/tools/titan/results/{d}')
shutil.copy(PROBE, f'{M}/tools/titan/results/wave55-plan/orig.geometry.py')
open(f'{M}/tools/titan/results/wave55-plan/prop.geometry.py', 'w').write(prop)
shutil.copy(f'{ROOT}/tools/titan/results/wave53-plan/geometry_common.py', f'{M}/tools/titan/results/wave53-plan/')
os.makedirs(f'{M}/tools/titan/runs')
os.symlink(f'{ROOT}/tools/wpt', f'{M}/tools/wpt')
os.symlink(f'{ROOT}/{SEC.rsplit("/sections", 1)[0]}', f'{M}/tools/titan/runs/wave54-final')

dev = np.asarray(Image.open(f'{ROOT}/{SEC}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png').convert('RGB')).astype(np.int16)
cw = np.asarray(Image.open(f'{HERE}/skeptic-catalyst-u3-with.png').convert('RGB')).astype(np.int16)
co = np.asarray(Image.open(f'{HERE}/skeptic-catalyst-u3-without.png').convert('RGB')).astype(np.int16)
correct = np.where((cw != co).any(axis=2)[..., None], cw, dev)
WHITE = 255


def erase(a, x0, y0, x1, y1):
    a = a.copy(); a[y0:y1, x0:x1] = WHITE; return a


def shift(a, x0, y0, x1, y1, dx, dy):
    """The box's content moved by (dx, dy): erased in place, then darkest-of pasted at the new place."""
    b = a.copy(); patch = a[y0:y1, x0:x1].copy(); b[y0:y1, x0:x1] = WHITE
    b[y0 + dy:y1 + dy, x0 + dx:x1 + dx] = np.minimum(b[y0 + dy:y1 + dy, x0 + dx:x1 + dx], patch); return b


def copy_add(a, x0, y0, x1, y1, dx, dy):
    """ADDED ink: the box's content painted a second time at (dx, dy), nothing erased (round 3 / 4's additive class)."""
    b = a.copy(); patch = a[y0:y1, x0:x1].copy()
    b[y0 + dy:y1 + dy, x0 + dx:x1 + dx] = np.minimum(b[y0 + dy:y1 + dy, x0 + dx:x1 + dx], patch); return b


def blob(a, x0, y0, w, h):
    b = a.copy(); b[y0:y0 + h, x0:x0 + w] = 0; return b


def grey(a, x, y):
    """One light-grey pixel (min channel < 200, RGB sum ≥ 300): ink LEVEL 1, not `dark` — below every blob read."""
    b = a.copy(); b[y, x] = 170; return b


def block(a, x0, y0, x1, y1):
    b = a.copy(); b[y0:y1, x0:x1] = 0; return b


# name, picture, expectation: 'T-geo' = [T] ios WRONG on a geometry (non-ink) reason even with the decided twin;
# 'T-ink' = [T] ios WRONG with the decided twin, on its `ink:` read; 'K' = [K] ios WRONG; 'ok' = the correct picture;
# 'floor' = the PLAN's NAMED 2-px-floor residual (§0 round 4: ≤ 2 px under a probe's own mask passes every XOR ≤ 2 read by
# construction) — printed, never counted: it shows the proposal does not widen that class, it inherits it
PICS = [
    ('base', dev, 'base'),
    ('correct', correct, 'ok'),
    ('F1 row9 period erased', erase(correct, 130, 208, 136, 232), 'T-geo'),
    ('F2 row10 period moved +2 px', shift(correct, 130, 232, 136, 256, 2, 0), 'T-geo'),
    ('F3 row11 period erased', erase(correct, 130, 256, 136, 280), 'T-geo'),
    ('F4 row12 period moved -2 px', shift(correct, 130, 280, 136, 304, -2, 0), 'T-geo'),
    ('F5 row10 two erased', erase(correct, 136, 232, 151, 256), 'T-geo'),
    ('F6 row10 two moved +2 px', shift(correct, 136, 232, 151, 256, 2, 0), 'T-geo'),
    ('F7 row10 two as a block', block(correct, 138, 238, 146, 250), 'T-geo'),
    ('F8 row10 two copied 1 px down (added)', copy_add(correct, 136, 232, 151, 255, 0, 1), 'T-ink'),
    ('F9 2x2 blob beside row11 period (added)', blob(correct, 131, 266, 2, 2), 'T-geo'),
    ('F10 row11 letter erased (outside the boxes)', erase(correct, 136, 256, 151, 280), 'K'),
    ('F11 row12 period copied 1 px down (added)', copy_add(correct, 130, 280, 136, 303, 0, 1), 'T-ink'),
    ('F12 one light-grey px in row9 period box (added, 1 level)', grey(correct, 131, 220), 'floor'),
]


def make_run(rid, ios):
    out = f'{M}/tools/titan/runs/{rid}/sections/css-counter-styles'
    for d in ('screenshots', 'android-screenshots', 'ios-screenshots'):
        os.makedirs(f'{out}/{d}')
        for t in TESTS:
            shutil.copy(f'{ROOT}/{SEC}/{d}/wpt__css-counter-styles__{t}.png', f'{out}/{d}/')
    Image.fromarray(ios.astype(np.uint8)).save(f'{out}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png')


make_run('decided', correct)


def read(probe, rid, twin):
    cmd = [PY, '-B', f'{M}/tools/titan/results/wave55-plan/{probe}.geometry.py', rid, 'wave54-final'] + (['--twin-run', twin] if twin else [])
    r = subprocess.run(cmd, capture_output=True, text=True)
    lines = {l.split(' → ')[0].split()[0] + ' ' + l.split()[2]: l.split(' → ', 1)[1] for l in r.stdout.splitlines()
             if l.startswith(('[T] counter-suffix', '[K] counter-suffix', '[H] counter-suffix', '[C] counter-suffix', '[I] cjk', '[L] counter-suffix'))}
    return r.returncode, lines, r.stdout


print(f'# fix-r1.probe-proposal.out.txt — the L5 fix lane\'s executed probe proposal (fix-r1.probe-proposal.py)')
print(f'# plan probe read: counter-suffix-rows.geometry.py sha256 {hashlib.sha256(src.encode()).hexdigest()} (orig) vs the proposal '
      f'(fix-r1.probe-proposal.diff.txt); base wave54-final; decided = the correct picture (stands in for TWIN_RUN = wave55-pre)')
bad = 0
for i, (name, pic, want) in enumerate(PICS):
    rid = f'p{i}'
    make_run(rid, pic)
    rows = []
    for probe in ('orig', 'prop'):
        for twin in (None, 'decided'):
            rc, L, raw = read(probe, rid, twin)
            rows.append((probe, twin, rc, L.get('[T] ios', 'MISSING'), L.get('[K] ios', 'MISSING'), L))
    print(f'== {name} (expect: {want})')
    for probe, twin, rc, T, K, _ in rows:
        print(f'  {probe:4} twin={twin or "-":7} rc={rc} [T] ios → {T}')
        print(f'  {"":4} {"":12} {"":4} [K] ios → {K}')
    # the expectations, on the PROPOSAL (the orig rows are printed for the before / after)
    p_none = next(r for r in rows if r[0] == 'prop' and r[1] is None)
    p_dec = next(r for r in rows if r[0] == 'prop' and r[1] == 'decided')
    T0, K0, T1, K1 = p_none[3], p_none[4], p_dec[3], p_dec[4]
    ok = lambda s: s == 'GEOMETRY OK'
    inkonly = lambda s: s.startswith('GEOMETRY WRONG (ink:')
    if want == 'base':
        good = (not ok(T0)) and not inkonly(T0) and ok(K0)
    elif want == 'ok':
        good = inkonly(T0) and ok(K0) and ok(T1) and ok(K1)
    elif want == 'T-geo':
        good = (not ok(T1)) and not inkonly(T1) and (not ok(T0)) and not inkonly(T0)
    elif want == 'T-ink':
        good = (not ok(T1)) and (not ok(T0))
    elif want == 'K':
        good = (not ok(K1)) and (not ok(K0))
    elif want == 'floor':
        good = True
    # every OTHER key of the proposal equals the original's on the same picture (the edit touches [T] / [K] ios only)
    o_dec = next(r for r in rows if r[0] == 'orig' and r[1] == 'decided')
    others = {k: v for k, v in p_dec[5].items() if k not in ('[T] ios', '[K] ios')}
    o_others = {k: v for k, v in o_dec[5].items() if k not in ('[T] ios', '[K] ios')}
    same = others == o_others
    print(f'  → {"HOLDS" if good else "VIOLATED"}; every other key identical to the original probe: {same}')
    bad += (not good) + (not same)
# ── §R the plan's standing L5 replay set (counter-suffix-rows-replay/<tag>/, read through `replay:<tag>`) ─────────────────────
# ok-all's ios picture grafted ONLY Android's digit into row 9's window and kept the base's periods / '2' — the same fiction the
# defect rests on — so under the proposal every ok-all-built replay reads [T] ios `ink:` WRONG (its boxes are iOS's pre-tnum
# glyphs, the twin is Android's). The regeneration must re-graft them: the five boxes from Android's base capture, exactly as
# the plan grafted the '1'. This section proves that path: per tag, the proposal on the RE-GRAFTED replay prints every key's
# verdict identical to the original probe on the committed replay.
RP = f'{ROOT}/tools/titan/results/wave55-plan/counter-suffix-rows-replay'
os.symlink(RP, f'{M}/tools/titan/results/wave55-plan/counter-suffix-rows-replay')
andr = np.asarray(Image.open(f'{ROOT}/{SEC}/android-screenshots/wpt__css-counter-styles__counter-suffix.png').convert('RGB'))
BOXES = [(130, 208, 136, 232), (130, 232, 151, 256), (130, 256, 136, 280), (130, 280, 136, 304)]   # rows 9-12 minus row 9's window


def verdicts(probe, tag):
    r = subprocess.run([PY, '-B', f'{M}/tools/titan/results/wave55-plan/{probe}.geometry.py', f'replay:{tag}', 'wave54-final'],
                       capture_output=True, text=True)
    return [(l.split(' → ')[0].split()[0] + ' ' + l.split()[1] + ' ' + l.split()[2], l.split(' → ', 1)[1].split(' (')[0])
            for l in r.stdout.splitlines() if ' → ' in l]


print('== §R the standing L5 replays: orig on the committed replay vs the proposal on the committed / re-grafted replay')
for tag in sorted(t for t in os.listdir(RP) if os.path.isdir(f'{RP}/{t}') and os.path.exists(f'{RP}/{t}/counter-suffix.ios.png')):
    g = f'{M}/tools/titan/results/wave55-plan/counter-suffix-rows-replay-regraft-{tag}'
    os.makedirs(g)
    for f in os.listdir(f'{RP}/{tag}'):
        shutil.copy(f'{RP}/{tag}/{f}', g)
    ios = np.asarray(Image.open(f'{g}/counter-suffix.ios.png').convert('RGB')).copy()
    for x0, y0, x1, y1 in BOXES:
        ios[y0:y1, x0:x1] = andr[y0:y1, x0:x1]
    Image.fromarray(ios).save(f'{g}/counter-suffix.ios.png')
    # the probe reads replay:<tag> from <its dir>/counter-suffix-rows-replay/<tag>/: committed first, then the re-grafted copy
    o, pc = verdicts('orig', tag), verdicts('prop', tag)
    link = f'{M}/tools/titan/results/wave55-plan/counter-suffix-rows-replay'
    os.remove(link); os.makedirs(link); os.symlink(g, f'{link}/{tag}')
    pg = verdicts('prop', tag)
    shutil.rmtree(link); os.symlink(RP, link)
    flips_committed = [f'{k}: {a} → {b}' for (k, a), (_, b) in zip(o, pc) if a != b]
    same = [(k, a) for k, a in o] == [(k, b) for k, b in pg]
    print(f'  {tag:14} proposal on committed: {len(flips_committed)} verdict flip(s) {flips_committed}; '
          f'proposal on re-grafted == orig on committed: {same}')
    bad += not same
shutil.rmtree(M)
print(f'violations: {bad}')
sys.exit(1 if bad else 0)
