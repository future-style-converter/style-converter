// MaskCompositeExtractor.ts — FQCN {type} -> kebab keyword.
import { foldLast, type IRPropertyLike } from '../_shared';
import { tailKebab } from './_mask_shared';
import type { MaskCompositeConfig } from './MaskCompositeConfig';
import { MASK_COMPOSITE_PROPERTY_TYPE } from './MaskCompositeConfig';

function parseOne(data: unknown): string | undefined {
  if (typeof data === 'string') return tailKebab(data);                              // bare keyword
  if (data && typeof data === 'object') {
    const t = (data as Record<string, unknown>).type;
    if (typeof t === 'string') return tailKebab(t);                                  // FQCN variant
  }
  return undefined;
}

export function extractMaskComposite(properties: IRPropertyLike[]): MaskCompositeConfig {
  return { value: foldLast(properties, MASK_COMPOSITE_PROPERTY_TYPE, parseOne) };
}
