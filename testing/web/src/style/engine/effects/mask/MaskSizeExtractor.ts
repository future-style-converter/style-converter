// MaskSizeExtractor.ts — IR {width,height} -> CSS two-token size value.
import { extractLength, toCssLength } from '../../core/types/LengthValue';          // length parser
import { foldLast, type IRPropertyLike } from '../_shared';
import type { MaskSizeConfig } from './MaskSizeConfig';
import { MASK_SIZE_PROPERTY_TYPE } from './MaskSizeConfig';

// Serialise one axis {type:'auto'|'cover'|'contain'|'length'} to CSS token.
function axis(raw: unknown): string | undefined {
  if (!raw || typeof raw !== 'object') return undefined;                            // require wrapper
  const o = raw as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';                                             // keyword
  if (o.type === 'cover') return 'cover';                                           // single-keyword form
  if (o.type === 'contain') return 'contain';
  if (o.type === 'length') {                                                         // length wrapper
    const v = extractLength(o);                                                      // shared length parser
    if (v.kind !== 'unknown') return toCssLength(v);                                 // '40px' / '50%' / …
  }
  return undefined;
}

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                          // expect {width,height}
  const o = data as Record<string, unknown>;
  const w = axis(o.width), h = axis(o.height);
  if (!w || !h) return undefined;                                                   // both required
  // Per spec: if both are the same single-keyword (cover/contain) emit just the
  // one keyword so serialisation matches browser's canonical form.
  if ((w === 'cover' && h === 'cover') || (w === 'contain' && h === 'contain')) return w;
  return `${w} ${h}`;                                                                // two-token form
}

export function extractMaskSize(properties: IRPropertyLike[]): MaskSizeConfig {
  return { value: foldLast(properties, MASK_SIZE_PROPERTY_TYPE, parseOne) };
}
