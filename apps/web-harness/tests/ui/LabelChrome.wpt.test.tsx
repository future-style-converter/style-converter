// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture&wpt=1" }
//
// LabelChrome.wpt.test.tsx — the WPT-gate pin for the harness label chrome
// (wave 51 PR (A); docs/DYNAMIC_CAPTURE.md "Harness label chrome"). The
// environment URL carries `?wpt=1`, so the read-once WPT_MODE constants in
// CaptureGallery.tsx AND ComponentRenderer.tsx resolve true exactly the way
// a TITAN corpus capture boots (tools/titan/section-runner.sh WPT_MODE=1 →
// capture-url.mjs `&wpt=1`). The corpus diffs against a Chromium browser-
// ref that has no debug label, so a WPT capture must carry NONE — neither
// as canvas chrome nor (the old site) inside the component element.
//
// MUTATION RECORD (executed 2026-09-16 on this tree, source restored
// byte-exact afterwards): `!WPT_MODE` dropped from CaptureCanvas.showLabel
// → "zero chrome under ?wpt=1" red: one `[data-label-chrome]` per root
// (2 found, 0 expected). LabelChrome.test.tsx stayed green under the same
// mutation (its URL has no wpt), which is why this file exists.
import { describe, it, expect, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/** Minimal decoded v2 document. */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
// Two childless textless roots — the shape that IS labelled in legacy mode.
const roots: IRComponent[] = [
  { id: 'wpt__css-color__t__0', name: 'wpt__css-color__t__0', properties: [] },
  { id: 'wpt__css-color__t__1', name: 'wpt__css-color__t__1', properties: [] },
];

afterEach(() => { document.body.innerHTML = ''; });

describe('LabelChrome — WPT mode carries no label (?wpt=1)', () => {
  it('CaptureGallery emits zero [data-label-chrome] under ?wpt=1', () => {
    document.body.innerHTML = renderToStaticMarkup(<CaptureGallery document={doc(roots)} />);
    const canvases = document.querySelectorAll('[data-capture-canvas]');
    // Sanity: WPT mode took effect — the viewport-sized canvas shape.
    expect(canvases).toHaveLength(2);
    expect(canvases[0].getAttribute('style')).toContain('min-height:600px');
    // The pin: not one chrome svg anywhere on the page.
    expect(document.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
    // And the renderer skin draws none either (the old in-element site).
    expect(document.querySelector('[data-component-id] svg')).toBeNull();
  });

  it('ComposedCaptureGallery emits zero [data-label-chrome] (guard)', () => {
    document.body.innerHTML = renderToStaticMarkup(<ComposedCaptureGallery document={doc(roots)} />);
    expect(document.querySelectorAll('[data-capture-canvas]').length).toBeGreaterThan(0);
    expect(document.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
  });
});
