// ClipRuleConfig.ts — SVG `clip-rule` (CSS Masking 1 §5).
// https://developer.mozilla.org/docs/Web/CSS/clip-rule
// IR: 'NONZERO' | 'EVENODD'.
export interface ClipRuleConfig { value?: string; }
export const CLIP_RULE_PROPERTY_TYPE = 'ClipRule' as const;
export type ClipRulePropertyType = typeof CLIP_RULE_PROPERTY_TYPE;
