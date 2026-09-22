# Review notes — lane glyph-mask-A (30 sheets, 10 stems x 3 platforms)

Method: Read every sheet; then exact-pixel analysis of the diff pane (pane layout
from review-crops.mjs: capture 390 wide, 4-px white gutters, red=(255,0,0),
green=(0,200,0), magenta=(255,0,255); verified changed-set == red|green on all 30).

## Result: 27 ok, 3 unsure, 0 defect

Common to all 30 sheets:
- magenta: 0 px everywhere. green bbox starts at (8,6) on every sheet; green count
  == expected glyph set (no truncation, no missing glyphs).
- fresh label ink == (174,174,180) on web/iOS/Android for every stem (Android is
  within 1 byte on 023/024/025). Where the label overlays component paint reaching
  the band (037 rotated corner, 027 spread ring, 024 glow, 032 blur halo) the
  composited byte values are identical on all three platforms.
- fresh band ink mask is pixel-identical across the three platforms (XOR 0).
- every red run larger than 2 bytes is confined to the old in-box label footprint
  (44,42)..(~132,48) or its transform/blur/shadow ghost; no component edge, fill or
  size changed on any sheet (023/024/025/027 box white and unchanged; 032 blue
  box unchanged; 037/038/040/041 transformed boxes unchanged; 044 zoomed box
  unchanged).

Per-stem notes:
- 023/024/025 Shadow_*: iOS red also carries the old label's own box-shadow
  (dark blob right of the box; blue glow on 024) plus 1-byte Gaussian tails of
  that removed shadow scattered inside the box's own glow (024: x=14..17, 025:
  x=5..23, all |delta|<=2 bytes). Within "immediate shadow surroundings" — ok.
- 027 Shadow_Spread: red == old label ink only, on all three platforms.
- 032 Filter_Blur: red == blurred old-label halo (bbox (36,34)-(115,57)), all three.
- 037/038/040/041: red == rotated/scaled/skewed ghost of the old label; iOS and
  Android ghosts are bolder (bilinear AA of the transformed label) than web's.
  037's ghost tail reaches the capture's bottom row (y=71) — geometry of the
  rotated label, not the box.
- 044 Zoom_75: red == 75%-zoomed old label (web dotted 38 px, iOS/Android 124 px).

## UNSURE — Android__023_Shadow_Simple, Android__024_Shadow_Colored, Android__025_Shadow_Multiple
57 / 54 / 53 red px INSIDE the label band (y 6..12), each a +1-byte brightening
of the background ((26,26,46) -> (27,27,47)) immediately to the RIGHT of vertical
strokes of some glyphs (H, W, and the glyphs at x~62..73 and ~86..97), with a
matching -1 byte on some Android ink pixels ((173,173,179)). Invisible to the eye;
the label's ink mask itself is identical to web/iOS. Android-only; across the whole
run (109 stems) it appears on exactly 4 Android sheets — 023, 024, 025 and
093_Avatar_Circle (outside this lane) — i.e. only on blurred box-shadow stems, and
NOT on 027 Shadow_Spread (spread-only shadow). Looks like the Android band label is
composited through the component's blurred-shadow graphics layer with a sub-pixel
offset. Not covered by design-record §5's allowed red set, so flagged for a decision
rather than passed silently; no component paint is affected.
