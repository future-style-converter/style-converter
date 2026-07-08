// AnimationCompositionApplier.ts — csstype-widened.
// `animation-composition` landed in Chromium 112 / Firefox 115; csstype may
// not ship the key yet, so we cast through Record<string,string>.  See
// https://developer.mozilla.org/docs/Web/CSS/animation-composition
import type { CSSProperties } from 'react';
import type { AnimationCompositionConfig } from './AnimationCompositionConfig';

export type AnimationCompositionStyles = Record<string, string>;

export function applyAnimationComposition(c: AnimationCompositionConfig): AnimationCompositionStyles {
  if (c.value === undefined) return {};
  // Widen: native key, but csstype's definition is incomplete — route through
  // Record<string,string> so the engine can still emit the declaration.
  return ({ animationComposition: c.value } as unknown as CSSProperties) as AnimationCompositionStyles;
}
