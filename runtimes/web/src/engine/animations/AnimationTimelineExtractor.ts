// AnimationTimelineExtractor.ts — emits the full scroll(...)/view(...) L2 syntax.
import { foldLast, type IRPropertyLike } from './_shared';
import { ANIMATION_TIMELINE_PROPERTY_TYPE, type AnimationTimelineConfig } from './AnimationTimelineConfig';

// Inner helper — builds the scroll(...) function arg list.  Per spec order:
// `<scroller> <axis>` with any missing piece omitted.
function scrollArgs(o: Record<string, unknown>): string {
  const tokens: string[] = [];
  if (typeof o.scroller === 'string') tokens.push(o.scroller);                  // `root` | `nearest` | `self`
  if (typeof o.axis === 'string') tokens.push(o.axis);                          // block | inline | x | y
  return tokens.join(' ');
}

// Inner helper — builds the view(...) function arg list: `<axis> <inset-start> <inset-end>`.
function viewArgs(o: Record<string, unknown>): string {
  const tokens: string[] = [];
  if (typeof o.axis === 'string') tokens.push(o.axis);                          // axis
  if (typeof o.insetStart === 'string') tokens.push(o.insetStart);              // parser pre-stringified
  if (typeof o.insetEnd === 'string') tokens.push(o.insetEnd);
  return tokens.join(' ');
}

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  switch (o.type) {
    case 'auto': case 'none': return o.type as string;                          // bare keyword
    case 'named': return typeof o.name === 'string' ? o.name : undefined;       // custom-ident
    case 'scroll': {                                                            // scroll(...)
      const inner = scrollArgs(o);
      return inner ? `scroll(${inner})` : 'scroll()';
    }
    case 'view': {                                                              // view(...)
      const inner = viewArgs(o);
      return inner ? `view(${inner})` : 'view()';
    }
    default: return undefined;
  }
}

export function extractAnimationTimeline(properties: IRPropertyLike[]): AnimationTimelineConfig {
  return { value: foldLast(properties, ANIMATION_TIMELINE_PROPERTY_TYPE, parseOne) };
}
