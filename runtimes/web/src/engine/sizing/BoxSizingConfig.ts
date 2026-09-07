// BoxSizingConfig.ts — https://developer.mozilla.org/docs/Web/CSS/box-sizing
// css-sizing-3 §3.3: switches the sizing model between content-box (initial)
// and border-box. Issue #38 fix: was a coverage-only registry claim with no
// applier — width/height fixtures with box-sizing silently mis-measured.
export interface BoxSizingConfig { value?: 'content-box' | 'border-box' }
export const BOX_SIZING_PROPERTY_TYPE = 'BoxSizing' as const;
