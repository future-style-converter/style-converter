// CornerShapeConfig.ts — typed record for the `corner-shape` IR property.
// Mirrors src/main/kotlin/app/irmodels/properties/borders/CornerShapeProperty.kt.
// `corner-shape` is an experimental CSS Borders L4 feature (§corner-shaping)
// that lets border-radius describe shapes other than rounded corners
// (angle/notch/bevel/scoop/squircle).  Chromium flagged impl only (Apr 2026).

// IR enum values from the parser (UPPERCASE) — after canonicalisation
// they become the kebab-case CSS keywords.
export type CornerShapeValue =
  | 'round' | 'angle' | 'notch' | 'bevel' | 'scoop' | 'squircle';          // CSS Borders L4 enum

// Single-field config — absent when the property isn't set.
export interface CornerShapeConfig {
  value?: CornerShapeValue;                                                // validated keyword
}

// IR property type string — used by extractor + registry.
export const CORNER_SHAPE_PROPERTY_TYPE = 'CornerShape' as const;
export type CornerShapePropertyType = typeof CORNER_SHAPE_PROPERTY_TYPE;
