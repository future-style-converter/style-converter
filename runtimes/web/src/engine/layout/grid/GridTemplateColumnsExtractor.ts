// GridTemplateColumnsExtractor.ts — folds IR `GridTemplateColumns` into a
// pre-rendered CSS string using the shared renderTrackList helper.

import { GridTemplateColumnsConfig, GRID_TEMPLATE_COLUMNS_PROPERTY_TYPE } from './GridTemplateColumnsConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { renderTrackList } from './_grid_shared';

export function extractGridTemplateColumns(properties: IRPropertyLike[]): GridTemplateColumnsConfig {
  // renderTrackList handles both the array form (every-variant fixture) and
  // the { expr } escape hatch for minmax/repeat complex expressions.
  return { value: foldLast(properties, GRID_TEMPLATE_COLUMNS_PROPERTY_TYPE, renderTrackList) };
}
