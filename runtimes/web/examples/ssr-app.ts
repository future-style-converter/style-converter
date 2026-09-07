/**
 * ssr-app.ts — the "consume from a real app" example (issue #41),
 * executed standalone by runtimes/web/examples/ssr-smoke.mjs (the sibling
 * file — retro P2e made the path repo-relative: the bare `examples/`
 * prefix has read as the repo-root `examples/` tree, deleted by the
 * 2026-07-08 restructure 02e4c457, ever since that rename).
 *
 * A token-themed IR document — CSS custom properties as design tokens
 * defined on the root component, consumed via var() below it — rendered
 * to static HTML with react-dom/server. This is exactly the recipe from
 * the package README: decode → renderToString(DocumentRenderer) +
 * buildStylesheet for the <style> text. JSX-free on purpose (the
 * renderer itself is createElement-based), so any TS toolchain can
 * compile it without a JSX transform.
 */

import { createElement } from 'react';
import { renderToString } from 'react-dom/server';
// The package surface a real app imports — one specifier, no deep paths.
import {
  DocumentRenderer,
  buildStylesheet,
  decodeIRDocument,
} from '@style-converter/web';

/**
 * The wire document, as it would arrive from a server. Tokens live in
 * `variables` on the theme root (spec 01/02: raw declaration values,
 * resolved by CSS inheritance at render time); the button consumes them
 * through the parser's var() pass-through; `keyframes` + a hover bucket
 * exercise the stylesheet path; `meta.sourceTag` maps the button to a
 * REAL <button> element (production trusts the wire — the very thing
 * the capture harness's allowlist refuses).
 */
const WIRE_DOC = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'theme',
      name: 'ThemeRoot',
      properties: [
        // Plain typed declaration: 24px padding on the themed surface.
        { type: 'PaddingTop', data: { type: 'length', px: 24 } },
      ],
      // The design tokens — CSS custom properties, verbatim values.
      variables: { '--brand': '#e74c3c', '--radius': '8px' },
    },
    {
      id: 'cta',
      name: 'CtaButton',
      text: 'Buy now',
      // Wire says this was a <button>; the renderer emits a real one.
      meta: { sourceTag: 'button' },
      slot: { parent: 'theme', name: 'content' },
      properties: [
        // Token consumption: var() rides the Generic pass-through and
        // the browser (or any CSS engine) resolves it via inheritance.
        { type: 'Generic', data: { propertyName: 'background-color', rawValue: 'var(--brand)' } },
        { type: 'Generic', data: { propertyName: 'border-radius', rawValue: 'var(--radius)' } },
        // Animation binding to the document-level keyframes set below.
        { type: 'AnimationName', data: [{ type: 'identifier', name: 'pulse' }] },
      ],
      // Hover state for the stylesheet path (spec 06).
      selectors: [{ condition: 'hover', properties: [{ type: 'Opacity', data: 0.85 }] }],
    },
  ],
  // Document-scoped @keyframes (spec 07 §1.2).
  keyframes: {
    pulse: [
      { offset: 0, properties: [{ type: 'Opacity', data: 0.6 }] },
      { offset: 1, properties: [{ type: 'Opacity', data: 1 }] },
    ],
  },
};

/**
 * Render the document to a complete static HTML page: markup from
 * renderToString, stylesheet from buildStylesheet (the SSR twin of the
 * client-side mount DocumentRenderer performs by itself in a browser).
 */
export function render(): { page: string; html: string; css: string } {
  // The decode gate: version check + v1 translation + slot defaults.
  const doc = decodeIRDocument(WIRE_DOC);
  // Body markup — pure semantics, no harness calibration anywhere.
  const html = renderToString(createElement(DocumentRenderer, { document: doc }));
  // Document stylesheet as a string (selector/media rules + @keyframes).
  const css = buildStylesheet(doc.components, doc.keyframes);
  // A self-contained page a server could ship as-is.
  const page = `<!doctype html>\n<html>\n<head>\n<style>\n${css}\n</style>\n</head>\n<body>\n${html}\n</body>\n</html>\n`;
  return { page, html, css };
}
