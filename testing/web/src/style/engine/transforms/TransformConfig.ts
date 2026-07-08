// TransformConfig.ts — typed config for the CSS `transform` property.
// CSS Transforms L2 — https://developer.mozilla.org/docs/Web/CSS/transform.
// IR shape (from parsing/css/properties/longhands/transforms/TransformPropertyParser.kt):
//   { type: 'functions', list: [ {fn:'translate', x:{px}, y:{px}}, ... ] }
//   { type: 'none' }                        -> CSS `transform:none`
//   'calc(...)'                             -> raw expression passthrough
// Each function shape is enumerated in TransformExtractor.ts (one per CSS
// transform-function keyword: translate, rotate, scale, skew, matrix, ...).

export interface TransformConfig {
  // Pre-serialised CSS value, e.g. "translate(20px, 0) rotate(45deg)".
  // `undefined` means the property is absent from the IR and the applier
  // should emit nothing (so downstream cascade can still inherit).
  value?: string;
}

// IR property type string — single source of truth for extractor + registry.
export const TRANSFORM_PROPERTY_TYPE = 'Transform' as const;
export type TransformPropertyType = typeof TRANSFORM_PROPERTY_TYPE;
