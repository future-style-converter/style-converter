// AnimationNameExtractor.ts — IR -> AnimationNameConfig.
// Mirrors AnimationNamePropertyParser.kt: entries are either {type:'none'}
// or {type:'identifier', name}.  The parser lowercases ident strings, so no
// further case-folding happens here.
import { foldLast, type IRPropertyLike } from './_shared';
import { ANIMATION_NAME_PROPERTY_TYPE, type AnimationNameConfig } from './AnimationNameConfig';

// One entry -> CSS identifier or 'none' keyword.
function entryToCss(e: unknown): string | undefined {
  if (!e || typeof e !== 'object') return undefined;                            // defensive
  const o = e as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                         // explicit keyword
  if (o.type === 'identifier' && typeof o.name === 'string') return o.name;     // ident (dashed or plain)
  return undefined;
}

export function extractAnimationName(properties: IRPropertyLike[]): AnimationNameConfig {
  // IR shape: `data` is the raw List<AnimationName>.  Last-write-wins cascade.
  const value = foldLast(properties, ANIMATION_NAME_PROPERTY_TYPE, (data) => {
    if (!Array.isArray(data) || data.length === 0) return undefined;
    const parts = data.map(entryToCss).filter((s): s is string => !!s);
    return parts.length === 0 ? undefined : parts.join(', ');
  });
  return { value };
}
