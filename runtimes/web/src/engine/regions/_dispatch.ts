// _dispatch.ts — Phase-10 regions long-tail dispatch (10 properties).
import type { CSSProperties } from 'react';
import { extractFlowInto } from './FlowIntoExtractor';
import { applyFlowInto } from './FlowIntoApplier';
import { extractFlowFrom } from './FlowFromExtractor';
import { applyFlowFrom } from './FlowFromApplier';
import { extractRegionFragment } from './RegionFragmentExtractor';
import { applyRegionFragment } from './RegionFragmentApplier';
import { extractContinue } from './ContinueExtractor';
import { applyContinue } from './ContinueApplier';
import { extractCopyInto } from './CopyIntoExtractor';
import { applyCopyInto } from './CopyIntoApplier';
import { extractWrapFlow } from './WrapFlowExtractor';
import { applyWrapFlow } from './WrapFlowApplier';
import { extractWrapThrough } from './WrapThroughExtractor';
import { applyWrapThrough } from './WrapThroughApplier';
import { extractWrapBefore } from './WrapBeforeExtractor';
import { applyWrapBefore } from './WrapBeforeApplier';
import { extractWrapAfter } from './WrapAfterExtractor';
import { applyWrapAfter } from './WrapAfterApplier';
import { extractWrapInside } from './WrapInsideExtractor';
import { applyWrapInside } from './WrapInsideApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyRegionsPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyFlowInto(extractFlowInto(properties)));
  Object.assign(out, applyFlowFrom(extractFlowFrom(properties)));
  Object.assign(out, applyRegionFragment(extractRegionFragment(properties)));
  Object.assign(out, applyContinue(extractContinue(properties)));
  Object.assign(out, applyCopyInto(extractCopyInto(properties)));
  Object.assign(out, applyWrapFlow(extractWrapFlow(properties)));
  Object.assign(out, applyWrapThrough(extractWrapThrough(properties)));
  Object.assign(out, applyWrapBefore(extractWrapBefore(properties)));
  Object.assign(out, applyWrapAfter(extractWrapAfter(properties)));
  Object.assign(out, applyWrapInside(extractWrapInside(properties)));
  return out;
}
