// ScrollTimelineNameExtractor.ts — single string unwrap.
import { foldLast, type IRPropertyLike } from '../animations/_shared';
import { SCROLL_TIMELINE_NAME_PROPERTY_TYPE, type ScrollTimelineNameConfig } from './ScrollTimelineNameConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  return typeof o.name === 'string' && o.name.length > 0 ? o.name : undefined;
}

export function extractScrollTimelineName(properties: IRPropertyLike[]): ScrollTimelineNameConfig {
  return { value: foldLast(properties, SCROLL_TIMELINE_NAME_PROPERTY_TYPE, parseOne) };
}
