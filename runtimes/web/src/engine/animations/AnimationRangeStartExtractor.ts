// AnimationRangeStartExtractor.ts — covers plain scalars + named-phase pairs.
import { foldLast, scalarOrLengthToCss, type IRPropertyLike } from './_shared';
import { ANIMATION_RANGE_START_PROPERTY_TYPE, type AnimationRangeStartConfig } from './AnimationRangeStartConfig';

function parseOne(data: unknown): string | undefined {
  if (typeof data === 'string') return data;                                    // 'normal' / 'cover' / ...
  if (typeof data === 'number') return `${data}%`;                              // percent fallback
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.name === 'string' && typeof o.offset === 'number') {           // named-phase <offset>
      return `${o.name} ${o.offset}%`;
    }
    return scalarOrLengthToCss(data);                                           // {px} / {percentage}
  }
  return undefined;
}

export function extractAnimationRangeStart(properties: IRPropertyLike[]): AnimationRangeStartConfig {
  return { value: foldLast(properties, ANIMATION_RANGE_START_PROPERTY_TYPE, parseOne) };
}
