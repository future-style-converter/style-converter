// _dispatch.ts — Phase-10 math long-tail dispatch (3 properties).
import type { CSSProperties } from 'react';
import { extractMathStyle } from './MathStyleExtractor';
import { applyMathStyle } from './MathStyleApplier';
import { extractMathShift } from './MathShiftExtractor';
import { applyMathShift } from './MathShiftApplier';
import { extractMathDepth } from './MathDepthExtractor';
import { applyMathDepth } from './MathDepthApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyMathPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyMathStyle(extractMathStyle(properties)));
  Object.assign(out, applyMathShift(extractMathShift(properties)));
  Object.assign(out, applyMathDepth(extractMathDepth(properties)));
  return out;
}
