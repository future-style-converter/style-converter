// BaselineSourceConfig.ts — typed config for the CSS `baselineSource` property.
// Family: keyword.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/BaselineSourcePropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface BaselineSourceConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const BASELINE_SOURCE_PROPERTY_TYPE = 'BaselineSource' as const;
export type BaselineSourcePropertyType = typeof BASELINE_SOURCE_PROPERTY_TYPE;
