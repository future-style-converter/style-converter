// TransitionBehaviorExtractor.ts — singleton or list.
import { foldLast, type IRPropertyLike } from './_shared';
import { TRANSITION_BEHAVIOR_PROPERTY_TYPE, type TransitionBehaviorConfig } from './TransitionBehaviorConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'list' && Array.isArray(o.values)) {
    const parts = (o.values as unknown[]).filter((v): v is string => typeof v === 'string');
    return parts.length === 0 ? undefined : parts.join(', ');
  }
  if (typeof o.type === 'string') return o.type;
  return undefined;
}

export function extractTransitionBehavior(properties: IRPropertyLike[]): TransitionBehaviorConfig {
  return { value: foldLast(properties, TRANSITION_BEHAVIOR_PROPERTY_TYPE, parseOne) };
}
