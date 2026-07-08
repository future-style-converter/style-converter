// _dispatch.ts — Phase-10 content long-tail dispatch (1 property).
import type { CSSProperties } from 'react';
import { extractContent } from './ContentExtractor';
import { applyContent } from './ContentApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyContentPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyContent(extractContent(properties)));
  return out;
}
