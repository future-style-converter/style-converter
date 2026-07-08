// MaskModeExtractor.ts — parses IR {type:'<FQCN>.<Variant>'} -> CSS keyword.
import { foldLast, type IRPropertyLike } from '../_shared';
import { tailKebab } from './_mask_shared';
import type { MaskModeConfig } from './MaskModeConfig';
import { MASK_MODE_PROPERTY_TYPE } from './MaskModeConfig';

// Parse one FQCN-wrapped MaskMode payload.  Accept both plain keyword strings
// and the `{type:'…Alpha'}` wrapper emitted by the IR.
function parseOne(data: unknown): string | undefined {
  if (typeof data === 'string') return tailKebab(data);                              // bare string
  if (data && typeof data === 'object') {
    const t = (data as Record<string, unknown>).type;                                // discriminator
    if (typeof t === 'string') return tailKebab(t);                                  // FQCN.Variant
  }
  return undefined;
}

export function extractMaskMode(properties: IRPropertyLike[]): MaskModeConfig {
  return { value: foldLast(properties, MASK_MODE_PROPERTY_TYPE, parseOne) };
}
