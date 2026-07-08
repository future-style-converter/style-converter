// GridAutoFlowExtractor.ts — handles both bare-enum and `{direction, dense}`.

import { GridAutoFlowConfig, GRID_AUTO_FLOW_PROPERTY_TYPE } from './GridAutoFlowConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (typeof data === 'string') return kebab(data);                                 // bare 'ROW' / 'COLUMN'
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    const dir = kebab(o.direction) ?? 'row';                                        // default per spec
    return o.dense ? `${dir} dense` : dir;                                          // CSS grammar
  }
  return undefined;                                                                 // unknown shape
}

export function extractGridAutoFlow(properties: IRPropertyLike[]): GridAutoFlowConfig {
  return { value: foldLast(properties, GRID_AUTO_FLOW_PROPERTY_TYPE, parse) };
}
