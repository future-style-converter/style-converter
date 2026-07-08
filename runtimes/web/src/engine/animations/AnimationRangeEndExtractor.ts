// AnimationRangeEndExtractor.ts — identical parse logic to AnimationRangeStart.
import { foldLast, scalarOrLengthToCss, type IRPropertyLike } from './_shared';
import { ANIMATION_RANGE_END_PROPERTY_TYPE, type AnimationRangeEndConfig } from './AnimationRangeEndConfig';

function parseOne(data: unknown): string | undefined {
  if (typeof data === 'string') return data;
  if (typeof data === 'number') return `${data}%`;
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.name === 'string' && typeof o.offset === 'number') return `${o.name} ${o.offset}%`;
    return scalarOrLengthToCss(data);
  }
  return undefined;
}

export function extractAnimationRangeEnd(properties: IRPropertyLike[]): AnimationRangeEndConfig {
  return { value: foldLast(properties, ANIMATION_RANGE_END_PROPERTY_TYPE, parseOne) };
}
