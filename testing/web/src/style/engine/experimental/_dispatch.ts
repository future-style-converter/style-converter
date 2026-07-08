// _dispatch.ts — Phase-10 experimental long-tail dispatch (3 properties).
import type { CSSProperties } from 'react';
import { extractPresentationLevel } from './PresentationLevelExtractor';
import { applyPresentationLevel } from './PresentationLevelApplier';
import { extractRunning } from './RunningExtractor';
import { applyRunning } from './RunningApplier';
import { extractStringSet } from './StringSetExtractor';
import { applyStringSet } from './StringSetApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyExperimentalPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyPresentationLevel(extractPresentationLevel(properties)));
  Object.assign(out, applyRunning(extractRunning(properties)));
  Object.assign(out, applyStringSet(extractStringSet(properties)));
  return out;
}
