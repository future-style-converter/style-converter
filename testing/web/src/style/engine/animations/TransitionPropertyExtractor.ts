// TransitionPropertyExtractor.ts — emits comma-joined property names.
import { foldLast, type IRPropertyLike } from './_shared';
import { TRANSITION_PROPERTY_PROPERTY_TYPE, type TransitionPropertyConfig } from './TransitionPropertyConfig';

function entryToCss(e: unknown): string | undefined {
  if (!e || typeof e !== 'object') return undefined;
  const o = e as Record<string, unknown>;
  if (o.type === 'all') return 'all';                                           // CSS keyword
  if (o.type === 'none') return 'none';
  if (o.type === 'property-name' && typeof o.name === 'string') return o.name;  // ident (already lowercased)
  return undefined;
}

export function extractTransitionProperty(properties: IRPropertyLike[]): TransitionPropertyConfig {
  const value = foldLast(properties, TRANSITION_PROPERTY_PROPERTY_TYPE, (data) => {
    if (!Array.isArray(data) || data.length === 0) return undefined;
    const parts = data.map(entryToCss).filter((s): s is string => !!s);
    return parts.length === 0 ? undefined : parts.join(', ');
  });
  return { value };
}
