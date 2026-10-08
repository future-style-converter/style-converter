#!/usr/bin/env python3
"""Skeptic sheet: ref | web | ios | android side by side, cropped to the top
H px, for one <section> <stem> (stem may contain a sub-dir like abspos/x).
Usage: sheet.py <run> <section> <stem> <out.png> [H]"""
import sys, os
from PIL import Image, ImageDraw
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../../.."))
run, sec, stem, out = sys.argv[1:5]
H = int(sys.argv[5]) if len(sys.argv) > 5 else 300
refstem = stem.replace("/", "__")
ref = f"{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/{sec}/{refstem}.png"
flat = "wpt__" + sec + "__" + stem.replace("/", "__")
caps = [f"{ROOT}/tools/titan/runs/{run}/sections/{sec}/{d}/{flat}.png" for d in ("screenshots", "ios-screenshots", "android-screenshots")]
ims = []
for p in [ref] + caps:
    im = Image.open(p).convert("RGB") if os.path.exists(p) else Image.new("RGB", (390, H), (128, 128, 128))
    ims.append(im.crop((0, 0, im.width, min(H, im.height))))
W = sum(i.width for i in ims) + 10 * 3
sheet = Image.new("RGB", (W, H + 14), (255, 0, 255))
x = 0
d = ImageDraw.Draw(sheet)
for lab, i in zip(("ref", "web", "ios", "android"), ims):
    sheet.paste(i, (x, 14)); d.text((x + 2, 1), lab, fill=(0, 0, 0)); x += i.width + 10
sheet.save(out)
print(out)
