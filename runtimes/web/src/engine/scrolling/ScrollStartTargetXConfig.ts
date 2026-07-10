// ScrollStartTargetXConfig.ts — axis variant of scroll-start-target
// (css-scroll-snap-2 draft; csstype-widened). Issue #38 audit: was a
// coverage-only registry claim with no applier — now a real triplet
// mirroring ScrollStartTarget.
export interface ScrollStartTargetXConfig { value?: string }
export const SCROLLSTARTTARGETX_PROPERTY_TYPE = 'ScrollStartTargetX' as const;
