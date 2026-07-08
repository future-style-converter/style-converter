// _dispatch.ts — Phase-10 rhythm long-tail dispatch (5 properties).
import type { CSSProperties } from 'react';
import { extractBlockStep } from './BlockStepExtractor';
import { applyBlockStep } from './BlockStepApplier';
import { extractBlockStepAlign } from './BlockStepAlignExtractor';
import { applyBlockStepAlign } from './BlockStepAlignApplier';
import { extractBlockStepInsert } from './BlockStepInsertExtractor';
import { applyBlockStepInsert } from './BlockStepInsertApplier';
import { extractBlockStepRound } from './BlockStepRoundExtractor';
import { applyBlockStepRound } from './BlockStepRoundApplier';
import { extractBlockStepSize } from './BlockStepSizeExtractor';
import { applyBlockStepSize } from './BlockStepSizeApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyRhythmPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyBlockStep(extractBlockStep(properties)));
  Object.assign(out, applyBlockStepAlign(extractBlockStepAlign(properties)));
  Object.assign(out, applyBlockStepInsert(extractBlockStepInsert(properties)));
  Object.assign(out, applyBlockStepRound(extractBlockStepRound(properties)));
  Object.assign(out, applyBlockStepSize(extractBlockStepSize(properties)));
  return out;
}
