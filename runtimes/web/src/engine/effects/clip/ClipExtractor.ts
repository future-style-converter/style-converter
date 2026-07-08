// ClipExtractor.ts — IR -> ClipConfig.
import { extractLength, toCssLength } from '../../core/types/LengthValue';
import { foldLast, type IRPropertyLike } from '../_shared';
import type { ClipConfig } from './ClipConfig';
import { CLIP_PROPERTY_TYPE } from './ClipConfig';

function len(raw: unknown): string {
  if (raw === 'auto') return 'auto';                                                 // rect(auto ...) per spec
  const v = extractLength(raw);                                                      // shared
  return v.kind === 'unknown' ? '0' : toCssLength(v);
}

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                           // absent/unknown
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';                                              // keyword
  if (o.type === 'rect') {                                                           // rect(T,R,B,L)
    return `rect(${len(o.top)}, ${len(o.right)}, ${len(o.bottom)}, ${len(o.left)})`; // comma per CSS 2.1
  }
  return undefined;
}

export function extractClip(properties: IRPropertyLike[]): ClipConfig {
  return { value: foldLast(properties, CLIP_PROPERTY_TYPE, parseOne) };
}
