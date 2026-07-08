// AnimationTimingFunctionExtractor.ts — delegates to shared `timingFunctionListToCss`.
import { foldLast, timingFunctionListToCss, type IRPropertyLike } from './_shared';
import { ANIMATION_TIMING_FUNCTION_PROPERTY_TYPE, type AnimationTimingFunctionConfig } from './AnimationTimingFunctionConfig';
export function extractAnimationTimingFunction(properties: IRPropertyLike[]): AnimationTimingFunctionConfig {
  return { value: foldLast(properties, ANIMATION_TIMING_FUNCTION_PROPERTY_TYPE, timingFunctionListToCss) };
}
