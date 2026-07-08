// _dispatch.ts — Phase-10 table long-tail dispatch (5 properties).
import type { CSSProperties } from 'react';
import { extractBorderCollapse } from './BorderCollapseExtractor';
import { applyBorderCollapse } from './BorderCollapseApplier';
import { extractBorderSpacing } from './BorderSpacingExtractor';
import { applyBorderSpacing } from './BorderSpacingApplier';
import { extractCaptionSide } from './CaptionSideExtractor';
import { applyCaptionSide } from './CaptionSideApplier';
import { extractEmptyCells } from './EmptyCellsExtractor';
import { applyEmptyCells } from './EmptyCellsApplier';
import { extractTableLayout } from './TableLayoutExtractor';
import { applyTableLayout } from './TableLayoutApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyTablePhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyBorderCollapse(extractBorderCollapse(properties)));
  Object.assign(out, applyBorderSpacing(extractBorderSpacing(properties)));
  Object.assign(out, applyCaptionSide(extractCaptionSide(properties)));
  Object.assign(out, applyEmptyCells(extractEmptyCells(properties)));
  Object.assign(out, applyTableLayout(extractTableLayout(properties)));
  return out;
}
