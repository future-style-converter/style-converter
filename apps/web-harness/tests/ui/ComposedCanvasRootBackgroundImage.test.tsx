// wave-53 lane L3 (item A) — the composed canvas paints the ROOT'S
// background-IMAGE layers (css-backgrounds-3 §2.11.2), not only its colour.
//
// THE DEFECT THIS PINS. resolveCanvasBackground read the body-root's
// BackgroundColor and discarded the BackgroundImage the same buildStyles call
// computes, and the body-root node is a SIBLING of the flow content, so nobody
// painted the image across the canvas. MEASURED on wave52-ship: css-display/
// display-contents-root-background web f 0.534 (white where the ref is all
// (0,128,0)); css-backgrounds/background-attachment-margin-root-001/-002 web
// f 0.4511 / 0.339 (the gradients only inside the 258×300 root box at (66,66)).
//
// THE CONTRACT (shared with Compose ComposedCanvasRootBackgroundTest and the
// SwiftUI WPTCaptureModeTests root-background pins):
//   - a uniform-by-construction stack (1×1 PNG, repeat) paints the FRAMED outer
//     surface (capture-frame chrome — the ref's padColorFor ring rule);
//   - any other stack paints the ICB div only, tile origins per §3.4 (scroll →
//     the root box = ICB + canvas-owned margin; fixed → the ICB corner), with
//     attachment never passed through as `fixed`;
//   - the body-root node renders WITHOUT the image layers (one owner);
//   - containment (css-contain-2 §2) blocks it, exactly as for the colour;
//   - colour-only bodies render the exact pre-wave-53 markup (no image key).
//
// Every component list is VERBATIM from tools/titan/runs/wave52-ship/sections/
// <section>/per-test-ir/ (byte-identical to wave53-open).
//
// EXECUTED MUTATIONS (applied to ComposedCaptureGallery.tsx, this file run
// alone, source restored byte-exact — sha256 in
// tools/titan/results/wave53-canvas-root/_note.md):
//   GW1 the colour-only read restored (rootImagePlan forced null) → target_framed* red.
//   GW2 the strip dropped (canvasRoots = roots)                     → *_bodyRootLosesImage red.
//   GW3 every stack painted framed (rootImageOnIcb = false)          → marginRoot001_icbOnly red.
//   GW4 the containment gate dropped (contained passed as false)     → contained_* red.
//   GW5 always emit `backgroundImage: 'none'` on the outer surface    → colourOnly_* red.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery, resolveCanvasRootBackground } from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** css-display/display-contents-root-background — both roots, verbatim. */
const TARGET = [{"id":"wpt__css-display__display-contents-root-background__0-234","name":"wpt__css-display__display-contents-root-background__0","properties":[{"type":"Display","data":"CONTENTS"},{"type":"BackgroundImage","data":[{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82","data":true}]}],"meta":{"role":"body-root"}},{"id":"wpt__css-display__display-contents-root-background__1-235","name":"wpt__css-display__display-contents-root-background__1","properties":[],"text":"Pass if the background is green.","meta":{"sourceTag":"p"}}];
/** css-backgrounds/background-attachment-margin-root-001 — its one root, verbatim. */
const MR001 = [{"id":"wpt__css-backgrounds__background-attachment-margin-root-001__0-091","name":"wpt__css-backgrounds__background-attachment-margin-root-001__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"scroll"},{"type":"fixed"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}}];
/** css-cascade/initial-background-color — all roots, verbatim (colour-only body). */
const INITIAL_BG = [{"id":"wpt__css-cascade__initial-background-color__0-033","name":"wpt__css-cascade__initial-background-color__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}],"meta":{"role":"body-root"}},{"id":"wpt__css-cascade__initial-background-color__1-034","name":"wpt__css-cascade__initial-background-color__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"percentage","value":100}},{"type":"BackgroundColor","data":{"original":"initial"}}]}];
/** css-color/a98rgb-003 — all roots, verbatim (colour-only body). */
const A98 = [{"id":"wpt__css-color__a98rgb-003__0-005","name":"wpt__css-color__a98rgb-003__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"grey"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-color__a98rgb-003__1-006","name":"wpt__css-color__a98rgb-003__1","properties":[],"text":"Test passes if you see a single square, and not two rectangles of different colors.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-color__a98rgb-003__2-007","name":"wpt__css-color__a98rgb-003__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.996078431372549,"g":0.996078431372549,"b":0.996078431372549},"original":{"r":254,"g":254,"b":254}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginBottom","data":{"px":0}}],"meta":{"role":"ws-after"}},{"id":"wpt__css-color__a98rgb-003__3-008","name":"wpt__css-color__a98rgb-003__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9999300658875595,"g":1,"b":1},"original":{"type":"color","colorSpace":"a98-rgb","values":[1,1,1]}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginTop","data":{"px":0}}]}];
/** css-contain/contain-body-bg-001 — all roots, verbatim (contained colour body). */
const CONTAIN_BG = [{"id":"wpt__css-contain__contain-body-bg-001__0-003","name":"wpt__css-contain__contain-body-bg-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":300}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Contain","data":["LAYOUT"]}],"meta":{"role":"body-root","lang":"en"}},{"id":"contain-body-bg-001__0-004","name":"contain-body-bg-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":300}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}}],"slot":{"parent":"wpt__css-contain__contain-body-bg-001__0-003"},"text":"Test passes if there is no red.","meta":{"sourceTag":"p","lang":"en"}}];
/** css-masking/clip-path/clip-path-document-element — all roots, verbatim (root clip, colour body). */
const CLIP_DOC = [{"id":"wpt__css-masking__clip-path__clip-path-document-element__0-047","name":"wpt__css-masking__clip-path__clip-path-document-element__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"ClipPath","data":{"type":"polygon","points":[{"x":{"px":50},"y":{"px":50}},{"x":{"px":100},"y":{"px":50}},{"x":{"px":100},"y":{"px":100}},{"x":{"px":150},"y":{"px":100}},{"x":{"px":150},"y":{"px":150}},{"x":{"px":50},"y":{"px":150}}]}}],"meta":{"role":"body-root"}},{"id":"wpt__css-masking__clip-path__clip-path-document-element__1-048","name":"wpt__css-masking__clip-path__clip-path-document-element__1","properties":[{"type":"Width","data":{"type":"length","px":500}},{"type":"Height","data":{"type":"length","px":500}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]}];

/** A per-test document over a verbatim component list. */
const doc = (components: unknown[]): IRDocument =>
  ({ irVersion: 2, minReaderVersion: 2, components }) as unknown as IRDocument;
/** Render one document through the real gallery (static markup). */
const render = (d: IRDocument) => renderToStaticMarkup(<ComposedCaptureGallery document={d} />);
/** The framed outer canvas div's inline style. */
const outerStyleOf = (html: string) => /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** The ICB div's inline style. */
const icbStyleOf = (html: string) => /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** One component node's opening tag, by its data-component-id (its style, if any, lives here). */
const nodeTagOf = (html: string, id: string) =>
  new RegExp(`<[^>]*data-component-id="${id}"[^>]*>`).exec(html)?.[0] ?? '';
/** That tag's inline style only (the ids themselves contain "background-attachment"). */
const nodeStyleOf = (html: string, id: string) => /style="([^"]*)"/.exec(nodeTagOf(html, id))?.[1] ?? '';

describe('ComposedTestCanvas — root background image (wave-53 L3 item A)', () => {
  it('target_framedSurface_paintsTheDataUriOverTheColour', () => {
    const outer = outerStyleOf(render(doc(TARGET)));
    // The colour rides background-color (no shorthand), the image above it.
    expect(outer).not.toMatch(/(^|;)background:/);
    expect(outer).toContain('background-color:#FFFFFF');
    expect(outer).toContain('background-image:url(&quot;data:image/png,%89%50%4e%47');
    // Tile origin = the ICB corner inside the framed surface (16,16).
    expect(outer).toContain('background-position:16px 16px');
    expect(outer).toContain('background-attachment:scroll');
    // …and the ICB div carries no image of its own.
    expect(icbStyleOf(render(doc(TARGET)))).not.toContain('background-image');
  });

  it('target_bodyRootLosesImage', () => {
    // §2.11.2 "not painted again": the `display: contents` node keeps Display only.
    const html = render(doc(TARGET));
    expect(nodeTagOf(html, TARGET[0].id)).not.toBe('');
    expect(nodeStyleOf(html, TARGET[0].id)).not.toContain('background-image');
  });

  it('marginRoot001_icbOnly_frameKeepsTheColour', () => {
    const html = render(doc(MR001));
    // Non-uniform stack → the ICB div paints, origins per §3.4 (scroll 50,50; fixed 0,0).
    const icb = icbStyleOf(html);
    expect(icb).toContain('background-image:linear-gradient(180deg, rgba(0, 255, 0, 0.5), rgba(0, 0, 255, 0.5)), linear-gradient(180deg, rgba(0, 0, 0, 1), rgba(0, 0, 0, 1))');
    expect(icb).toContain('background-position:50px 50px, 0px 0px');
    expect(icb).toContain('background-attachment:scroll, scroll');
    expect(icb).toContain('background-size:100px 100px, 100px 100px');
    // The framed surface keeps today's colour shorthand and no image.
    const outer = outerStyleOf(html);
    expect(outer).toContain('background:#FFFFFF');
    expect(outer).not.toContain('background-image');
  });

  it('marginRoot001_bodyRootLosesImage', () => {
    // The sized 258×300 root box no longer paints its own (wrong-phase) tiles.
    const html = render(doc(MR001));
    expect(nodeTagOf(html, MR001[0].id)).not.toBe('');
    // Its box keeps the 300-px height and nothing of the image stack.
    expect(nodeStyleOf(html, MR001[0].id)).toContain('height:300px');
    expect(nodeStyleOf(html, MR001[0].id)).not.toMatch(/background-(image|attachment|size)/);
  });

  it('contained_noPropagation_bodyKeepsItsImage', () => {
    // The target + `contain: layout` → the canvas never paints it, the node does.
    const contained = TARGET.map((c, i) => i === 0
      ? { ...c, properties: [...c.properties, { type: 'Contain', data: ['LAYOUT'] }] } : c);
    expect(resolveCanvasRootBackground(doc(contained))).toBeNull();
    const html = render(doc(contained));
    expect(outerStyleOf(html)).not.toContain('background-image');
    expect(icbStyleOf(html)).not.toContain('background-image');
    expect(nodeStyleOf(html, TARGET[0].id)).toContain('background-image');
  });

  it('colourOnly_noImageKeyAnywhere', () => {
    // Colour-only, contained and root-clipped bodies: no plan, no image key on
    // either canvas div — the pre-wave-53 surfaces exactly.
    for (const comps of [INITIAL_BG, A98, CONTAIN_BG, CLIP_DOC]) {
      expect(resolveCanvasRootBackground(doc(comps))).toBeNull();
      const html = render(doc(comps));
      expect(outerStyleOf(html)).not.toContain('background-image');
      expect(icbStyleOf(html)).not.toContain('background-image');
    }
    // The two colour bodies keep the shorthand colour on the framed surface.
    expect(outerStyleOf(render(doc(INITIAL_BG)))).toContain('background:rgba(0, 128, 0, 1)');
    expect(outerStyleOf(render(doc(A98)))).toMatch(/(^|;)background:rgba\(128, 128, 128, 1\)/);
  });
});
