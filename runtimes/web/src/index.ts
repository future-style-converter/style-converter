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

// PropertyRegistry — migrated-property set used for coverage introspection
export * from './engine/PropertyRegistry';

// Debug tools
export * from './debug';
