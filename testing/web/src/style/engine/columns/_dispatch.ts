// _dispatch.ts — Phase-10 columns long-tail dispatch (7 properties).
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
  return out;
}
