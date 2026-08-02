// _dispatch.ts — Phase-10 columns long-tail dispatch (7 properties)
// + the wave-24 CSS Gap Decorations Level 1 family (8 properties).
import type { CSSProperties } from 'react';
import { extractColumnCount } from './ColumnCountExtractor';
import { applyColumnCount } from './ColumnCountApplier';
import { extractColumnWidth } from './ColumnWidthExtractor';
import { applyColumnWidth } from './ColumnWidthApplier';
import { extractColumnRuleStyle } from './ColumnRuleStyleExtractor';
import { applyColumnRuleStyle } from './ColumnRuleStyleApplier';
import { extractColumnRuleWidth } from './ColumnRuleWidthExtractor';
import { applyColumnRuleWidth } from './ColumnRuleWidthApplier';
import { extractColumnRuleColor } from './ColumnRuleColorExtractor';
import { applyColumnRuleColor } from './ColumnRuleColorApplier';
import { extractColumnSpan } from './ColumnSpanExtractor';
import { applyColumnSpan } from './ColumnSpanApplier';
import { extractColumnFill } from './ColumnFillExtractor';
import { applyColumnFill } from './ColumnFillApplier';
// ── gap decorations (css-gaps-1): row-axis twins + the four axis knobs ──
import { extractRowRuleStyle } from './RowRuleStyleExtractor';
import { applyRowRuleStyle } from './RowRuleStyleApplier';
import { extractRowRuleWidth } from './RowRuleWidthExtractor';
import { applyRowRuleWidth } from './RowRuleWidthApplier';
import { extractRowRuleColor } from './RowRuleColorExtractor';
import { applyRowRuleColor } from './RowRuleColorApplier';
import { extractColumnRuleBreak } from './ColumnRuleBreakExtractor';
import { applyColumnRuleBreak } from './ColumnRuleBreakApplier';
import { extractRowRuleBreak } from './RowRuleBreakExtractor';
import { applyRowRuleBreak } from './RowRuleBreakApplier';
import { extractColumnRuleInset } from './ColumnRuleInsetExtractor';
import { applyColumnRuleInset } from './ColumnRuleInsetApplier';
import { extractRowRuleInset } from './RowRuleInsetExtractor';
import { applyRowRuleInset } from './RowRuleInsetApplier';
import { extractRuleOverlap } from './RuleOverlapExtractor';
import { applyRuleOverlap } from './RuleOverlapApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyColumnsPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyColumnCount(extractColumnCount(properties)));
  Object.assign(out, applyColumnWidth(extractColumnWidth(properties)));
  Object.assign(out, applyColumnRuleStyle(extractColumnRuleStyle(properties)));
  Object.assign(out, applyColumnRuleWidth(extractColumnRuleWidth(properties)));
  Object.assign(out, applyColumnRuleColor(extractColumnRuleColor(properties)));
  Object.assign(out, applyColumnSpan(extractColumnSpan(properties)));
  Object.assign(out, applyColumnFill(extractColumnFill(properties)));
  // Gap decorations. Order inside the object is irrelevant to CSS — these are
  // all longhands and no shorthand in this set resets another — so the three
  // row-axis twins are grouped first, then the two break knobs, the two
  // insets, and finally the cross-axis paint-order switch.
  Object.assign(out, applyRowRuleStyle(extractRowRuleStyle(properties)));
  Object.assign(out, applyRowRuleWidth(extractRowRuleWidth(properties)));
  Object.assign(out, applyRowRuleColor(extractRowRuleColor(properties)));
  Object.assign(out, applyColumnRuleBreak(extractColumnRuleBreak(properties)));
  Object.assign(out, applyRowRuleBreak(extractRowRuleBreak(properties)));
  Object.assign(out, applyColumnRuleInset(extractColumnRuleInset(properties)));
  Object.assign(out, applyRowRuleInset(extractRowRuleInset(properties)));
  Object.assign(out, applyRuleOverlap(extractRuleOverlap(properties)));
  return out;
}
