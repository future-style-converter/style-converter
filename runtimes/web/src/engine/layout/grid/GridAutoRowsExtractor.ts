// GridAutoRowsExtractor.ts — IR shape mirrors GridAutoColumns exactly.

import { GridAutoRowsConfig, GRID_AUTO_ROWS_PROPERTY_TYPE } from './GridAutoRowsConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { renderTrackList } from './_grid_shared';

export function extractGridAutoRows(properties: IRPropertyLike[]): GridAutoRowsConfig {
  return { value: foldLast(properties, GRID_AUTO_ROWS_PROPERTY_TYPE, renderTrackList) };
}
