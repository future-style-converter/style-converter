// BackgroundRepeatConfig.ts — typed record for CSS `background-repeat`.
// IR is an array: each entry is either a keyword string
// ('repeat'|'no-repeat'|'space'|'round'|'repeat-x'|'repeat-y') or an
// axis-pair object { x: keyword, y: keyword }.

// One entry per layer — pre-rendered CSS fragment.
export interface BackgroundRepeatLayer {
  css: string;                                                        // CSS fragment for one layer
}

// Config holder.
export interface BackgroundRepeatConfig {
  layers: BackgroundRepeatLayer[];                                    // empty = unset
}

// IR property type.
export const BACKGROUND_REPEAT_PROPERTY_TYPE = 'BackgroundRepeat' as const;
export type BackgroundRepeatPropertyType = typeof BACKGROUND_REPEAT_PROPERTY_TYPE;
