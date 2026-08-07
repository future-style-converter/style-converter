/**
 * useFontFaces — the harness-side MOUNT of the document's `@font-face`
 * declarations (schema/spec/01-envelope.md §5; wave-34 lane F2).
 *
 * ## Why this lives in the HARNESS and not in the engine
 *
 * Everything under `runtimes/web/src/engine/` is a per-PROPERTY triplet:
 * one IR property in, one CSS declaration out. A font FACE is neither — it
 * is a document-level RESOURCE that must be registered with the browser
 * before any element referencing it lays out, and registering it means
 * knowing where the producing pipeline's asset root is mounted. That is a
 * host concern by construction (an SDUI shell embedding the runtime would
 * serve the same faces from its own CDN), so the engine stays free of it
 * and the harness — which owns the /wpt-font/ route in vite.config.ts —
 * does the mounting.
 *
 * ## What it fixes
 *
 * Until wave 34 the pipeline dropped `@font-face` entirely: the extractor's
 * CSS reader skips every at-rule, so a test declaring
 * `@font-face { font-family: test; src: url(resources/X.woff) }` plus
 * `body { font: 36px test }` reached the harness carrying the family NAME
 * and no face, and every capture fell through to the bundled Inter while
 * the browser-ref — raw WPT HTML that Chromium renders with the real file —
 * painted the author's face. css-text/boundary-shaping-001…010 are the
 * clean example: they assert on LIGATURES ("fi"/"ffi") that only the
 * declared LinLibertine face carries, so the assertion was unobservable on
 * our side of the diff.
 *
 * ## Contract
 *
 * One managed `<style data-font-faces>` element in <head>, rebuilt only
 * when the rule text changes (documents without faces mount nothing, so the
 * committed 327-pair baseline DOM is untouched). Rules are emitted in
 * DOCUMENT order because css-fonts-4 §4.1 makes a later face with the same
 * (family, weight, style) win — reordering would silently change which file
 * renders.
 *
 * `font-display: block` mirrors the harness's own bundled-Inter rules in
 * index.html and the ref injection's embedded faces
 * (tools/titan/capture-browser-ref.mjs interFontFaceCss): a capture must
 * never race a fallback-face first paint, because the screenshot would then
 * record the WRONG typeface with no error anywhere.
 */

import React from 'react';
import type { IRDocument, IRFontFace } from '@style-converter/web/core/ir/IRModels';

/** The <head> element id/attribute of the managed stylesheet. Distinct from
 *  RuleBuilder's managed element so the two mounts never fight over one
 *  node — they have different lifetimes (faces change per document, rules
 *  change per document AND per forced state). */
export const FONT_FACE_STYLE_ID = 'sc-wpt-font-faces';

/** URL prefix the harness serves the producing pipeline's corpus under.
 *  Pinned here and in vite.config.ts's middleware; the unit test compares
 *  the two so a rename cannot half-land. */
export const WPT_FONT_ROUTE = '/wpt-font/';

/**
 * Escape a string for use inside a CSS `<string>` token (CSS Syntax 3
 * §4.3.5). Family names and paths both arrive from the WPT corpus, which is
 * untrusted text as far as this file is concerned: an unescaped quote would
 * terminate the token early and turn the remainder of the family name into
 * arbitrary CSS. Backslash first, then the quote — the reverse order would
 * double-escape the backslashes it just inserted.
 */
