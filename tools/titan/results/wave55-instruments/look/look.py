#!/usr/bin/env python3
# tools/titan/results/wave55-instruments/look/look.py — L7's look (task rule 4: "LOOK AT THE PNGS"): the frozen ref next
# to the three wave54-final captures (= wave55-open, byte-identical) of the three tests L7 must not move, one strip each.
# Read-only on the repo; writes the strips beside itself. Usage: /opt/homebrew/bin/python3.12 -B look.py [run=wave54-final]
import os, sys
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
REF = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
def strip(sec, flat, crop, out):
    """ref | web | ios | android, cropped alike, labelled."""
    paths = [('ref', f'{REF}/{sec}/{flat}.png')] + [(p, f'{ROOT}/tools/titan/runs/{RUN}/sections/{sec}/{d}/wpt__{sec}__{flat}.png')
             for p, d in (('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots'))]
    ims = [(l, Image.open(p).convert('RGB').crop(crop)) for l, p in paths]
    w, h = ims[0][1].size; c = Image.new('RGB', (w * 4 + 30, h + 16), 'white'); d = ImageDraw.Draw(c)
    for i, (l, im) in enumerate(ims): c.paste(im, (i * (w + 10), 16)); d.text((i * (w + 10) + 2, 2), l, fill='black')
    c.save(os.path.join(HERE, out))
strip('css-gaps', 'flex__flex-gap-decorations-033', (0, 0, 190, 190), f'flex-gap-decorations-033.{RUN}.png')
strip('css-counter-styles', 'counter-suffix', (0, 0, 260, 330), f'counter-suffix.{RUN}.png')
strip('css-text', 'hyphens__hyphens-out-of-flow-002', (0, 0, 240, 420), f'hyphens-out-of-flow-002.{RUN}.png')
