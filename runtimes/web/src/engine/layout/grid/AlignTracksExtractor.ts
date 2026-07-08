// AlignTracksExtractor.ts — shares the parser body with JustifyTracks.

import { AlignTracksConfig, ALIGN_TRACKS_PROPERTY_TYPE } from './AlignTracksConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'multi' && Array.isArray(o.values)) {
    const parts = (o.values as unknown[]).filter((s): s is string => typeof s === 'string');
    return parts.length ? parts.join(' ') : undefined;
  }
  if (typeof o.type === 'string') return o.type;
  return undefined;
}

export function extractAlignTracks(properties: IRPropertyLike[]): AlignTracksConfig {
  return { value: foldLast(properties, ALIGN_TRACKS_PROPERTY_TYPE, parse) };
}
