// TranslateExtractor.ts — IR -> TranslateConfig.
import { extractLength, toCssLength } from '../core/types/LengthValue';            // shared length
import { foldLast, type IRPropertyLike } from '../effects/_shared';
import type { TranslateConfig } from './TranslateConfig';
import { TRANSLATE_PROPERTY_TYPE } from './TranslateConfig';

// Convert one translate-axis IR shape to a CSS length token.  Accepts the inner
// {type:'length', px}, {type:'percentage', percentage}, or bare {px}/{pct}.
function axis(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) return undefined;                         // absent axis
  if (typeof raw === 'object') {                                                   // typed wrapper path
    const o = raw as Record<string, unknown>;
    if (o.type === 'percentage' && typeof o.percentage === 'number') return `${o.percentage}%`;
    if (o.type === 'length' && typeof o.px === 'number') return `${o.px}px`;
  }
  const v = extractLength(raw);                                                    // shared fallback
  return v.kind === 'unknown' ? undefined : toCssLength(v);
}

function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                       // absent
  if (typeof data !== 'object') return undefined;                                  // unknown
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                            // CSS keyword
  if (o.type === 'length' && o.length) return axis(o.length);                      // single X length
  if (o.type === 'percentage' && typeof o.percentage === 'number') return `${o.percentage}%`;
  if (o.type === '2d') {                                                           // 2 axes
    const x = axis(o.x), y = axis(o.y);
    return x && y ? `${x} ${y}` : undefined;
  }
  if (o.type === '3d') {                                                           // 3 axes (z is plain length)
    const x = axis(o.x), y = axis(o.y), z = axis(o.z);
    return x && y && z ? `${x} ${y} ${z}` : undefined;
  }
  return undefined;
}

export function extractTranslate(properties: IRPropertyLike[]): TranslateConfig {
  return { value: foldLast(properties, TRANSLATE_PROPERTY_TYPE, parseOne) };
}
