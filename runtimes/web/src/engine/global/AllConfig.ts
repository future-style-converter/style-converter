// AllConfig.ts — CSS `all` global longhand-reset shorthand.
// MDN: https://developer.mozilla.org/docs/Web/CSS/all
// IR emits one of the global keywords: initial | inherit | unset | revert | revert-layer.
export interface AllConfig { value?: string }
export const ALL_PROPERTY_TYPE = 'All' as const;
