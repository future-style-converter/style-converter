#!/usr/bin/env python3
# tools/titan/results/wave54-label-chrome/look-seed.py — wave 54 lane L7 (label-chrome), [W-L7] step 2 "LOOK at the six
# canvases x3": builds ONE montage (rows = the six all-then-color stems, columns = Android / iOS / web, 2x nearest-neighbour)
# from a PNG directory, so the seed is looked at before it is committed. Expected picture: the block-font label in the top
# band on 000 / 002 / 004 only; 001 / 003 / 005 with an empty band (container / container / text root, DYNAMIC_CAPTURE §5).
#   python3 tools/titan/results/wave54-label-chrome/look-seed.py [DIR|baseline] OUT.png
#   DIR defaults to tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8; `baseline` = tools/visual/baseline
import os, sys
from PIL import Image, ImageDraw                                         # Pillow: decode + compose
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))   # repo root
args = sys.argv[1:]                                                        # [DIR] OUT
src = args[0] if len(args) > 1 else os.path.join(ROOT, 'tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8')
src = os.path.join(ROOT, 'tools/visual/baseline') if src == 'baseline' else src           # the committed dir after seeding
out_path = args[-1]                                                        # where the montage goes
STEMS = ['000_ATC_AllThenProps', '001_ATC_PropsThenAll_InGreenParent', '002_reset',
         '003_ATC_InitialUnderRedParent', '004_span', '005_ATC_DirectionSurvives']      # capture order (device flatten)
PLATS, S = ['Android', 'iOS', 'web'], 2                                    # column order, scale
rows = [[Image.open(os.path.join(src, f'{p}__{s}.png')).convert('RGB') for p in PLATS] for s in STEMS]  # a missing PNG raises: loud
H = sum(max(im.height for im in r) * S + 24 for r in rows)                 # stacked height with a caption per row
out = Image.new('RGB', (390 * S * 3 + 40, H), (255, 255, 255)); d = ImageDraw.Draw(out); y = 0  # white page
for s, r in zip(STEMS, rows):                                              # one row per stem
    d.text((4, y + 4), s, fill=(0, 0, 0)); y += 20                         # caption
    for k, im in enumerate(r): out.paste(im.resize((im.width * S, im.height * S), Image.NEAREST), (k * (390 * S + 20), y))  # 2x, no smoothing
    y += max(im.height for im in r) * S + 4                                # next row
out.save(out_path); print(f'{out_path} {out.size[0]}x{out.size[1]} from {src}')            # where to LOOK
