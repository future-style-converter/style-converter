#!/usr/bin/env python3
# Wave 52 · lane L9 — PNG replay, step 1: build the PREDICTED post-fix
# captures by pixel surgery on the wave51-fix captures (PLAN.md §0 (iii):
# "the predicted post-fix picture … laid over the frozen ref, with the
# residual described in pixels"). Step 2 (replay.mjs) scores them with the
# campaign's own SSIM recipe. Every coordinate below was measured off the
# named capture (ink-band scan, see _note.md "PNG replay"); nothing is drawn
# from a font — only the capture's own glyph pixels are moved or recoloured,
# so the prediction carries the native rasteriser's exact ink.
#
#   T1 hanging-punctuation-inline-001 (android, ios): F1 folds the `」` member
#      onto the orange line; the 4em box (128px at 32px) holds 4 ideographs
#      exactly and UAX #14 LB13 forbids a break before U+300D (class CL), so
#      the run wraps as web's capture does: `字字字` / `字」`. Built by
#      erasing the 4th orange glyph and the stacked BLACK `」`, then pasting
#      the native's own BLUE `字」` (the reference div's last two glyphs,
#      same face/size) recoloured orange onto line 3 (pitch +40px = 2em ×
#      1.25, web's measured pitch 132→172).
#   T4 block-ellipsis-025 (ios, android): F4 bakes the marker — line 4 is the
#      "…" ALONE. Built by erasing line 4 and pasting the capture's own "…"
#      (line 2's trailing glyph) at the line start.
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
RUN = os.path.join(ROOT, "tools", "titan", "runs", "wave51-fix", "sections")
OUT = os.path.join(HERE, "replay")
os.makedirs(OUT, exist_ok=True)
WHITE = (255, 255, 255)
ORANGE = (255, 165, 0)   # css `orange`, the host's colour


def erase(im, box):
    # Paint the WPT canvas white over box = (x0, y0, x1, y1), exclusive.
    im.paste(WHITE, box)


def blue_to_orange(region):
    # Anti-aliased blue-on-white → the same coverage in orange: blue ink has
    # r = 0, so coverage t = (255 - r) / 255, and the orange pixel is the
    # white→orange blend at that coverage (straight alpha over white).
    px = region.load()
    for y in range(region.size[1]):
        for x in range(region.size[0]):
            r = px[x, y][0]
            t = (255 - r) / 255.0
            px[x, y] = tuple(round(255 - t * (255 - c)) for c in ORANGE)
    return region


def t1(platform, blue_y, orange_y):
    # blue_y / orange_y: the measured top of each glyph band (android
    # 95/135, ios 94/134); glyph cells are 32px wide from x = 18.
    src = os.path.join(RUN, "css-text", f"{platform}-screenshots",
                       "wpt__css-text__hanging-punctuation__hanging-punctuation-inline-001.png")
    im = Image.open(src).convert("RGB")
    # The 4th orange ideograph (x 114-141) leaves line 2.
    erase(im, (112, orange_y - 3, 146, orange_y + 33))
    # The stacked black `」` (x 17-26, y 184-204 on both natives) goes.
    erase(im, (14, 180, 30, 208))
    # The blue line's `字」` (x 114-154) → line 3 at x 18, recoloured.
    region = im.crop((112, blue_y - 3, 158, blue_y + 33))
    im.paste(blue_to_orange(region), (112 - 96, blue_y - 3 + 80))
    out = os.path.join(OUT, f"hanging-punctuation-inline-001.{platform}.predicted.png")
    im.save(out)
    return out


def t4(platform, l2_marker_x, l2_top, l4_top):
    # The capture's own "…" (line 2's last glyph) pasted at line 4's start.
    src = os.path.join(RUN, "css-overflow", f"{platform}-screenshots",
                       "wpt__css-overflow__line-clamp__block-ellipsis-025.png")
    im = Image.open(src).convert("RGB")
    marker = im.crop((l2_marker_x - 2, l2_top - 2, l2_marker_x + 9, l2_top + 15))
    # Line 4 (the word + its marker on iOS, the clipped word on Android).
    erase(im, (0, l4_top - 2, 390, l4_top + 15))
    # The ref's line-4 "…" starts at x 16 (dots 16-22).
    im.paste(marker, (16 - 2, l4_top - 2))
    out = os.path.join(OUT, f"block-ellipsis-025.{platform}.predicted.png")
    im.save(out)
    return out


