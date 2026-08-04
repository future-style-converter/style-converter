/**
 * @style-converter/web/renderer — the production renderer surface
 * (issue #41): slot composition + React document/node renderers +
 * stylesheet lifecycle + the calibration-hook types that let hosts
 * (like the capture harness) skin the shared core.
 */

// Slot composition (spec 03): flat wire → ComposedNode forest.
export * from './Composer';
// Production sourceTag → element policy (denylist + void set).
export * from './TagMapping';
// wave-20 W1: wire meta.attrs → DOM-prop policy (widget tag set + mapper).
export * from './WidgetAttrs';
// wave-22 DECOR: wire meta.decorations → nested decorating-box spans.
export * from './DecorationSpans';
// Pseudo-element span renderer (split from NodeRenderer, wave-20 W1).
export * from './PseudoNodeRenderer';
// wave-28 PG: root-scope generated-box placement (the html::before pin).
export * from './RootPseudoPlacement';
// Calibration surface: RenderContext + RendererOptions.
export * from './RendererOptions';
// The per-node renderer core.
export * from './NodeRenderer';
// Per-root render-error containment (one bad component fails alone).
export * from './RootErrorBoundary';
// The whole-document renderer.
export * from './DocumentRenderer';
// Stylesheet mount/unmount lifecycle hook.
export * from './useDocumentRules';
