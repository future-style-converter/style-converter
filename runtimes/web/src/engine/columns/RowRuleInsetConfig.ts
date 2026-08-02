// RowRuleInsetConfig.ts — CSS Gap Decorations Level 1 `row-rule-inset`.
// https://drafts.csswg.org/css-gaps-1/#row-rule-inset
// Wave-24 web recovery: Chromium implements gap decorations in flex natively,
// so Web's whole job is parser + verbatim CSS emission — no painted segments.
// Config mirrors the ColumnRule{Color,Style,Width}Config shape exactly: one
// already-serialised CSS string, `undefined` when the IR carried no value.
export interface RowRuleInsetConfig { value?: string }
export const ROWRULEINSET_PROPERTY_TYPE = 'RowRuleInset' as const;
