#!/usr/bin/env python3
# tools/titan/results/wave55-plan/ua-heading-margin.fakes.py — completes the per-arm picture sets the probe's
# --arm self-test reads (./ua-heading-margin-replay/<test>-<plat>-<ARM>.png), from the replay pictures
# (ua-heading-margin.replay.mjs must have run first) and the wave54-final captures. Read-only outside that folder.
#
# Arms, one picture per native target cell (005/006/014/011 × ios/android):
#   T    today (the wave54-final capture, copied)                       → [M] and [F] must be WRONG (011 [M] WRONG)
#   U1   U1 alone (the replay's M21 / M17; 011 M21)                     → [M] OK, [F] WRONG
#   R    the rejected runtime-face basis (R11 / R9; 011 R11)            → [M] WRONG
#   X2   a DOUBLE margin (today's heading moved 2 × 21 / 2 × 17 px)     → [M] WRONG
#   B    U2 alone: the face with NO margin (B21 / B17; 011 = today)     → [M] WRONG (face present, top ~21 px high)
#   C    U1+U2 with the wrap (the ceiling; 011 = U1)                    → [M] OK, [F] OK
#   D    U1+U2 as each native is predicted to paint it: Compose 005/006 NO wrap (replay D), every other cell = C
#        (iOS wraps at the containing block: wave54-final inset-009 ios; 014 has a definite 16ch width)
#                                                                       → [M] OK; [F] OK except 005/006 android
#   C4   C with the heading 4 px LOW (a margin 4 px too big at the face) → [M] WRONG on 005/006/014
import os
import shutil
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUN = f'{ROOT}/tools/titan/runs/wave54-final/sections/css-text-decor'
OUT = os.path.join(HERE, 'ua-heading-margin-replay')
DIRS = {'ios': 'ios-screenshots', 'android': 'android-screenshots'}
M = {'005': 21, '006': 21, '014': 17, '011': 21}
R = {'005': 11, '006': 11, '014': 9, '011': 11}
CUT = {'005': 96, '006': 96, '014': 96, '011': 118}


def shifted(src, cut, by):
    im = src.copy()
    w, h = im.size
    im.paste((255, 255, 255), (0, cut, w, h))
    im.paste(src.crop((0, cut, w, h - by)), (0, cut + by))
    return im


for n in ('005', '006', '014', '011'):
    t = f'text-decoration-inset-{n}'
    for plat, d in DIRS.items():
        cap = os.path.join(RUN, d, f'wpt__css-text-decor__{t}.png')
        dst = lambda arm: os.path.join(OUT, f'{t}-{plat}-{arm}.png')  # noqa: E731
        shutil.copyfile(cap, dst('T'))
        shutil.copyfile(os.path.join(OUT, f'{t}-{plat}-M{M[n]}.png'), dst('U1'))
        shutil.copyfile(os.path.join(OUT, f'{t}-{plat}-R{R[n]}.png'), dst('R'))
        shifted(Image.open(cap).convert('RGB'), CUT[n], 2 * M[n]).save(dst('X2'))
        if n == '011':  # U2 never reaches 011 (its fold bails): B = today, C = D = U1
            shutil.copyfile(cap, dst('B'))
            shutil.copyfile(dst('U1'), dst('C'))
            shutil.copyfile(dst('U1'), dst('D'))
            continue
        shutil.copyfile(os.path.join(OUT, f'{t}-{plat}-B{M[n]}.png'), dst('B'))
        # C: the replay already wrote <t>-<plat>-C.png under this exact name (the ceiling arm).
        if not os.path.exists(dst('D')):  # the replay wrote D for Compose 005/006 only; every other cell wraps → C
            shutil.copyfile(dst('C'), dst('D'))
        shifted(Image.open(dst('C')).convert('RGB'), 96, 4).save(dst('C4'))
print('ok')
