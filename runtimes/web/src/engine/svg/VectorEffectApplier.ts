// VectorEffectApplier.ts — emits { vectorEffect }.  MDN: vector-effect.
import type { CSSProperties } from 'react';
import type { VectorEffectConfig } from './VectorEffectConfig';
export function applyVectorEffect(c: VectorEffectConfig): CSSProperties {
  return c.value === undefined ? {} : { vectorEffect: c.value } as CSSProperties;
}
