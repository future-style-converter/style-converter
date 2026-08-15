// LineClampConfig.ts — typed config for the CSS `lineClamp` property.
// Family: line-clamp.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/LineClampPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface LineClampConfig {
  value?: string | number;                                           // serialised CSS value
  // wave-37 lane W2 — `line-clamp: auto` (css-overflow-4 §5.1) clamps at the
  // element's own block-size constraint, so the LINE COUNT is a used value the
  // IR cannot carry (`{"type":"auto"}` has no count by design).  The extractor
  // resolves it from the component's sibling max-height/height + line-height
  // and records the result here; `auto` stays true even when the count could
  // not be resolved, so the applier can still clip honestly rather than
  // pretending the declaration was absent.
  //
  // wave-42 lane W7 — `auto` is more precisely the GEOMETRY-CLAMP PATH
  // selector: it is also set for a fixed count whose <'block-ellipsis'>
  // component suppresses the marker (`4 no-ellipsis` / `4 ""`,
  // css-overflow-4 §5.1 two-value grammar), because that path clamps at
  // N·lh + hidden overflow WITHOUT the -webkit-box trio's undisableable
  // "…" — see the extractor's suppressesMarker note.
  auto?: boolean;                                                   // geometry-clamp path: `auto` declared, or count with suppressed marker
  autoLines?: number;                                                // resolved clamp count (>= 1) or absent
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const LINE_CLAMP_PROPERTY_TYPE = 'LineClamp' as const;
export type LineClampPropertyType = typeof LINE_CLAMP_PROPERTY_TYPE;
