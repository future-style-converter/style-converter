// ViewTimelineExtractor.ts — emits "<name>" or "<name> <axis>".
import { foldLast, kebabEnum, type IRPropertyLike } from './_shared';
import { VIEW_TIMELINE_PROPERTY_TYPE, type ViewTimelineConfig } from './ViewTimelineConfig';

function parseOne(data: unknown): string | undefined {
  if (typeof data === 'string') return data;                                    // name-only
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const name = typeof o.name === 'string' ? o.name : undefined;
  if (!name) return undefined;
  const axis = kebabEnum(o.axis);                                               // SHOUTY -> kebab
  return axis ? `${name} ${axis}` : name;
}

export function extractViewTimeline(properties: IRPropertyLike[]): ViewTimelineConfig {
  return { value: foldLast(properties, VIEW_TIMELINE_PROPERTY_TYPE, parseOne) };
}
