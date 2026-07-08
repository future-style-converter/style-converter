// BackgroundImageConfig.ts — typed record for the CSS `background-image` IR.
// IR payload is an array of layer entries.  Each entry is either:
//   - a gradient: { type: 'linear-gradient'|'radial-gradient'|'conic-gradient'|
//                   'repeating-linear-gradient'|'repeating-radial-gradient'|'repeating-conic-gradient',
//                   angle?: {deg}, stops: [{color, position}], ... }
//   - a url: { type: 'url', url: '...' }
//   - a none: { type: 'none' }
// We stash each layer as a reconstructed CSS string; the applier comma-joins them.

// One entry per background layer — already a CSS string such as
// 'linear-gradient(45deg, red, blue)' or 'url(x.png)' or 'none'.
export interface BackgroundLayer {
  css: string;                                                        // CSS fragment for one layer
}

// Config holder — array so multi-layer syntax is supported natively.
export interface BackgroundImageConfig {
  layers: BackgroundLayer[];                                          // empty = no background-image set
}

// IR property type name handled here.
export const BACKGROUND_IMAGE_PROPERTY_TYPE = 'BackgroundImage' as const;
export type BackgroundImagePropertyType = typeof BACKGROUND_IMAGE_PROPERTY_TYPE;
