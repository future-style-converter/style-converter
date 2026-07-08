// AnimationIterationCountExtractor.ts — entries are either the literal string
// "infinite" or a plain number.  IRNumber serialises as bare number when the
// value has no unit, so fixtures show e.g. `[1.0, 3.0, 0.5]`.
import { foldLast, type IRPropertyLike } from './_shared';
import { ANIMATION_ITERATION_COUNT_PROPERTY_TYPE, type AnimationIterationCountConfig } from './AnimationIterationCountConfig';

function entryToCss(e: unknown): string | undefined {
  if (e === 'infinite') return 'infinite';                                      // keyword
  if (typeof e === 'number') return String(e);                                  // IRNumber unwrapped
  if (e && typeof e === 'object') {                                             // IRNumber object form
    const v = (e as Record<string, unknown>).value;
    if (typeof v === 'number') return String(v);
  }
  return undefined;
}

export function extractAnimationIterationCount(properties: IRPropertyLike[]): AnimationIterationCountConfig {
  const value = foldLast(properties, ANIMATION_ITERATION_COUNT_PROPERTY_TYPE, (data) => {
    if (!Array.isArray(data) || data.length === 0) return undefined;
    const parts = data.map(entryToCss).filter((s): s is string => !!s);
    return parts.length === 0 ? undefined : parts.join(', ');
  });
  return { value };
}
