// ZoomConfig.ts — the typed value of the CSS `zoom` property.
//
// Spec: css-viewport-1 §"The zoom property"
// (https://drafts.csswg.org/css-viewport/#zoom-property). `zoom` is no
// longer the legacy IE/WebKit extension it used to be — it is a
// standardised property that multiplies the element's *used* values
// (lengths, spacing, stroke widths, the effective zoom inherited by
// descendants), and Chromium/WebKit ship it. Unlike `transform: scale()`
// it also scales the layout slot the element occupies.
//
// `value` is the CSS token to hand to the declaration, already in the
// exact spelling the property grammar accepts:
//   `<number>`      → "2", "0.75"
//   `<percentage>`  → "150%"
//   `normal`        → "normal" (equivalent to 1)
//   `reset`         → "reset"  (the legacy non-standard keyword; kept
//                    verbatim so a UA that still honours it can, and any
//                    UA that does not simply drops an invalid
//                    declaration — see ZoomExtractor for why we do not
//                    silently rewrite it)
// `undefined` = no Zoom property in the IR, so nothing is emitted.
export interface ZoomConfig { value?: string }
