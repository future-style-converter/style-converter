// GapConfig.ts — flex / grid gap sizing (row + column axes).
// The Kotlin shorthand expander converts `gap: X Y` into RowGap + ColumnGap,
// so we never see a raw `Gap` IR property at this layer.  We still accept it
// defensively though, mapping it onto both axes.

import type { LengthValue } from '../core/types/LengthValue';

// Two independent axes, each a LengthValue (auto not permitted by CSS for gap,
// but the union still includes it — applier filters such invalid values).
export interface GapConfig {
  rowGap?: LengthValue;
  columnGap?: LengthValue;
}

// IR property-type strings this module recognises.
export const GAP_PROPERTY_TYPES = ['Gap', 'RowGap', 'ColumnGap'] as const;

// Tuple literal type — used for exhaustive dispatch in the extractor.
export type GapPropertyType = (typeof GAP_PROPERTY_TYPES)[number];
