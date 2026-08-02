<!-- iOS style-engine scaffold README -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/columns/ -->
<!-- Phase 0: empty; properties migrate per testing/ROLLOUT.md -->

# columns/ — iOS style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/columns/`. Each
IR property in that directory gets a `{Property}Config.swift`,
`{Property}Extractor.swift`, and `{Property}Applier.swift` here.

See `CLAUDE.md` → *Per-property contract*.

## Expected properties

- ColumnCount
- ColumnFill
- ColumnGap
- ColumnRuleColor
- ColumnRuleStyle
- ColumnRuleWidth
- ColumnSpan
- ColumnWidth

Wave 24 adds the css-gaps-1 gap-decorations family here (same category —
the row-rule-* longhands are the cross-axis twins of column-rule-*):

- ColumnRuleBreak
- ColumnRuleInset
- RowRuleBreak
- RowRuleColor
- RowRuleInset
- RowRuleStyle
- RowRuleWidth
- RuleOverlap

They ship as `GapDecorations{Config,Extractor,Applier}.swift` plus the
pure `GapDecorationSegments.swift` geometry module, because the family
is rendered as ONE painter over a flex container rather than as eight
independent per-property appliers.

## Status

Empty — properties migrate in one-at-a-time from `../Renderer/StyleBuilder.swift`
as they're implemented per the `testing/ROLLOUT.md` phase plan.
