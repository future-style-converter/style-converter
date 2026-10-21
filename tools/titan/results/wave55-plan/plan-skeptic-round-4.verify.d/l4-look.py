#!/usr/bin/env python3
# Round-4 verifier: three accepted L4 fakes beside their fixed (C arm) picture, rows y110-229 — left the fake, right the fixed:
# 005 ios band-1's last glyph doubled ("brownn", +158 dark px); 005 android band 2 ("fox") copied 5 px right (+335); 014 ios
# the whole heading copied 1 px down (+75). Usage: python3.12 -B l4-look.py <fakes4 --scratch dir of L4> <out.png>
import glob, sys
from PIL import Image
SC, OUT = sys.argv[1], sys.argv[2]
ims = []
for tag, cap in (('c0_dg-band0-last', 'ios-screenshots/*inset-005.png'), ('c0_hd-band1@+5', 'android-screenshots/*inset-005.png'), ('c1_vd-tgt+1', 'ios-screenshots/*inset-014.png')):
    for run in (f'r4-L4-C-fixed-{tag}', 'r4-L4-C-fixed-FIXED'):
        ims.append(Image.open(glob.glob(f'{SC}/fakeruns/{run}/sections/css-text-decor/{cap}')[0]).convert('RGB').crop((0, 110, 390, 230)))
c = Image.new('RGB', (790, 378), 'white')
for i, im in enumerate(ims):
    c.paste(im, ((i % 2) * 400, (i // 2) * 126))
c.save(OUT)
