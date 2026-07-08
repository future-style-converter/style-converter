// SizeConfig.ts — typed record holding every sizing value a component sets.
// One shared config carries both the physical CSS axes (width/height/min-*/max-*)
// and the logical counterparts (block-size / inline-size / min-* / max-*),
// because modern browsers accept the logical longhands directly and the CSS
// engine resolves them against writing-mode/direction at layout time.
//
// AspectRatio lives here too so a single extract/apply pair covers every
// sizing property the Phase-3 migration owns.

// Reuse the Phase-1 discriminated-union so we speak one length alphabet across
// the entire engine (exact px, relative unit, auto, none, intrinsic, calc, ...).
import type { LengthValue } from '../core/types/LengthValue';
// AspectRatio has its own (ratio, isAuto) shape — kept separate to avoid
// polluting LengthValue.
import type { AspectRatioValue } from './AspectRatioValue';

// Every field is optional — the extractor only populates keys whose IR
// property actually appeared in the component's properties array.
export interface SizeConfig {
  // Physical sizing — width / height longhands from IR.
  width?: LengthValue;
  height?: LengthValue;
  minWidth?: LengthValue;
  maxWidth?: LengthValue;
  minHeight?: LengthValue;
  maxHeight?: LengthValue;
  // Logical sizing — block-size / inline-size longhands.  These map to the
  // `blockSize` / `inlineSize` CSS camelCase keys directly; CSS resolves
  // against the current writing-mode.
  blockSize?: LengthValue;
  inlineSize?: LengthValue;
  minBlockSize?: LengthValue;
  maxBlockSize?: LengthValue;
  minInlineSize?: LengthValue;
  maxInlineSize?: LengthValue;
  // Aspect ratio — `null` / missing means "don't emit".  An `isAuto:true`
  // value round-trips to the CSS keyword `auto`.
  aspectRatio?: AspectRatioValue;
}

// IR property-type strings the sizing pipeline owns.  Used both by the
// renderer (to gate legacy vs engine path) and by the extractor's switch.
export const SIZE_PROPERTY_TYPES = [
  // Physical sizing (from irmodels/properties/spacing/).
  'Width', 'Height',
  'MinWidth', 'MaxWidth', 'MinHeight', 'MaxHeight',
  // Logical sizing (from irmodels/properties/sizing/).
  'BlockSize', 'InlineSize',
  'MinBlockSize', 'MaxBlockSize', 'MinInlineSize', 'MaxInlineSize',
  // AspectRatio — lives alongside sizing; shape handled by AspectRatioValue.
  'AspectRatio',
] as const;

// Tuple literal → union type — lets the extractor's switch be exhaustive.
export type SizePropertyType = (typeof SIZE_PROPERTY_TYPES)[number];
