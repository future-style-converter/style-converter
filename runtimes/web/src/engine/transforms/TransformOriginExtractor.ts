// TransformOriginExtractor.ts — IR -> TransformOriginConfig.
// Two- or three-axis positional value.  Uses the shared `positionAxis` helper
// that normalises keyword / percentage / length IR shapes into CSS tokens.

import { foldLast, positionAxis, type IRPropertyLike } from '../effects/_shared';
import { extractLength, toCssLength } from '../core/types/LengthValue';
import type { TransformOriginConfig } from './TransformOriginConfig';
import { TRANSFORM_ORIGIN_PROPERTY_TYPE } from './TransformOriginConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                         // expect {x,y,z?}
  const o = data as Record<string, unknown>;
  const x = positionAxis(o.x);                                                     // horizontal
  const y = positionAxis(o.y);                                                     // vertical
  if (!x || !y) return undefined;                                                  // CSS requires both
  if (o.z !== undefined) {                                                         // optional z (px only per spec)
    const zv = extractLength(o.z);                                                 // parser emits {px:N}
    if (zv.kind !== 'unknown') return `${x} ${y} ${toCssLength(zv)}`;              // 3-value form
  }
  return `${x} ${y}`;                                                               // 2-value form
}

export function extractTransformOrigin(properties: IRPropertyLike[]): TransformOriginConfig {
  return { value: foldLast(properties, TRANSFORM_ORIGIN_PROPERTY_TYPE, parseOne) };
}
