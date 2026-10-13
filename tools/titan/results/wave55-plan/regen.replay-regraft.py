#!/usr/bin/env python3
# tools/titan/results/wave55-plan/regen.replay-regraft.py — the pre-landing regeneration's L5 R2 (wave55-counter-suffix-rows/
# _note.md "fix-r1" R2; regen.done.md): RE-GRAFTS U3-ios's five other tabular run boxes into the ios picture of each standing L5
# replay (counter-suffix-rows-replay/<tag>/counter-suffix.ios.png), exactly as the round-4 model grafted Android's '1'.
#
# Why: Font.monospacedDigit() turns on Inter's whole `tnum` feature for the run, so U3-ios repaints the periods of rows 9-12
# (x130-135, period.tf) and row 10's '2' (x136-150, two.tf) besides row 9's '1' (fix-r1.inter-tnum.out.txt; Catalyst: 0 px of
# U3's delta outside those boxes, fix-r1.u3-boxes.measure.out.txt). The committed replays grafted only the '1', so under the
# regenerated probe (R1: [T] ios reads the five boxes against a twin's tabular glyphs) every replay would print [T] ios
# `ink:` WRONG on the boxes' pre-tnum iOS glyphs. The boxes are taken from Android's wave54-final capture — the twin the
# probe reads, whose run is already tabular — with the lane's BOXES verbatim (fix-r1.probe-proposal.py §R), which the lane
# proved makes the regenerated probe print, on every re-grafted tag, exactly the installed probe's verdicts on the committed
# tag (fix-r1.probe-proposal.out.txt §R; re-proven by regen.l5-proof.py).
# Guarded: each tag's picture must have the pinned PRE sha256 (the committed bytes) — a second run is refused, never a double
# graft; written atomically (temp file + os.replace). Only the ten tags the probe reads as replay:<tag> are touched (arms/ are
# the brief's score replays, never read by the probe).
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/regen.replay-regraft.py [--check]
#   --check: write nothing; print what a run would change (or that it already ran: every tag at its POST sha256).
import hashlib, os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RP = os.path.join(HERE, 'counter-suffix-rows-replay')
ANDROID = os.path.join(ROOT, 'tools/titan/runs/wave54-final/sections/css-counter-styles/android-screenshots/wpt__css-counter-styles__counter-suffix.png')
BOXES = [(130, 208, 136, 232), (130, 232, 151, 256), (130, 256, 136, 280), (130, 280, 136, 304)]   # x0, y0, x1, y1: rows 9-12 minus row 9's window
PRE = {   # the committed bytes (HEAD 7566fb03 = 611c9f93's): a run is refused unless every tag still has them
    'ok-all': '3ff66c0867dfca42a7815b39fa17444b7dcf836396d9bf706b1a96003d892a38', 'C-nocomma': '3ff66c0867dfca42a7815b39fa17444b7dcf836396d9bf706b1a96003d892a38',
    'I-wholeline': '3ff66c0867dfca42a7815b39fa17444b7dcf836396d9bf706b1a96003d892a38', 'C-shift8': '9f81eeeffd354334f3707efbe7d9e2f9bd968f39abe33249869e8f5a48e25388',
    'C-space-only': '9f81eeeffd354334f3707efbe7d9e2f9bd968f39abe33249869e8f5a48e25388', 'H-broken': '9f81eeeffd354334f3707efbe7d9e2f9bd968f39abe33249869e8f5a48e25388',
    'H-flush': '7e5e0c0fe5d3a7a666cf65f471fb673fec832bdb2d1b6ba80fdde1ed00caf570', 'H-low': 'ff15bf19770ec1fc184f10c4828f11253b877a2868c8a0b70af6bd66f7dd3256',
    'H-nodot': '7b2eb665196a207ad44d95523481284e982e9ffd70723dc1837f98357c78c16b', 'K-leak': '8f7379ac2d6c6138dbc1de9d390b9c500eeeb0f651c9749a0543a60a89d1b37f'}
CHECK = '--check' in sys.argv[1:]
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
andr = np.asarray(Image.open(ANDROID).convert('RGB'))
print(f'# regen.replay-regraft.py{" --check" if CHECK else ""} — boxes {BOXES} from {os.path.relpath(ANDROID, ROOT)} (sha256 {sha(ANDROID)[:16]}…)')
plan = []
for tag in sorted(PRE):
    p = os.path.join(RP, tag, 'counter-suffix.ios.png')
    h = sha(p)
    if h != PRE[tag]:
        sys.exit(f'refused: {tag} counter-suffix.ios.png sha256 {h[:16]}… is not the committed {PRE[tag][:16]}… (already re-grafted, or changed) — nothing written')
    im = np.asarray(Image.open(p).convert('RGB')).copy()
    before = im.copy()
    for x0, y0, x1, y1 in BOXES:
        im[y0:y1, x0:x1] = andr[y0:y1, x0:x1]
    d = (im != before).any(axis=2)
    ys, xs = np.nonzero(d)
    outside = int(d.sum() - sum(int(d[y0:y1, x0:x1].sum()) for x0, y0, x1, y1 in BOXES))
    assert outside == 0
    plan.append((tag, p, im, int(d.sum()), (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if d.any() else None))
for tag, p, im, n, bb in plan:
    if not CHECK:
        tmp = p + '.tmp-regen'
        Image.fromarray(im).save(tmp, format='PNG'); os.replace(tmp, p)
        post = np.asarray(Image.open(p).convert('RGB'))
        assert (post == im).all(), tag
    print(f'  {tag:13} {n:4} px changed, bbox x{bb[0]}-{bb[2]} y{bb[1]}-{bb[3]} (0 px outside the boxes) · pre {PRE[tag][:16]}…'
          + ('' if CHECK else f' → post {sha(p)[:16]}… (decoded pixels re-read equal)'))
print(f'{"would re-graft" if CHECK else "re-grafted"} {len(plan)} tags')
