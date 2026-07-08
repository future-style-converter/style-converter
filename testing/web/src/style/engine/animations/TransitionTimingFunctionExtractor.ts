// TransitionTimingFunctionExtractor.ts — reuses the shared serialiser.
import { foldLast, timingFunctionListToCss, type IRPropertyLike } from './_shared';
import { TRANSITION_TIMING_FUNCTION_PROPERTY_TYPE, type TransitionTimingFunctionConfig } from './TransitionTimingFunctionConfig';
export function extractTransitionTimingFunction(properties: IRPropertyLike[]): TransitionTimingFunctionConfig {
  return { value: foldLast(properties, TRANSITION_TIMING_FUNCTION_PROPERTY_TYPE, timingFunctionListToCss) };
}
