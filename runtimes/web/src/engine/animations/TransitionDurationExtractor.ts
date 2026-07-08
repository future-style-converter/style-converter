// TransitionDurationExtractor.ts — time-list.
import { foldLast, timeListToCss, type IRPropertyLike } from './_shared';
import { TRANSITION_DURATION_PROPERTY_TYPE, type TransitionDurationConfig } from './TransitionDurationConfig';
export function extractTransitionDuration(properties: IRPropertyLike[]): TransitionDurationConfig {
  return { value: foldLast(properties, TRANSITION_DURATION_PROPERTY_TYPE, timeListToCss) };
}
