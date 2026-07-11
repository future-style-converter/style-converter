/**
 * @style-converter/web — package barrel.
 *
 * This module provides the complete style engine for rendering
 * IR components as CSS in the browser. Deep-path imports are also
 * supported via the package's `"./*": "./src/*"` export wildcard.
 */

// IR model types (IRDocument, IRComponent, IRProperty, …)
export * from './core/ir';
// Value types + extractors (lengths, colors, angles, times, …)
export * from './core/types';
export * from './core/colors';
// StyleBuilder — the top-level IRProperty[] → CSSStyles dispatcher
export * from './core/renderer';

// Renderer surface (issue #41) — DocumentRenderer/NodeRenderer, the
// Composer (slot refs → tree), the sourceTag element policy, the
// stylesheet-lifecycle hook, and the RendererOptions calibration types.
export * from './renderer';

// PropertyRegistry — migrated-property set used for coverage introspection
export * from './engine/PropertyRegistry';

// PropertyTracker — log-once unknown/unhandled tracker (CLAUDE.md
// no-silent-fallthrough contract; mirrors the Compose runtime's object).
// Namespaced because its member names (reset, getReport, …) are generic.
export * as PropertyTracker from './engine/PropertyTracker';

// Debug tools
export * from './debug';
