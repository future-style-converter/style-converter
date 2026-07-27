// Tests for the `only` prop on ComposedCaptureGallery — the page side of
// the B-RC3 (wave 21) isolated composed-capture path. capture-isolated.mjs
// re-navigates to the composed URL plus `&only=<testKey>` (App.tsx →
// getComposedOnly → this prop) so the fresh page mounts EXACTLY ONE
// composed canvas: headless Chromium mis-paints the spanner+abspos family
// on the tall multi-canvas page (~2312px paint offset, correct geometry —
// css-multicol/abspos-containing-block-outside-spanner) but paints a
// single-canvas page correctly.
//
// Backstop guarantees pinned here:
//   - No `only` ⇒ every test renders, sentinel counts them all — the
//     batch composed capture is byte-identical to pre-B-RC3.
//   - `only=<key>` ⇒ exactly that test's canvas mounts and the
//     `data-capture-ready` sentinel reads 1 (the driver asserts ===1).
//   - Exact-key matching (never prefix) and a no-match ⇒ zero canvases +
//     sentinel 0, which the driver turns into a LOUD failure.
//
// renderToStaticMarkup (no DOM emulation) is enough: the observable is
// which data-capture-* attributes appear in the emitted markup — the same
// technique CaptureGallery.test.tsx uses.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

// Combined-fixture-shaped document: prefixed ROOTS (`wpt__<sect>__<stem>__
// <idx>`), one raw-named CHILD linked via slot.parent (grouping must walk
// the chain, exactly like split-combined-ir.mjs), spanning two tests.
const doc: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    // Test A — two roots + one child.
    { id: 'a0', name: 'wpt__css-multicol__test-a__0', properties: [] },
    {
      id: 'a0c',
      name: 'test-a__0__0',
      properties: [],
      slot: { parent: 'a0' },
    },
    { id: 'a1', name: 'wpt__css-multicol__test-a__1', properties: [] },
    // Test B — one root. Its stem shares "test-a" as a PREFIX extension so
    // the exact-match pin below is meaningful.
    { id: 'b0', name: 'wpt__css-multicol__test-a-extended__0', properties: [] },
  ],
};

// Count composed canvases in the emitted markup by their marker attribute.
const canvasCount = (html: string) => (html.match(/data-capture-canvas/g) ?? []).length;
// Extract the sentinel value the puppeteer driver waits on / asserts.
const sentinel = (html: string) => html.match(/data-capture-ready="(\d+)"/)?.[1];

describe('ComposedCaptureGallery `only` filter (B-RC3 isolated capture)', () => {
  it('renders every test when `only` is absent (batch path unchanged)', () => {
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    // Two grouped tests ⇒ two canvases, sentinel 2 — pre-B-RC3 behavior.
    expect(canvasCount(html)).toBe(2);
    expect(sentinel(html)).toBe('2');
    expect(html).toContain('data-capture-name="wpt__css-multicol__test-a"');
    expect(html).toContain('data-capture-name="wpt__css-multicol__test-a-extended"');
  });

  it('mounts exactly one canvas for a matching `only` key', () => {
    const html = renderToStaticMarkup(
      <ComposedCaptureGallery document={doc} only="wpt__css-multicol__test-a" />
    );
    // The isolated driver asserts sentinel===1 and canvases===1 — this is
    // the page-side half of that contract.
    expect(canvasCount(html)).toBe(1);
    expect(sentinel(html)).toBe('1');
    expect(html).toContain('data-capture-name="wpt__css-multicol__test-a"');
    // The prefix-extended sibling must NOT leak in (exact key equality).
    expect(html).not.toContain('data-capture-name="wpt__css-multicol__test-a-extended"');
  });

  it('yields zero canvases and sentinel 0 for a non-matching key', () => {
    // Driver/page key drift must surface as the driver's loud
    // "expected exactly 1 canvas, sentinel=0" failure — the page's job is
    // to report an honest 0, never to fall back to the full gallery.
    const html = renderToStaticMarkup(
      <ComposedCaptureGallery document={doc} only="wpt__css-multicol__no-such-test" />
    );
    expect(canvasCount(html)).toBe(0);
    expect(sentinel(html)).toBe('0');
  });
});
