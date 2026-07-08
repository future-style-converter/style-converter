// GridColumnStartExtractor.ts — IR shape from /tmp/layout_ir/grid-column-start-end:
//   {type:'auto'} | {type:'number',number:N} | {type:'span',count:N} | {type:'name',name:'x'}

import { GridColumnStartConfig, GRID_COLUMN_START_PROPERTY_TYPE } from './GridColumnStartConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { gridLine } from './_grid_shared';

export function extractGridColumnStart(properties: IRPropertyLike[]): GridColumnStartConfig {
  return { value: foldLast(properties, GRID_COLUMN_START_PROPERTY_TYPE, gridLine) };
}
