// LineHeightStepConfig.ts — typed config for the CSS `lineHeightStep` property.
// Family: length-keyword.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/LineHeightStepPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface LineHeightStepConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const LINE_HEIGHT_STEP_PROPERTY_TYPE = 'LineHeightStep' as const;
export type LineHeightStepPropertyType = typeof LINE_HEIGHT_STEP_PROPERTY_TYPE;
