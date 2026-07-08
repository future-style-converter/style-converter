// RotateExtractor.ts — IR -> RotateConfig.
// Covers the three IR shapes enumerated in RotateConfig.ts.

import { extractAngle } from '../core/types/AngleValue';                           // deg normalisation
import { foldLast, type IRPropertyLike } from '../effects/_shared';
import type { RotateConfig } from './RotateConfig';
import { ROTATE_PROPERTY_TYPE } from './RotateConfig';

function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                       // absent
  if (typeof data !== 'object') return undefined;                                  // bare string not produced by parser
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                            // CSS keyword
  if (o.type === 'angle') {                                                        // 2D rotate
    const a = extractAngle(o);                                                     // reads deg / original
    return a ? `${a.degrees}deg` : undefined;                                      // require a valid angle
  }
  if (o.type === 'axis-angle') {                                                   // 3D axis + angle
    const a = extractAngle(o.angle);                                               // nested angle wrapper
    if (!a) return undefined;                                                       // skip if angle unreadable
    return `${o.x ?? 0} ${o.y ?? 0} ${o.z ?? 0} ${a.degrees}deg`;                  // CSS spec syntax
  }
  return undefined;                                                                 // unrecognised
}

export function extractRotate(properties: IRPropertyLike[]): RotateConfig {
  return { value: foldLast(properties, ROTATE_PROPERTY_TYPE, parseOne) };
}
