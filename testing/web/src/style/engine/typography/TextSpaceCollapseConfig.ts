// TextSpaceCollapseConfig.ts — typed config for the CSS `textSpaceCollapse` property.
// Family: keyword.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/TextSpaceCollapsePropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface TextSpaceCollapseConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const TEXT_SPACE_COLLAPSE_PROPERTY_TYPE = 'TextSpaceCollapse' as const;
export type TextSpaceCollapsePropertyType = typeof TEXT_SPACE_COLLAPSE_PROPERTY_TYPE;
