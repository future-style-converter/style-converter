#!/usr/bin/env python3
# fix-r1/widget-replay.py <run-sections-dir> <out-dir> — the fix lane's replay of U2 (fix-r1) on the one UA-widget host
# that still moves: css-gaps/flex/flex-gap-decorations-023's <button> `…__2-182` (border box x16..124 × y126..146, 2px
# OUTSET black band, padding 1/6, overflow visible — so fix-r1's clip rule does not hold it back). Its BUTTON replica
# (UAWidgets.Render, intrinsic 108.6×21 = the whole native border box) is drawn from the CONTENT-box origin (24,129) and
# overflows right / down past the band (the replica's own frame is x24..132 × y129..149 in the wave54-final capture).
# Today the band paints after the content and sits on top; under U2 it paints first, so every band pixel inside the
# replica's rect (x24..132 × y129..149) takes the replica's colour: its top hairline on row 129, its #efefef fill below.
import sys
from PIL import Image
RUN, OUT = sys.argv[1], sys.argv[2]
sec, stem, name = 'css-gaps', 'wpt__css-gaps__flex__flex-gap-decorations-023', 'flex__flex-gap-decorations-023'
im = Image.open(f'{RUN}/{sec}/android-screenshots/{stem}.png').convert('RGBA'); px = im.load()
hair, fill = px[128, 129], px[128, 135]       # the replica's own pixels just right of the band (outside it)
print(f'replica hairline {hair[:3]} fill {fill[:3]}')
n = 0
for y in range(129, 147):                      # replica rows that overlap the band (the band ends at y146)
    for x in range(24, 125):                   # replica columns that overlap the band (the band ends at x124)
        if x >= 123 or y >= 145:               # the right band (x123..124) and the bottom band (y145..146)
            new = hair if y == 129 else fill
            if px[x, y] != new: n += 1
            px[x, y] = new
im.save(f'{OUT}/{name}-android-U2.png')
print(f'{name}: {n} band px repainted by the replica')
