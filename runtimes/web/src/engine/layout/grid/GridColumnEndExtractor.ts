// GridColumnEndExtractor.ts — delegates to gridLine().

import { GridColumnEndConfig, GRID_COLUMN_END_PROPERTY_TYPE } from './GridColumnEndConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { gridLine } from './_grid_shared';

export function extractGridColumnEnd(properties: IRPropertyLike[]): GridColumnEndConfig {
  return { value: foldLast(properties, GRID_COLUMN_END_PROPERTY_TYPE, gridLine) };
}
