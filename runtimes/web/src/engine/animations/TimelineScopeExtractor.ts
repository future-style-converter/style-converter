// TimelineScopeExtractor.ts — names preserve case (parser docstring).
import { foldLast, type IRPropertyLike } from './_shared';
import { TIMELINE_SCOPE_PROPERTY_TYPE, type TimelineScopeConfig } from './TimelineScopeConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'none' || o.type === 'all') return o.type;                     // keyword
  if (o.type === 'names' && Array.isArray(o.names)) {                           // ident list
    const parts = (o.names as unknown[]).filter((n): n is string => typeof n === 'string');
    return parts.length === 0 ? undefined : parts.join(', ');
  }
  return undefined;
}

export function extractTimelineScope(properties: IRPropertyLike[]): TimelineScopeConfig {
  return { value: foldLast(properties, TIMELINE_SCOPE_PROPERTY_TYPE, parseOne) };
}
