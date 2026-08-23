<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/scrolling/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# scrolling/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/scrolling/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- OverflowAnchor
- OverflowClipMargin
- OverscrollBehaviorBlock
- OverscrollBehaviorInline
- OverscrollBehavior
- OverscrollBehaviorX
- OverscrollBehaviorY
- ScrollMarginBlockEnd
- ScrollMarginBlockStart
- ScrollMarginBottom
- ScrollMarginInlineEnd
- ScrollMarginInlineStart
- ScrollMarginLeft
- ScrollMarginRight
- ScrollMarginTop
- …

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.

## Block-level line-clamp cap (wave 46, lane Y1)

`LineClampCap.swift` (+ `LineClampCensus*.swift`, `LineClampChildMetrics.swift`,
`LineClampRootMetrics.swift`, `LineClampHeightCapLayout.swift`) is the iOS
twin of Compose's `scrolling/LineClampCap.kt`: the css-overflow-4 §5
`continue: discard` height cap for a clamp root whose line boxes live in
CHILD components. The renderer attaches it via `View.engineLineClampCap(_:)`
on the flow container (innermost of the box chain); the line-box census
inside walks `meta.runs` / in-flow children in document order and bails to
the uniform N × root-line-box model only when every run shares that box.

