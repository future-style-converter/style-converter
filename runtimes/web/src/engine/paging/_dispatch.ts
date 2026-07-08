// _dispatch.ts — Phase-10 paging long-tail dispatch (7 properties).
import type { CSSProperties } from 'react';
import { extractBreakBefore } from './BreakBeforeExtractor';
import { applyBreakBefore } from './BreakBeforeApplier';
import { extractBreakAfter } from './BreakAfterExtractor';
import { applyBreakAfter } from './BreakAfterApplier';
import { extractBreakInside } from './BreakInsideExtractor';
import { applyBreakInside } from './BreakInsideApplier';
import { extractPageBreakBefore } from './PageBreakBeforeExtractor';
import { applyPageBreakBefore } from './PageBreakBeforeApplier';
import { extractPageBreakAfter } from './PageBreakAfterExtractor';
import { applyPageBreakAfter } from './PageBreakAfterApplier';
import { extractPageBreakInside } from './PageBreakInsideExtractor';
import { applyPageBreakInside } from './PageBreakInsideApplier';
import { extractMarginBreak } from './MarginBreakExtractor';
import { applyMarginBreak } from './MarginBreakApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyPagingPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyBreakBefore(extractBreakBefore(properties)));
  Object.assign(out, applyBreakAfter(extractBreakAfter(properties)));
  Object.assign(out, applyBreakInside(extractBreakInside(properties)));
  Object.assign(out, applyPageBreakBefore(extractPageBreakBefore(properties)));
  Object.assign(out, applyPageBreakAfter(extractPageBreakAfter(properties)));
  Object.assign(out, applyPageBreakInside(extractPageBreakInside(properties)));
  Object.assign(out, applyMarginBreak(extractMarginBreak(properties)));
  return out;
}
