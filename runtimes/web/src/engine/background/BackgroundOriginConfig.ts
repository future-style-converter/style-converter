// BackgroundOriginConfig.ts — typed record for CSS `background-origin`.
// IR shape: array of { type: 'border-box'|'padding-box'|'content-box' }.

// One entry per layer.
export interface BackgroundOriginLayer {
  css: string;                                                        // CSS token for one layer
}

// Config holder.
export interface BackgroundOriginConfig {
  layers: BackgroundOriginLayer[];                                    // empty = unset
}

// IR property type.
export const BACKGROUND_ORIGIN_PROPERTY_TYPE = 'BackgroundOrigin' as const;
export type BackgroundOriginPropertyType = typeof BACKGROUND_ORIGIN_PROPERTY_TYPE;
