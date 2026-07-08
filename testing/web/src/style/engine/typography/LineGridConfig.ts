// LineGridConfig.ts — typed config for the CSS `lineGrid` property.
// Family: line-grid.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/LineGridPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface LineGridConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const LINE_GRID_PROPERTY_TYPE = 'LineGrid' as const;
export type LineGridPropertyType = typeof LINE_GRID_PROPERTY_TYPE;
