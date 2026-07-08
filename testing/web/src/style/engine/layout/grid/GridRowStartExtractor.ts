// GridRowStartExtractor.ts — delegates to gridLine() (same shape as columns).

import { GridRowStartConfig, GRID_ROW_START_PROPERTY_TYPE } from './GridRowStartConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { gridLine } from './_grid_shared';

export function extractGridRowStart(properties: IRPropertyLike[]): GridRowStartConfig {
  return { value: foldLast(properties, GRID_ROW_START_PROPERTY_TYPE, gridLine) };
}
