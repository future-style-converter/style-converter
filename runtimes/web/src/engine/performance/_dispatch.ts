// _dispatch.ts — Phase-10 performance long-tail dispatch (7 properties).
import type { CSSProperties } from 'react';
import { extractContain } from './ContainExtractor';
import { applyContain } from './ContainApplier';
import { extractWillChange } from './WillChangeExtractor';
import { applyWillChange } from './WillChangeApplier';
import { extractContainIntrinsicSize } from './ContainIntrinsicSizeExtractor';
import { applyContainIntrinsicSize } from './ContainIntrinsicSizeApplier';
import { extractContainIntrinsicWidth } from './ContainIntrinsicWidthExtractor';
import { applyContainIntrinsicWidth } from './ContainIntrinsicWidthApplier';
import { extractContainIntrinsicHeight } from './ContainIntrinsicHeightExtractor';
import { applyContainIntrinsicHeight } from './ContainIntrinsicHeightApplier';
import { extractContainIntrinsicBlockSize } from './ContainIntrinsicBlockSizeExtractor';
import { applyContainIntrinsicBlockSize } from './ContainIntrinsicBlockSizeApplier';
import { extractContainIntrinsicInlineSize } from './ContainIntrinsicInlineSizeExtractor';
import { applyContainIntrinsicInlineSize } from './ContainIntrinsicInlineSizeApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyPerformancePhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyContain(extractContain(properties)));
  Object.assign(out, applyWillChange(extractWillChange(properties)));
  Object.assign(out, applyContainIntrinsicSize(extractContainIntrinsicSize(properties)));
  Object.assign(out, applyContainIntrinsicWidth(extractContainIntrinsicWidth(properties)));
  Object.assign(out, applyContainIntrinsicHeight(extractContainIntrinsicHeight(properties)));
  Object.assign(out, applyContainIntrinsicBlockSize(extractContainIntrinsicBlockSize(properties)));
  Object.assign(out, applyContainIntrinsicInlineSize(extractContainIntrinsicInlineSize(properties)));
  return out;
}