def t030_ios():
    # Fix pass (skeptic M1) — block-ellipsis-030 box 1 (`123<U+1680>5 789`,
    # 5ch, line-clamp 1). The fixed clamp hides at the OGHAM SPACE MARK and
    # bakes `123…` — the very string box 2 carries verbatim, in the same
    # label face. So box 1's interior is box 2's own glyph rows: the
    # capture's text sits 4px under each box's top border (box 1 border
    # y 88, glyphs 92-100; box 2 border y 119, glyphs 123-131; borders
    # x 29 / 69, measured off the wave51-fix iOS PNG).
    src = os.path.join(RUN, "css-overflow", "ios-screenshots",
                       "wpt__css-overflow__line-clamp__block-ellipsis-030.png")
    im = Image.open(src).convert("RGB")
    glyphs = im.crop((30, 123, 69, 132))
    # Today's TextKit `12…` leaves; the baked `123…` arrives.
    erase(im, (30, 89, 69, 105))
    im.paste(glyphs, (30, 92))
    out = os.path.join(OUT, "block-ellipsis-030.ios.predicted.png")
    im.save(out)
    return out


def t030_ios_old_f4():
    # COUNTERFACTUAL — the skeptic's M1 picture: the PRE-fix F4 found no
    # U+0020 on `123<U+1680>5` and baked `…` ALONE in box 1. Built from box
    # 2's own "…" (dots x 54-61, y 130-131) moved to box 1's first cell
    # (x 31) on box 1's baseline (dots row 99-100, 31px above box 2's).
    src = os.path.join(RUN, "css-overflow", "ios-screenshots",
                       "wpt__css-overflow__line-clamp__block-ellipsis-030.png")
    im = Image.open(src).convert("RGB")
    dots = im.crop((53, 129, 63, 133))
    erase(im, (30, 89, 69, 105))
    im.paste(dots, (30, 98))
    out = os.path.join(OUT, "block-ellipsis-030.ios.counterfactual-preFixF4.png")
    im.save(out)
    return out


def t028_ios_old_f4():
    # COUNTERFACTUAL, not a prediction: what the PRE-fix F4 (U+0020-only
    # cut) would have painted on block-ellipsis-028 iOS — line 2 cut back
    # to `…room…`, losing the visible `uncha` (x 354-389, y 36-48). The
    # fixed walk hides at the soft hyphen instead (`…uncharacteristi‐…`,
    # 60ch), whose tail lies past the 390px canvas, so the FIXED picture's
    # visible region is today's capture unchanged. The "…" glyph is the
    # iOS 025 capture's own line-2 marker (same monospace label face;
    # dots x 259-265, y 44-45), placed in the cell after `room` (x 346).
    src = os.path.join(RUN, "css-overflow", "ios-screenshots",
                       "wpt__css-overflow__line-clamp__block-ellipsis-028.png")
    im = Image.open(src).convert("RGB")
    donor = Image.open(os.path.join(RUN, "css-overflow", "ios-screenshots",
                                    "wpt__css-overflow__line-clamp__block-ellipsis-025.png")).convert("RGB")
    marker = donor.crop((257, 40, 268, 49))
    erase(im, (346, 36, 390, 49))
    im.paste(marker, (346, 40))
    out = os.path.join(OUT, "block-ellipsis-028.ios.counterfactual-preFixF4.png")
    im.save(out)
    return out


if __name__ == "__main__":
    print(t1("android", 95, 135))
    print(t1("ios", 94, 134))
    # ios: line 2 "…" dots x 259-265, line 2 top 36, line 4 top 68.
    print(t4("ios", 259, 36, 68))
    # android: line 2 "…" x 259-265, top 35, line 4 top 68 (line 3 stays
    # CLIPPED at the box edge in this simulation — the residual F4's
    # `Visible` repairs on device but pixels cannot be invented here).
    print(t4("android", 259, 35, 68))
    # Fix pass (skeptic M1): 030 iOS prediction + the 028 pre-fix counterfactual.
    print(t030_ios())
    print(t028_ios_old_f4())
    print(t030_ios_old_f4())
