// ClipPathGeometryBoxConfig.ts — The standalone "geometry-box" component of
// the `clip-path` grammar.  In the IR this appears as its own property when
// the CSS declaration specifies only a box keyword and no shape — the combined
// form is folded into ClipPath.  There is no standalone CSS property; web
// appliers merge this into the `clipPath` value.
// IR: bare SHOUTY_SNAKE keyword (e.g. 'BORDER_BOX').
export interface ClipPathGeometryBoxConfig { value?: string; }
export const CLIP_PATH_GEOMETRY_BOX_PROPERTY_TYPE = 'ClipPathGeometryBox' as const;
export type ClipPathGeometryBoxPropertyType = typeof CLIP_PATH_GEOMETRY_BOX_PROPERTY_TYPE;
