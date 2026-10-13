#!/opt/homebrew/bin/python3.12
# wave-55 L2 look sheets: ref | wave54-final android | wave54-probe android (= HEAD + U1 on device), 2x nearest.
# Usage: /opt/homebrew/bin/python3.12 -I look.py <shared-tree-root> <out.png>
import sys
from PIL import Image
SH, out = sys.argv[1], sys.argv[2]
REF = SH + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
rows = [('filter-effects', 'backdrop-filter-nested-border-radius-clip-3', (0, 0, 260, 160)),
        ('filter-effects', 'backdrop-filter-nested-border-radius-clip', (0, 0, 260, 160)),
        ('filter-effects', 'backdrop-filter-nested-border-radius-clip-4', (0, 0, 260, 160)),
        ('css-flexbox', 'abspos/abspos-autopos-htb-ltr', (0, 60, 160, 220)),
        ('css-flexbox', 'abspos/abspos-autopos-htb-rtl', (0, 60, 160, 220))]
tiles = []
for sec, test, box in rows:
    flat = test.replace('/', '__')
    paths = [f'{REF}/{sec}/{flat}.png',
             f'{SH}/tools/titan/runs/wave54-final/sections/{sec}/android-screenshots/wpt__{sec}__{flat}.png',
             f'{SH}/tools/titan/runs/wave54-probe/sections/{sec}/android-screenshots/wpt__{sec}__{flat}.png']
    row = []
    for p in paths:
        im = Image.open(p).convert('RGB').crop(box)
        row.append(im.resize((im.size[0] * 2, im.size[1] * 2), Image.NEAREST))
    tiles.append(row)
W = max(sum(t.size[0] for t in r) + 20 for r in tiles)
H = sum(r[0].size[1] + 10 for r in tiles)
sheet = Image.new('RGB', (W, H), (255, 255, 255))
y = 0
for r in tiles:
    x = 0
    for t in r:
        sheet.paste(t, (x, y)); x += t.size[0] + 10
    y += r[0].size[1] + 10
sheet.save(out)
print(out, sheet.size)
