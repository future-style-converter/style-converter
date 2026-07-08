// InsetAreaExtractor.ts — single or combined tokens become one CSS string.

import { InsetAreaConfig, INSET_AREA_PROPERTY_TYPE } from './InsetAreaConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'single' && typeof o.value === 'string') return o.value;           // single keyword
  if (o.type === 'combined' && typeof o.first === 'string' && typeof o.second === 'string') {
    return `${o.first} ${o.second}`;                                                // two-value form
  }
  return undefined;                                                                 // unknown
}

export function extractInsetArea(properties: IRPropertyLike[]): InsetAreaConfig {
  return { value: foldLast(properties, INSET_AREA_PROPERTY_TYPE, parse) };
}
