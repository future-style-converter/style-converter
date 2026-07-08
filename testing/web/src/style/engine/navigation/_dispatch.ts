// _dispatch.ts — Phase-10 navigation long-tail dispatch (5 properties).
import type { CSSProperties } from 'react';
import { extractNavUp } from './NavUpExtractor';
import { applyNavUp } from './NavUpApplier';
import { extractNavDown } from './NavDownExtractor';
import { applyNavDown } from './NavDownApplier';
import { extractNavLeft } from './NavLeftExtractor';
import { applyNavLeft } from './NavLeftApplier';
import { extractNavRight } from './NavRightExtractor';
import { applyNavRight } from './NavRightApplier';
import { extractReadingOrder } from './ReadingOrderExtractor';
import { applyReadingOrder } from './ReadingOrderApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyNavigationPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyNavUp(extractNavUp(properties)));
  Object.assign(out, applyNavDown(extractNavDown(properties)));
  Object.assign(out, applyNavLeft(extractNavLeft(properties)));
  Object.assign(out, applyNavRight(extractNavRight(properties)));
  Object.assign(out, applyReadingOrder(extractReadingOrder(properties)));
  return out;
}
