# columns/ — web style-engine scaffold

Mirrors `src/main/kotlin/app/irmodels/properties/columns/`. Each IR
property in that directory gets a `{Property}Config.ts`,
`{Property}Extractor.ts`, and `{Property}Applier.ts` here.

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

CSS Gap Decorations Level 1 (wave 24) — same category, same folder:

- ColumnRuleBreak
- ColumnRuleInset
- RowRuleBreak
- RowRuleColor
- RowRuleInset
- RowRuleStyle
- RowRuleWidth
- RuleOverlap

## Status

All 16 properties ship a Config/Extractor/Applier triplet, dispatched from
`_dispatch.ts` (`applyColumnsPhase10`). The gap-decorations family is a pure
pass-through: Chromium paints gap decorations natively in flex containers, so
Web's recovery is parser + verbatim CSS emission — no painted segments. Its
eight keys are csstype-widened because css-gaps-1 is still a draft.
