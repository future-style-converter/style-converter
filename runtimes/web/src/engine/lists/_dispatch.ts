// _dispatch.ts — Phase-10 lists long-tail dispatch (3 properties).
import type { CSSProperties } from 'react';
import { extractListStyleType } from './ListStyleTypeExtractor';
import { applyListStyleType } from './ListStyleTypeApplier';
import { extractListStylePosition } from './ListStylePositionExtractor';
import { applyListStylePosition } from './ListStylePositionApplier';
import { extractListStyleImage } from './ListStyleImageExtractor';
import { applyListStyleImage } from './ListStyleImageApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyListsPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyListStyleType(extractListStyleType(properties)));
  Object.assign(out, applyListStylePosition(extractListStylePosition(properties)));
  Object.assign(out, applyListStyleImage(extractListStyleImage(properties)));
  return out;
}
