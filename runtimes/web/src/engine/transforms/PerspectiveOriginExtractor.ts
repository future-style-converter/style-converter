// PerspectiveOriginExtractor.ts — uses shared positionAxis helper.
import { foldLast, positionAxis, type IRPropertyLike } from '../effects/_shared';
import type { PerspectiveOriginConfig } from './PerspectiveOriginConfig';
import { PERSPECTIVE_ORIGIN_PROPERTY_TYPE } from './PerspectiveOriginConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                         // expect {x,y}
  const o = data as Record<string, unknown>;
  const x = positionAxis(o.x);                                                     // horizontal
  const y = positionAxis(o.y);                                                     // vertical
  return x && y ? `${x} ${y}` : undefined;                                         // two-value CSS form
}

export function extractPerspectiveOrigin(properties: IRPropertyLike[]): PerspectiveOriginConfig {
  return { value: foldLast(properties, PERSPECTIVE_ORIGIN_PROPERTY_TYPE, parseOne) };
}
