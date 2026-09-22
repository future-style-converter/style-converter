# wave51-labels sheet review — lane: small-fixtures

Reviewed 63 sheets (Read on every PNG, 3 panes committed | fresh | diff at 3x):
- filter-sepia-amounts/review: 24 sheets (8 stems x web/iOS/Android)
- aspect-ratio/review: 27 sheets (9 stems x 3)
- filter-grayscale-basis/review: 12 sheets (4 stems x 3)

Verdict: 63 ok, 0 defect, 0 unsure.

## What every sheet shows
- Committed pane: label inside the box at the content-box origin (x=24, rows 22..28).
- Fresh pane: the same box, untouched, and the label in the pad band at (8,6).
- Diff pane: GREEN = exactly the band glyph set; RED = exactly the old in-box
  footprint (rows 22..28, x from 24) where label ink became the box fill;
  no MAGENTA; nothing else red. Box geometry, fill and edges unchanged.

## Throwaway measurement (scratchpad sheet-stats.mjs, pure-colour pixel scan of the diff pane)
On all 63 sheets: red bbox = [24,22 .. <=166,28], green bbox = [8,6 .. <=150,12],
magenta = 0, and committed-vs-fresh changed-pixel count == red + green exactly
(every changed pixel is either the new band glyph or the old footprint).
Band ink in the fresh captures is a single flat colour (174,174,180) on web, iOS
and Android alike (the composited byte-180 value from the design record); pad is
(26,26,46). No platform is dimmer or brighter than another.

## Per-stem notes (all ok)
- 004_Sepia_ExtendedRange: the OLD in-box label was truncated to
  "SEPIA EXTENDEDRAN" by the 60-px box; the NEW band label reads the full
  "SEPIA EXTENDEDRANGE" (green 296 px vs red 261 px). Expected, not a defect.
- 005_Sepia_White: the old label was near-invisible on the white box; diff still
  marks its footprint red. Expected.
- 006_Sepia_Translucent: iOS box is bluish-grey while web/Android are neutral
  grey, in BOTH committed and fresh panes; diff shows only the label. This is the
  already-ledgered iOS filter-composite drift (design-record s5 step 1), not a
  label effect.
- 008_Ratio_Auto_With_Explicit: web PNG is 390x144, iOS/Android 390x145 — the
  committed web baseline is also 144 (checked with `file`), so the refresh
  changes no dimension; the cross-platform 1-px difference is pre-existing.
- aspect-ratio 000/001/002/005/007 print "[phase-b] divergence=mixed/structural"
  lines in verdict-lines.txt; those are the cross-platform gate's pre-existing
  classifications, and the sheets show identical box geometry in committed and
  fresh panes on all three platforms.

## Labels
Every band label reads the component name in upper-case block letters
(e.g. "RATIO 16 9", "GRAYBASIS MIXED", "SEPIA OVERCLAMP"), single, at (8,6),
not truncated, same glyph mask on all three platforms.
