// AnimationCompositionExtractor.ts — singleton or list of lowercase idents.
import { foldLast, type IRPropertyLike } from './_shared';
import { ANIMATION_COMPOSITION_PROPERTY_TYPE, type AnimationCompositionConfig } from './AnimationCompositionConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'list' && Array.isArray(o.values)) {                           // multi-value list
    const parts = (o.values as unknown[]).filter((v): v is string => typeof v === 'string');
    return parts.length === 0 ? undefined : parts.join(', ');
  }
  if (typeof o.type === 'string') return o.type;                                // singleton keyword
  return undefined;
}

export function extractAnimationComposition(properties: IRPropertyLike[]): AnimationCompositionConfig {
  return { value: foldLast(properties, ANIMATION_COMPOSITION_PROPERTY_TYPE, parseOne) };
}
