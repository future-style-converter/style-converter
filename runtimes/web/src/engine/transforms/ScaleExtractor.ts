// ScaleExtractor.ts — IR -> ScaleConfig.
import { foldLast, type IRPropertyLike } from '../effects/_shared';
import type { ScaleConfig } from './ScaleConfig';
import { SCALE_PROPERTY_TYPE } from './ScaleConfig';

function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                       // absent
  if (typeof data === 'number') return String(data);                               // bare uniform scale
  if (typeof data !== 'object') return undefined;                                  // unknown
  const o = data as Record<string, unknown>;
  if (o.type === 'none')    return 'none';                                         // CSS keyword
  if (o.type === 'uniform') return typeof o.value === 'number' ? String(o.value) : undefined;
  if (o.type === '2d')      return `${o.x ?? 1} ${o.y ?? 1}`;                      // 2D 2-factor form
  if (o.type === '3d')      return `${o.x ?? 1} ${o.y ?? 1} ${o.z ?? 1}`;          // 3D 3-factor form
  return undefined;                                                                 // other shapes drop
}

export function extractScale(properties: IRPropertyLike[]): ScaleConfig {
  return { value: foldLast(properties, SCALE_PROPERTY_TYPE, parseOne) };
}
