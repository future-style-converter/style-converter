// BackgroundClipConfig.ts — typed record for CSS `background-clip`.
// IR is an array of uppercase enum strings: 'BORDER_BOX' | 'PADDING_BOX' |
// 'CONTENT_BOX' | 'TEXT'.  We lowercase + hyphenate per CSS spec.

// One entry per layer — pre-rendered CSS token.
export interface BackgroundClipLayer {
  css: string;                                                        // 'border-box'|'padding-box'|'content-box'|'text'
}

// Config holder.
export interface BackgroundClipConfig {
  layers: BackgroundClipLayer[];                                      // empty = unset
}

// IR property type.
export const BACKGROUND_CLIP_PROPERTY_TYPE = 'BackgroundClip' as const;
export type BackgroundClipPropertyType = typeof BACKGROUND_CLIP_PROPERTY_TYPE;
