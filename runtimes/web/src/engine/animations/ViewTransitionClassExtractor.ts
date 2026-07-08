// ViewTransitionClassExtractor.ts — class list uses SPACE separator (spec),
// not comma, so we override the usual list-join here.
import { foldLast, type IRPropertyLike } from './_shared';
import { VIEW_TRANSITION_CLASS_PROPERTY_TYPE, type ViewTransitionClassConfig } from './ViewTransitionClassConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';
  if (o.type === 'classes' && Array.isArray(o.names)) {
    const parts = (o.names as unknown[]).filter((n): n is string => typeof n === 'string');
    return parts.length === 0 ? undefined : parts.join(' ');                    // space-separated per spec
  }
  return undefined;
}

export function extractViewTransitionClass(properties: IRPropertyLike[]): ViewTransitionClassConfig {
  return { value: foldLast(properties, VIEW_TRANSITION_CLASS_PROPERTY_TYPE, parseOne) };
}
