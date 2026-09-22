# Review notes — lane visual-test-000-034 (wave51 label-chrome baseline refresh)

Scope: 30 stems (000–022, 026, 028–031, 033, 034; the 5 excluded stems 023/024/025/027/032 belong to another lane) × 3 platforms = 90 sheets under `tools/titan/runs/wave51-labels/visual-test/review/`.

Method: every sheet Read (visual pass) PLUS a scratchpad pngjs scan that recomputes the change mask from the committed and fresh panes, rebuilds the expected glyph set P from `tools/visual/block-font.json`, and classifies every changed pixel as (a) in P, or (b) a translated (possibly clipped) copy of P — the old in-box footprint — or (c) residual.

## Findings (all 90 sheets OK)

- green == |P| and newLabel == |P| on every sheet; magenta == 0 everywhere; no sheet lacked a committed twin.
- Residual red == 0 on all 90 sheets: every red pixel is a translated copy of the label glyphs at the old content-box origin (24,22 / 34,32 / 39,37 / 41–44,39–42 / 54,37–52 / 64,32 depending on the fixture's margin/padding/border). No box edge, fill, gradient, border or shadow pixel changed outside the old label ink.
- Label ink is byte-identical on all three platforms: (174,174,180) on the (26,26,46) band, one unique colour per label — no dim/bright platform discrepancy.
- Label text reads the upper-case component name on every sheet; no truncation, doubling or misplacement.

## Explained curiosities (not defects)

- 030_Gradient_Conic Android/iOS: the OLD label was clipped by the circle (`border-radius: 50%` clips content natively), so the old footprint reads "..ADIENT CON.."; web let it overflow. Pure old-label behaviour; the fresh circle is unchanged.
- 033/034 Android: the OLD label was clipped to "FILT" by the box (Compose clips), iOS/web overflowed. Box geometry/colour unchanged.
- 005_Sizing_HeightPercent / 021_BorderRadius_Circle: the old label was truncated by the component's own width (17 / 10 chars); the new band label is complete.
- 014/015 Opacity: old label inherited the element's opacity (dim); the new band label is full-strength by design.
- 017 Border_Dashed: dash pattern differs native vs web, identically in committed and fresh — pre-existing, not a refresh change.
