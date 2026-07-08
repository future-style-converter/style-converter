// TransitionDelayExtractor.ts — time-list.
import { foldLast, timeListToCss, type IRPropertyLike } from './_shared';
import { TRANSITION_DELAY_PROPERTY_TYPE, type TransitionDelayConfig } from './TransitionDelayConfig';
export function extractTransitionDelay(properties: IRPropertyLike[]): TransitionDelayConfig {
  return { value: foldLast(properties, TRANSITION_DELAY_PROPERTY_TYPE, timeListToCss) };
}
