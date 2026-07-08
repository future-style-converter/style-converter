// JustifyTracksExtractor.ts — parses single and multi variants.

import { JustifyTracksConfig, JUSTIFY_TRACKS_PROPERTY_TYPE } from './JustifyTracksConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'multi' && Array.isArray(o.values)) {
    // Multi-value space-separated list; drop non-strings defensively.
    const parts = (o.values as unknown[]).filter((s): s is string => typeof s === 'string');
    return parts.length ? parts.join(' ') : undefined;                              // empty = drop
  }
  if (typeof o.type === 'string') return o.type;                                    // already kebab per parser
  return undefined;
}

export function extractJustifyTracks(properties: IRPropertyLike[]): JustifyTracksConfig {
  return { value: foldLast(properties, JUSTIFY_TRACKS_PROPERTY_TYPE, parse) };
}
