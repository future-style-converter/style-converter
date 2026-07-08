// GridTemplateRowsExtractor.ts — mirrors GridTemplateColumnsExtractor; the IR
// shape is identical (see GridTemplateRowsPropertyParser.kt — it delegates to
// the same track-list parser).

import { GridTemplateRowsConfig, GRID_TEMPLATE_ROWS_PROPERTY_TYPE } from './GridTemplateRowsConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { renderTrackList } from './_grid_shared';

export function extractGridTemplateRows(properties: IRPropertyLike[]): GridTemplateRowsConfig {
  return { value: foldLast(properties, GRID_TEMPLATE_ROWS_PROPERTY_TYPE, renderTrackList) };
}
