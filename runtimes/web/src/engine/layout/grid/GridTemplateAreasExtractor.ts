// GridTemplateAreasExtractor.ts — renders quoted area rows per CSS grammar.

import { GridTemplateAreasConfig, GRID_TEMPLATE_AREAS_PROPERTY_TYPE } from './GridTemplateAreasConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                        // must be typed object
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                           // explicit none
  if (o.type !== 'areas' || !Array.isArray(o.rows)) return undefined;             // unknown → drop
  // Each row becomes a quoted, space-separated string; CSS requires the quotes.
  const rows = (o.rows as unknown[][]).map((row) => {
    const cells = row.filter((c): c is string => typeof c === 'string');          // defensive
    return cells.length ? `"${cells.join(' ')}"` : '';                            // empty row skipped
  }).filter((s) => s.length > 0);
  return rows.length ? rows.join(' ') : undefined;                                // concat rows
}

export function extractGridTemplateAreas(properties: IRPropertyLike[]): GridTemplateAreasConfig {
  return { value: foldLast(properties, GRID_TEMPLATE_AREAS_PROPERTY_TYPE, parse) };
}
