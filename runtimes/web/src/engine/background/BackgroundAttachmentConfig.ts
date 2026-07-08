// BackgroundAttachmentConfig.ts — typed record for CSS `background-attachment`.
// IR shape: array of { type: 'scroll'|'fixed'|'local' } (or bare strings).

// One entry per layer.
export interface BackgroundAttachmentLayer {
  css: string;                                                        // 'scroll'|'fixed'|'local'
}

// Config holder.
export interface BackgroundAttachmentConfig {
  layers: BackgroundAttachmentLayer[];                                // empty = unset
}

// IR property type.
export const BACKGROUND_ATTACHMENT_PROPERTY_TYPE = 'BackgroundAttachment' as const;
export type BackgroundAttachmentPropertyType = typeof BACKGROUND_ATTACHMENT_PROPERTY_TYPE;
