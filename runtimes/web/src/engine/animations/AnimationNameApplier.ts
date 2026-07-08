// AnimationNameApplier.ts — emits the native `animation-name` declaration.
import type { CSSProperties } from 'react';
import type { AnimationNameConfig } from './AnimationNameConfig';

export type AnimationNameStyles = Pick<CSSProperties, 'animationName'>;

export function applyAnimationName(config: AnimationNameConfig): AnimationNameStyles {
  if (config.value === undefined) return {};
  return { animationName: config.value };
}
