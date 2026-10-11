#!/usr/bin/env python3
# tools/titan/results/wave55-plan/cbb-android.fakes.py — the planning fakes both clip-3 probes are read against
# (cbb-android.md §7). Each is a WRONG picture a plausible-but-wrong U2 would capture, built from Android's own pixels
# (cbb-android-replay/android-clip-3.png, itself the probe capture re-laid by cbb-android.replay.py):
#   F3 fake-centered.png    #middle measured 220×120 through Compose's stock requiredWidth/requiredHeight, which CENTRE
#                           an oversized placeable (ExactWidthOverflow.kt's header): #middle covers #outer's padding
#                           box x16..235, #inner lands at x26..225 / y26..125.
#   F4 fake-unclipped.png   U2 right but #outer's overflow clip missing: #middle's grey right/bottom padding band
#                           paints at x236..245 / y136..145.
#   F6 fake-double.png      the band added twice (#middle 240×140, #inner 220×120): #inner's right/bottom corners fall
#                           outside #outer's clip, so the green fills the bottom-right corner square.
# Usage: /opt/homebrew/bin/python3.12 tools/titan/results/wave55-plan/cbb-android.fakes.py
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
base = Image.open(f'{HERE}/cbb-android-replay/android-clip-3.png').convert('RGB')
W, H = base.size
src = base.load()
GREY = src[30, 80]     # #middle's own grey, as Android painted it (left padding band, row y=80)
GREEN = src[120, 80]   # the inverted backdrop green, as Android painted it


def save(img, name):
    img.save(f'{HERE}/cbb-android-replay/{name}')
    print('wrote', name)


# F3 — centred: everything inside #outer's padding box is #middle (grey) except #inner moved by (-10, -10).
f3 = base.copy(); o = f3.load()
for y in range(16, 136):
    for x in range(16, 236):
        o[x, y] = src[x + 10, y + 10] if (26 <= x <= 225 and 26 <= y <= 125) else GREY
save(f3, 'fake-centered.png')

# F4 — unclipped overflow: #middle's right band x236..245 (y26..145) and bottom band y136..145 (x26..245) paint.
f4 = base.copy(); o = f4.load()
for y in range(26, 146):
    for x in range(236, 246):
        o[x, y] = GREY
for y in range(136, 146):
    for x in range(26, 246):
        o[x, y] = GREY
save(f4, 'fake-unclipped.png')

# F6 — doubled band: #inner reaches past #outer's clip on the right and bottom, so its radius-25 arcs there are cut
# off and the corner squares fill with green up to #outer's own radius-10 rounding (kept from the replay's pixels).
f6 = base.copy(); o = f6.load()
for y in range(36, 136):
    for x in range(36, 236):
        tl = (x - 61) ** 2 + (y - 61) ** 2 > 25 ** 2 and x < 61 and y < 61   # keep #inner's top-left arc only
        outer_corner = (x - 225) ** 2 + (y - 125) ** 2 > 10 ** 2 and x > 225 and y > 125   # #outer's own rounding
        if not tl and not outer_corner:
            o[x, y] = GREEN
save(f6, 'fake-double.png')
