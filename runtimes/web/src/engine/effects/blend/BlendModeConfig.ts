// BlendModeConfig.ts — typed record for the two blend-mode CSS properties.
// `mix-blend-mode` is a single keyword; `background-blend-mode` is a list.
// IR emits uppercase SNAKE_CASE enum strings (e.g. 'PLUS_LIGHTER').

// Config holder — both fields optional so each can be set independently.
export interface BlendModeConfig {
  mix?: string;                                                       // single keyword for mix-blend-mode
  background?: string[];                                              // list for background-blend-mode
}

// IR property types handled by this module.
export const MIX_BLEND_MODE_PROPERTY = 'MixBlendMode' as const;
export const BACKGROUND_BLEND_MODE_PROPERTY = 'BackgroundBlendMode' as const;
export type BlendModePropertyType =
  | typeof MIX_BLEND_MODE_PROPERTY
  | typeof BACKGROUND_BLEND_MODE_PROPERTY;
