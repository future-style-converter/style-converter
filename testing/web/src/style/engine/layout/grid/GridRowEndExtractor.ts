// GridRowEndExtractor.ts — delegates to gridLine().

import { GridRowEndConfig, GRID_ROW_END_PROPERTY_TYPE } from './GridRowEndConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { gridLine } from './_grid_shared';

export function extractGridRowEnd(properties: IRPropertyLike[]): GridRowEndConfig {
  return { value: foldLast(properties, GRID_ROW_END_PROPERTY_TYPE, gridLine) };
}
