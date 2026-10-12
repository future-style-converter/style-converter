#!/usr/bin/env python3
# widget-replay.py — the L6 skeptic's replay of U2 on the two UA-widget <input> captures outside the plan set.
# Today (wave54-final android) the input's border box is x16..168 × y16..36; its 2px INSET band paints AFTER the content,
# so it covers the UA-widget replica's 153×21 white FillRect, which starts at the content-box origin (20,19) and
# overflows the 145×15 content box (Canvas(Modifier.size(153,21)) is coerced to the constraints, its draw is not clipped)
# — only the element's own overflow:clip at the border box stops it. Under U2 the band paints FIRST, so the replica's
# white fill (x20..172, y19..39) lands on the right band (x167..168) and the bottom band (y35..36). The replica's
# hairline is at x20 / y19 (left/top, drawn last over the fill: those pixels keep #767676) and x172 / y39 (outside the border box, clipped). Writes the predicted
# picture; widget-replay-score.mjs scores both against the frozen ref.
import sys
from PIL import Image
RUN, OUT = sys.argv[1], sys.argv[2]
for sec, stem, name in (('css-cascade', 'wpt__css-cascade__revert-layer-015', 'revert-layer-015'),
                        ('css-pseudo', 'wpt__css-pseudo__first-line-and-placeholder', 'first-line-and-placeholder')):
    im = Image.open(f'{RUN}/{sec}/android-screenshots/{stem}.png').convert('RGBA')
    px = im.load(); n = 0
    for y in range(20, 37):              # rows below the replica's top hairline (y19 keeps its #767676 hairline)
        for x in range(21, 169):         # replica fill right of its left hairline (x20, #767676) to the border-box clip
            if x >= 167 or y >= 35:      # only the right / bottom band pixels change
                if px[x, y][:3] != (255, 255, 255): n += 1
                px[x, y] = (255, 255, 255, 255)
    im.save(f'{OUT}/{name}-android-U2.png')
    print(f'{name}: {n} band px turned white')
