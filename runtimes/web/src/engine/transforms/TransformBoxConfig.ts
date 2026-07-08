// TransformBoxConfig.ts — CSS `transform-box` (Transforms L1).
// https://developer.mozilla.org/docs/Web/CSS/transform-box
// IR: bare SHOUTY_SNAKE keyword ('CONTENT_BOX', 'BORDER_BOX', 'FILL_BOX', ...).

export interface TransformBoxConfig { value?: string; }                           // kebab-case keyword
export const TRANSFORM_BOX_PROPERTY_TYPE = 'TransformBox' as const;
export type TransformBoxPropertyType = typeof TRANSFORM_BOX_PROPERTY_TYPE;
