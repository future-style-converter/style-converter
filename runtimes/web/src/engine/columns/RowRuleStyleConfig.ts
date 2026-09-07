// RowRuleStyleConfig.ts — CSS Gap Decorations Level 1 `row-rule-style`.
// https://drafts.csswg.org/css-gaps-1/#row-rule-style
// Wave-24 web recovery: Chromium implements gap decorations in flex natively,
// so Web's whole job is parser + verbatim CSS emission — no painted segments.
// Config mirrors the ColumnRule{Color,Style,Width}Config shape exactly: one
// already-serialised CSS string, `undefined` when the IR carried no value.
export interface RowRuleStyleConfig { value?: string }
