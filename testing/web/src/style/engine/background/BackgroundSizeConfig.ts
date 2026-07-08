// BackgroundSizeConfig.ts — typed record for CSS `background-size`.
// IR is an array of per-layer entries, each either a keyword string
// ('cover' | 'contain' | 'auto') or an object { w: Len|number, h?: Len|number }.
// We reduce every entry to a pre-rendered CSS fragment and comma-join.

// One entry per layer — already a CSS fragment such as 'cover' or '100px 50%'.
export interface BackgroundSizeLayer {
  css: string;                                                        // CSS fragment for one layer
}

// Config holder — array so multi-layer syntax is preserved.
export interface BackgroundSizeConfig {
  layers: BackgroundSizeLayer[];                                      // empty = unset
}

// IR property type name handled here.
export const BACKGROUND_SIZE_PROPERTY_TYPE = 'BackgroundSize' as const;
export type BackgroundSizePropertyType = typeof BACKGROUND_SIZE_PROPERTY_TYPE;
