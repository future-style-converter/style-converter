// DynamicRangeLimitConfig.ts — CSS Color HDR 1 §3.1 `dynamic-range-limit`.
// https://developer.mozilla.org/docs/Web/CSS/dynamic-range-limit
// Issue #38 audit: the old registry note claimed "web honours the CSS
// property natively" — but nothing ever EMITTED it, so the claim was a
// silent drop. Now a real (csstype-widened) one-key pass-through.
export interface DynamicRangeLimitConfig { value?: string }
export const DYNAMIC_RANGE_LIMIT_PROPERTY_TYPE = 'DynamicRangeLimit' as const;
