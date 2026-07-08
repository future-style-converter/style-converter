// MaskBorderSourceExtractor.ts — bare string => url() / none.
import { foldLast, type IRPropertyLike } from '../_shared';
import type { MaskBorderSourceConfig } from './MaskBorderSourceConfig';
import { MASK_BORDER_SOURCE_PROPERTY_TYPE } from './MaskBorderSourceConfig';

function parseOne(data: unknown): string | undefined {
  if (typeof data !== 'string') return undefined;                                    // unknown shape
  if (data.length === 0 || data === 'none') return 'none';                           // empty ↔ none
  return `url("${data}")`;                                                            // wrap raw URL
}

export function extractMaskBorderSource(properties: IRPropertyLike[]): MaskBorderSourceConfig {
  return { value: foldLast(properties, MASK_BORDER_SOURCE_PROPERTY_TYPE, parseOne) };
}
