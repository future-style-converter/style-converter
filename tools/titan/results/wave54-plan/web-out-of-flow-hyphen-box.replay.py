#!/usr/bin/env python3
# tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.replay.py — the composited replay behind the brief's
# prediction for css-text/hyphens/hyphens-out-of-flow-002 web (BACKLOG obligation 0(c)).
#
# Why a composite is a fair stand-in: the fix (W1, brief §5) rewrites boxes 3–6's runs `[A, M, B]` (A, B the word's two
# halves, M the paint-inert abspos member) to `[A+B, M]` — exactly box 7's runs shape, which the SAME capture already
# renders correctly (ink x25-61 / x24-54, 46 px). So the predicted picture is the wave53-final web capture with boxes 4
# and 5 replaced by a copy of web box 7's 51-px block (46 px box + 5 px collapsed margin) and boxes 6 and 7 moved down
# 40 px to the ref's pitch (y363 / y414). Boxes 1–3 are kept as captured.
# web-W1-exact.png goes one step further and is the EXACT W1 picture: W1 also rewrites boxes 3 and 6 to box 7's runs
# shape, and on the capture those two boxes differ from box 7 in a few px (box 3 at the hyphen, x56-63; box 6 at `way`,
# x46-55 — the separately shaped fragments' sub-pixel origin), so in it boxes 3, 4, 5 and 6 are all box 7's block.
# web-001-W1-exact.png is the same rewrite on hyphens-out-of-flow-001 (the UNGATED W1 variant would reach it; §5).
# Score them with web-out-of-flow-hyphen-box.replay-score.mjs.
import os, sys
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'wave53-plan'))
from geometry_common import paths
P = dict(paths(sys.argv[1] if len(sys.argv) > 1 else 'wave53-final', 'css-text', 'hyphens/hyphens-out-of-flow-002'))
web = Image.open(P['web']).convert('RGB')
W, H = web.size
out = Image.new('RGB', (W, H), (255, 255, 255))
block = lambda top: web.crop((0, top, W, top + 51))      # one box + the 5 px gap under it
out.paste(web.crop((0, 0, W, 261)), (0, 0))              # header paragraph + boxes 1-3, as captured
out.paste(block(374), (0, 261))                          # box 4 := web box 7 (runs [highway, M])
out.paste(block(374), (0, 312))                          # box 5 := web box 7
out.paste(block(323), (0, 363))                          # box 6, moved down 40 px
out.paste(block(374), (0, 414))                          # box 7, moved down 40 px
out.paste(web.crop((0, 425, W, H - 40)), (0, 465))       # the blank tail (white on the capture)
OUT = os.path.join(HERE, 'web-out-of-flow-hyphen-box-replay')
os.makedirs(OUT, exist_ok=True)
out.save(os.path.join(OUT, 'web-W1.png'))
exact = out.copy()
for top in (210, 261, 312, 363):                         # boxes 3-6 := web box 7's block (now at y414)
    exact.paste(out.crop((0, 414, W, 465)), (0, top))
exact.save(os.path.join(OUT, 'web-W1-exact.png'))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
w1 = Image.open(dict(paths(run, 'css-text', 'hyphens/hyphens-out-of-flow-001'))['web']).convert('RGB')
for top in (210, 363):                                   # -001 boxes 3 and 6 := its box 7 (4 and 5 already equal it)
    w1.paste(w1.crop((0, 414, W, 465)), (0, top))
w1.save(os.path.join(OUT, 'web-001-W1-exact.png'))
print('wrote web-W1.png, web-W1-exact.png, web-001-W1-exact.png under', os.path.relpath(OUT, os.path.join(HERE, '..', '..', '..', '..')))
