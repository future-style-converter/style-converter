// TransformStyleConfig.ts — CSS `transform-style` (Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/transform-style
// IR: bare 'FLAT' | 'PRESERVE_3D'.
export interface TransformStyleConfig { value?: string; }
export const TRANSFORM_STYLE_PROPERTY_TYPE = 'TransformStyle' as const;
export type TransformStylePropertyType = typeof TRANSFORM_STYLE_PROPERTY_TYPE;
