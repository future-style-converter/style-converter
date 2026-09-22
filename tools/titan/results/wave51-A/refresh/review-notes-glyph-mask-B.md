# Review notes — lane glyph-mask-B (27 sheets, 9 stems × 3 platforms)

Method: each sheet split into its three panes (1170-px panes, 12-px white
separators, 3× zoom → original frame 390 px wide); per pane counted the
diff-mask colours (green 0,200,0 / red 255,0,0 / magenta 255,0,255),
clustered the red, recomputed committed-vs-fresh independently (0 unexplained
pixels on every sheet), compared the green glyph-mask pixel SETS across the
three platforms (identical ×3 on all 9 stems), and tabulated the fresh ink
bytes under the green mask. Then looked at every pane stacked at 3× plus the
band at 6×. No magenta anywhere. Every label reads its stem name correctly,
untruncated, at (8,6).

## Verdicts

| stem | web | iOS | Android |
|---|---|---|---|
| 046_Perspective_Rotate | UNSURE | ok | ok |
| 081_Outline_Solid | ok | ok | ok |
| 082_Outline_Dashed | ok | ok | ok |
| 089_Card_Complete | ok | ok | ok |
| 093_Avatar_Circle | ok | ok | DEFECT |
| 096_Tooltip_Style | ok | ok | ok (note) |
| 098_Neumorphic_Light | ok | ok (note) | ok |
| 099_Edge_NegativeOffset | ok | ok | ok |
| 106_Edge_MultiTransform | ok | ok | ok |

## Details

### web__046_Perspective_Rotate — UNSURE
Old-label row (43..136, 42..48) red as expected, but ALSO two 1-px columns
along the perspective-rotated box's AA edges, rows 16..55 (80 px):
- x=19 (left edge): (110,67,135) → (110,67,134) — blue −1.
- x=62 (right edge): (114,69,139) → (110,67,134) — Δ4/2/5; the right edge is
  now byte-equal to the left edge.
No column moved (x=18 and x=63 are ground in both, x=20 and x=61 are the
full fill in both), so this is a coverage re-rasterisation of the 3D layer
after its label child left it, not a shifted edge. It is nevertheless
outside regions (i)+(ii) of §5 step 2, hence not "ok". iOS and Android 046
show only the old-label row.

### Android__093_Avatar_Circle — DEFECT (label raster, ±1)
Glyph-mask positions identical ×3, but Android's fresh ink is not P on
46 glyph px: (173,173,179)/(173,173,180) instead of (174,174,180), over the
plain ground (committed byte under them is (26,26,46), NOT component paint).
Every such column has the pixel immediately to its RIGHT lifted
(26,26,46) → (27,27,47) — 48 red px inside the band rows 6..12 at
x ∈ {15..19, 21, 39, 41..43, 63, 65..67, 69, 72}. Affected glyphs: V, first
column of the second A, the R of AVATAR, the R and C of CIRCLE. Pattern =
horizontal 1-px sub-pixel bleed. The committed Android baseline shows the
same bleed on the OLD label (x=48 beside the T stem, (155,90,182) vs web
(155,89,182), 6 px; 7 old ink px at (212,193,220) vs (212,192,220)), so it
is an Android-only raster property on this stem rather than something the
band move introduced — but it fails "fresh glyph pixel == P" (S4b) and it is
red in the band. web and iOS 093: exact P on all 180 glyph px, red only in
the clipped old "AVATAR" (27..61, 25..31).

### Android__096_Tooltip_Style — ok, note (predicted clause (iii) ±1)
22 glyph px on rows 11..12, x 21..59 read (174,174,179)/(173,173,179): the
tooltip's shadow reaches those rows on Android (committed ground there is
(26,26,45)/(25,25,45)) and Android's composite lands 1 below web's, which
gives (174,174,180) over the same (25,25,45). No neighbour lift, no red in
the band. This is the "one platform paints under the glyphs, ±1" case the
tooling lane predicted for the refresh decision on clause (iii).

### iOS__096_Tooltip_Style — ok
Red 5769 px in one blob (36..114, 24..46): the old label's own box-shadow
halo (committed (25,25,45) → ground (26,26,46), 1 level), gone with the
label. Confined to the old label's surroundings; the tooltip box itself is
unchanged. Category (b).

### iOS__098_Neumorphic_Light — ok, note
Red 49k px (49..165, 15..81): the old label's neumorphic glow (committed up
to (42,42,61) at (120,60), (30,30,50) at (150,40) → ground) — visible as a
haze right of the box in the committed pane, gone in fresh. Category (b).
Three isolated 1-level pixels at x=18 on the box's corner gradient
((18,17) 207→206, (18,22) 229→228, (18,61) 149→148) are the tail of the
same glow. web/Android 098: old-label row only; the ink over the box's
glow reaching the band varies (201/188/205…) as translucent ink over a
lighter ground should, identically-positioned ×3.

### 081 / 082 (outlines), 099 (box top edge in the band)
The band label is drawn over the outline / box top edge on all three
platforms: ink over the red outline = (235,189,184) ×3; over the blue dashes
(182,212,232) web/iOS, (181,211,231) Android (±1, paint under glyphs, the
legitimate per-platform case of S4). Old label clipped by the box on 099
("EDGE NEGATIVEO" web/iOS, "EDGE NEGA" Android) — red confined to it.

### 089, 106
089: old label row inside the card (44..120, 42..48), card unchanged ×3.
106: red = the old label's rotate/scale/skew ghost (47..147, 42..66),
bolder on iOS/Android (4.9k/4.8k px) than web (2.1k) but confined; the
green box unchanged ×3.
