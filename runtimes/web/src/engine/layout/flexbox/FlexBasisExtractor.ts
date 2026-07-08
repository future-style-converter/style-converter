// FlexBasisExtractor.ts — see /tmp/layout_ir/flex-grow-shrink-basis for every
// observed IR shape: bare strings ('auto','content'), bare numbers (percentage),
// { value:{px:N}, normalizedPixels:N }, { expr:'calc(…)' }.
// The layoutLength helper already handles those — this extractor is a thin
// wrapper that also accepts the {value:{px:N}} envelope specific to FlexBasis.

import { FlexBasisConfig, FLEX_BASIS_PROPERTY_TYPE } from './FlexBasisConfig';
import { foldLast, layoutLength, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  // FlexBasis sometimes wraps its LengthValue inside { value:{px:N}, normalizedPixels:N }.
  // Unwrap once then delegate to the shared length helper.
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (o.value && typeof o.value === 'object' && !Array.isArray(o.value)) {
      const inner = o.value as Record<string, unknown>;
      // Only unwrap if the inner object doesn't look like an enum payload.
      if (typeof inner.px === 'number') return layoutLength(inner);               // length envelope
    }
  }
  return layoutLength(data);                                                      // bare keyword / number / expr
}

export function extractFlexBasis(properties: IRPropertyLike[]): FlexBasisConfig {
  return { value: foldLast(properties, FLEX_BASIS_PROPERTY_TYPE, parse) };
}
