// BorderBoundaryConfig.ts — typed record for the `border-boundary` IR property.
// Mirrors src/main/kotlin/app/irmodels/properties/borders/BorderBoundaryProperty.kt
// (enum BorderBoundaryValue: NONE | PARENT | DISPLAY).
//
// `border-boundary` is a CSS Borders L4 property (§border-boundary) that
// constrains where borders are painted relative to the element's ancestor
// chain.  NO browser has shipped it yet (2026-04).  We include it for
// IR-coverage completeness; the Applier emits the declaration and lets the
// browser silently drop it if unsupported — matches the general
// forward-compatible CSS policy.

// Single-field config — `value` absent when the property isn't set.
export interface BorderBoundaryConfig {
  value?: 'none' | 'parent' | 'display';                                   // CSS enum, lowercase
}

// IR property type string — used by both extractor + registry.
export const BORDER_BOUNDARY_PROPERTY_TYPE = 'BorderBoundary' as const;
export type BorderBoundaryPropertyType = typeof BORDER_BOUNDARY_PROPERTY_TYPE;
