// GridAutoColumnsExtractor.ts — folds IR into a CSS track list.

import { GridAutoColumnsConfig, GRID_AUTO_COLUMNS_PROPERTY_TYPE } from './GridAutoColumnsConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { renderTrackList } from './_grid_shared';

export function extractGridAutoColumns(properties: IRPropertyLike[]): GridAutoColumnsConfig {
  // renderTrackList handles {fr},{px},{min,max},keywords and the { expr } hatch.
  return { value: foldLast(properties, GRID_AUTO_COLUMNS_PROPERTY_TYPE, renderTrackList) };
}
