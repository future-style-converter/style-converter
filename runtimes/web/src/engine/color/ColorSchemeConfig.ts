// ColorSchemeConfig.ts — https://developer.mozilla.org/docs/Web/CSS/color-scheme
// css-color-adjust-1 §2: declares which color schemes the element supports;
// drives light-dark() resolution and UA form/scrollbar theming. Issue #38
// fix: was a coverage-only registry claim with no applier — light-dark()
// fixtures could never flip because the scheme never reached the DOM.
export interface ColorSchemeConfig { value?: string }
export const COLOR_SCHEME_PROPERTY_TYPE = 'ColorScheme' as const;
export type ColorSchemePropertyType = typeof COLOR_SCHEME_PROPERTY_TYPE;
