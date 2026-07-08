// AnimationRangeExtractor.ts — emits "<start>" or "<start> <end>".
import { foldLast, scalarOrLengthToCss, type IRPropertyLike } from './_shared';
import { ANIMATION_RANGE_PROPERTY_TYPE, type AnimationRangeConfig } from './AnimationRangeConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const start = scalarOrLengthToCss(o.start);                                   // mandatory
  if (start === undefined) return undefined;
  const end = scalarOrLengthToCss(o.end);                                       // optional
  return end === undefined ? start : `${start} ${end}`;
}

export function extractAnimationRange(properties: IRPropertyLike[]): AnimationRangeConfig {
  return { value: foldLast(properties, ANIMATION_RANGE_PROPERTY_TYPE, parseOne) };
}
