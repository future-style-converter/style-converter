# L4 look helper: ref | web | ios | android side by side for one test, scaled
# down, so a lane reader sees the picture before reasoning about a fix.
# usage: python3 look.py <run> <section> <ref-relative-stem> <out.png>
import sys, os
from PIL import Image, ImageDraw
run, sec, stem, out = sys.argv[1:5]
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
# The frozen ref tree flattens a sub-directory stem's '/' to '__' (refs/<sha>/<variant>/<sec>/flex__x.png).
REF = os.path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin', sec, stem.replace('/', '__') + '.png')
cap = 'wpt__' + sec + '__' + stem.replace('/', '__') + '.png'
base = os.path.join(ROOT, 'tools/titan/runs', run, 'sections', sec)
paths = [('ref', REF)] + [(p, os.path.join(base, d, cap)) for p, d in (('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots'))]
ims = []
for label, p in paths:
    # A missing capture is drawn as a grey tile so the absence is visible.
    im = Image.open(p).convert('RGB') if os.path.exists(p) else Image.new('RGB', (390, 600), (128, 128, 128))
    ims.append((label, im))
w = sum(im.width for _, im in ims) + 10 * (len(ims) - 1); h = max(im.height for _, im in ims) + 20
sheet = Image.new('RGB', (w, h), (255, 0, 255)); x = 0; d = ImageDraw.Draw(sheet)
for label, im in ims:
    sheet.paste(im, (x, 20)); d.text((x + 4, 4), label, fill=(0, 0, 0)); x += im.width + 10
sheet.save(out)
print(out, sheet.size)
