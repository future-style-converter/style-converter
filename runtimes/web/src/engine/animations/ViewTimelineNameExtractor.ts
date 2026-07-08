// ViewTimelineNameExtractor.ts — passthrough string.
import { foldLast, type IRPropertyLike } from './_shared';
import { VIEW_TIMELINE_NAME_PROPERTY_TYPE, type ViewTimelineNameConfig } from './ViewTimelineNameConfig';
export function extractViewTimelineName(properties: IRPropertyLike[]): ViewTimelineNameConfig {
  return {
    value: foldLast(properties, VIEW_TIMELINE_NAME_PROPERTY_TYPE, (d) =>
      typeof d === 'string' && d.length > 0 ? d : undefined),
  };
}