function cssString(s: string): string {
  return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

/**
 * The css-fonts-4 §4.3 `format()` hint for a font path, derived from its
 * extension, or null when the extension is not one this route serves.
 *
 * Deriving beats trusting: the wire deliberately does NOT carry the
 * author's `format()` token (spec 01 §5 keeps the entry to four keys), and
 * a `format()` that disagrees with the actual bytes makes Chromium SKIP the
 * face silently — the exact failure mode this channel exists to end. A null
 * here means we emit no `format()` at all and let the browser sniff, which
 * is always legal.
 */
export function formatHintFor(src: string): string | null {
  const ext = /\.([A-Za-z0-9]+)$/.exec(src)?.[1]?.toLowerCase();
  switch (ext) {
    case 'woff2': return 'woff2';
    case 'woff':  return 'woff';
    case 'ttf':   return 'truetype';
    case 'otf':   return 'opentype';
    case 'ttc':
    case 'otc':   return 'collection';
    default:      return null;
  }
}

/**
 * Build the `@font-face` rule text for a document's face list.
 *
 * Pure + exported so the unit test can assert the emitted CSS without a
 * DOM. Returns '' for an absent/empty list — the caller mounts nothing.
 *
 * Entries whose `src` escapes the corpus (`..` segments, an absolute path,
 * or a scheme) are DROPPED rather than served: the route below resolves
 * against the corpus root and a traversal payload would ask it to read
 * outside. The producer already guarantees containment, so a drop here is
 * defence in depth, not an expected path — and dropping is right even so,
 * because emitting a rule the route will refuse would produce a silent
 * fallback face instead of a missing one.
 */
export function buildFontFaceCss(faces: IRFontFace[] | undefined): string {
  if (!faces || faces.length === 0) return '';
  const out: string[] = [];
  for (const f of faces) {
    if (!f?.family || !f?.src) continue;
    // Containment: relative, corpus-internal paths only.
    if (f.src.startsWith('/') || /^[a-z][a-z0-9+.-]*:/i.test(f.src)) continue;
    if (f.src.split('/').includes('..')) continue;
    const fmt = formatHintFor(f.src);
    const parts = [
      `font-family: "${cssString(f.family)}"`,
      // Absent weight/style mean the css-fonts-4 initial `normal`; we emit
      // nothing rather than the literal so the browser applies the same
      // initial it would for a hand-written rule.
      ...(f.weight ? [`font-weight: ${f.weight}`] : []),
      ...(f.style ? [`font-style: ${f.style}`] : []),
      // Never race a fallback first paint — see the file banner.
      'font-display: block',
      `src: url("${cssString(WPT_FONT_ROUTE + f.src)}")${fmt ? ` format("${fmt}")` : ''}`,
    ];
    out.push(`@font-face { ${parts.join('; ')}; }`);
  }
  return out.join('\n');
}

/**
 * Mount (or update, or remove) the managed `<style>` carrying the
 * document's `@font-face` rules. Idempotent: a no-op when the rule text is
 * unchanged, and it REMOVES the element when the text goes empty so a
 * hot-reload swap from a face-bearing document to a face-free one cannot
 * leave a stale face registered.
 *
 * SSR-safe: returns immediately when there is no document object.
 */
export function mountFontFaces(css: string): void {
  if (typeof globalThis.document === 'undefined') return;
  const doc = globalThis.document;
  const existing = doc.getElementById(FONT_FACE_STYLE_ID);
  if (!css) {
    existing?.remove();
    return;
  }
  if (existing) {
    // textContent compare, not innerHTML: identical text means the browser
    // already holds these faces and re-writing would restart every fetch
    // (and, with font-display: block, re-blank the text mid-capture).
    if (existing.textContent !== css) existing.textContent = css;
    return;
  }
  const el = doc.createElement('style');
  el.id = FONT_FACE_STYLE_ID;
  el.setAttribute('data-font-faces', '');
  el.textContent = css;
  doc.head.appendChild(el);
}

/**
 * Build + mount the document's `@font-face` rules. Called once from
 * App.tsx beside useDynamicRules, so every render mode (gallery, capture,
 * composed capture, fixture) registers the same faces.
 */
export function useFontFaces(document: IRDocument | null): void {
  // Pure derivation — memoise on decoded-document identity, exactly like
  // useDynamicRules's rule list.
  const css = React.useMemo(
    () => (document ? buildFontFaceCss(document.fontFaces) : ''),
    [document],
  );
  // useInsertionEffect is React's designated style-injection slot: it fires
  // before layout effects read the DOM — which matters more here than for
  // ordinary rules, since a face registered after first layout would reflow
  // every text box the capture is about to measure.
  React.useInsertionEffect(() => {
    mountFontFaces(css);
  }, [css]);
}
