// PerspectiveExtractor.ts — IR -> PerspectiveConfig.
import { extractLength, toCssLength } from '../core/types/LengthValue';
import { foldLast, type IRPropertyLike } from '../effects/_shared';
import type { PerspectiveConfig } from './PerspectiveConfig';
import { PERSPECTIVE_PROPERTY_TYPE } from './PerspectiveConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                         // require wrapper
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                            // CSS keyword
  const v = extractLength(o);                                                      // shared length parser (sees {px,…})
  return v.kind === 'unknown' ? undefined : toCssLength(v);
}

export function extractPerspective(properties: IRPropertyLike[]): PerspectiveConfig {
  return { value: foldLast(properties, PERSPECTIVE_PROPERTY_TYPE, parseOne) };
}
