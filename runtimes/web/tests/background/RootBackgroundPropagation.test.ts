// wave-53 lane L3 (item A) — pins for the PURE root-background propagation
// rule (runtimes/web/src/engine/background/RootBackgroundPropagation.ts),
// css-backgrounds-3 §2.11.2 / §3.4 + the capture-frame F2 rule.
//
// Every body-root bag is VERBATIM from tools/titan/runs/wave52-ship/sections/
// <section>/per-test-ir/ (byte-identical to wave53-open). The harness wiring
// (which surface paints, the forest strip, colour-only deep-equality) is pinned
// separately in apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx.
//
// EXECUTED MUTATIONS (applied to RootBackgroundPropagation.ts, this file run
// alone, source restored byte-exact — sha256 logged in
// tools/titan/results/wave53-canvas-root/_note.md):
//   RW1 every stack uniform (`uniform: true`)          → a2_marginRoot001 red.
//   RW2 attachment ignored (every layer `scroll`)      → a2_marginRoot002 red.
//   RW3 dataPngIs1x1 returns true for any PNG url      → a3_ihdr2x2 red.
//   RW4 the `contained` gate dropped                   → a4_contained red.

import { describe, it, expect } from 'vitest';
import {
  rootBackgroundPlan, rootCanvasBackgroundStyle, withRootBackgroundStripped, dataPngIs1x1,
} from '../../src/engine/background/RootBackgroundPropagation';

/** css-display/display-contents-root-background root 0 (`:root { display: contents; background-image: url(1x1-green.png) }`). */
const BODY_TARGET = {"id":"wpt__css-display__display-contents-root-background__0-234","name":"wpt__css-display__display-contents-root-background__0","properties":[{"type":"Display","data":"CONTENTS"},{"type":"BackgroundImage","data":[{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82","data":true}]}],"meta":{"role":"body-root"}};
/** css-backgrounds/background-attachment-margin-root-001 root 0 (`scroll, fixed`). */
const BODY_MR001 = {"id":"wpt__css-backgrounds__background-attachment-margin-root-001__0-091","name":"wpt__css-backgrounds__background-attachment-margin-root-001__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"scroll"},{"type":"fixed"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}};
/** css-backgrounds/background-attachment-margin-root-002 root 0 (`fixed, scroll`). */
const BODY_MR002 = {"id":"wpt__css-backgrounds__background-attachment-margin-root-002__0-092","name":"wpt__css-backgrounds__background-attachment-margin-root-002__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"fixed"},{"type":"scroll"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}};
/** css-cascade/initial-background-color root 0 (colour only). */
const BODY_INITIAL_BG = {"id":"wpt__css-cascade__initial-background-color__0-033","name":"wpt__css-cascade__initial-background-color__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}],"meta":{"role":"body-root"}};
/** css-color/a98rgb-003 root 0 (colour only). */
const BODY_A98 = {"id":"wpt__css-color__a98rgb-003__0-005","name":"wpt__css-color__a98rgb-003__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"grey"}}],"meta":{"role":"body-root"}};

/** The target's data-URI layer URL, read from the verbatim bag. */
const TARGET_URL = (BODY_TARGET.properties[1].data as Array<{ url: string }>)[0].url;
/** The canonical canvas-owned margins: none for the target, 50 px for the margin-root pair. */
const NO_MARGIN = { top: 0, left: 0 };
const M50 = { top: 50, left: 50 };

describe('rootBackgroundPlan — css-backgrounds-3 §2.11.2 / §3.4', () => {
  it('a1_target_oneUniformLayer_withThatUrl', () => {
    // One `scroll` image layer, ICB-relative origin (0,0), uniform (1×1 PNG, repeat).
    expect(rootBackgroundPlan(BODY_TARGET.properties, NO_MARGIN, false)).toEqual({
      attachments: ['scroll'], origins: [{ x: 0, y: 0 }], uniform: true });
    // The canvas paints exactly that URL (the runtime's own url() serialiser).
    const css = rootCanvasBackgroundStyle(BODY_TARGET.properties,
      rootBackgroundPlan(BODY_TARGET.properties, NO_MARGIN, false)!, 16);
    expect(css.backgroundImage).toBe(`url("${TARGET_URL}")`);
    // Framed surface: the origin rides the 16-px frame.
    expect(css.backgroundPosition).toBe('16px 16px');
  });

  it('a1_strip_removesTheImageKeys_keepsDisplay', () => {
    // §2.11.2 "not painted again": the forest node keeps only Display.
    expect(withRootBackgroundStripped(BODY_TARGET.properties).map((p) => p.type)).toEqual(['Display']);
  });

  it('a2_marginRoot001_notUniform_scrollAtRootBox_fixedAtIcb', () => {
    // Layer 0 is a real green→blue ramp → not uniform → ICB-only (F2).
    expect(rootBackgroundPlan(BODY_MR001.properties, M50, false)).toEqual({
      attachments: ['scroll', 'fixed'], origins: [{ x: 50, y: 50 }, { x: 0, y: 0 }], uniform: false });
  });

  it('a2_marginRoot002_isTheMirror', () => {
    // `fixed, scroll`: the top layer anchors at the viewport (ICB) corner.
    expect(rootBackgroundPlan(BODY_MR002.properties, M50, false)?.origins)
      .toEqual([{ x: 0, y: 0 }, { x: 50, y: 50 }]);
  });

  it('a2_style_neverPassesFixedThrough_andKeepsSizes', () => {
    const plan = rootBackgroundPlan(BODY_MR001.properties, M50, false)!;
    const css = rootCanvasBackgroundStyle(BODY_MR001.properties, plan, 0);
    // A verbatim `fixed` would anchor to the gallery page's viewport.
    expect(css.backgroundAttachment).toBe('scroll, scroll');
    // ICB surface: the origins are the plan's own (base 0).
    expect(css.backgroundPosition).toBe('50px 50px, 0px 0px');
    expect(css.backgroundSize).toBe('100px 100px, 100px 100px');
  });

  it('a3_ihdr1x1_isUniform_ihdr2x2_isNot', () => {
    expect(dataPngIs1x1(TARGET_URL)).toBe(true);
    // The SAME payload with IHDR width/height patched to 2.
    const twoByTwo = TARGET_URL.replace('%49%48%44%52%00%00%00%01%00%00%00%01', '%49%48%44%52%00%00%00%02%00%00%00%02');
    expect(twoByTwo).not.toBe(TARGET_URL);
    expect(dataPngIs1x1(twoByTwo)).toBe(false);
    // …and the plan follows: a 2×2 tile is never stretched into the frame.
    const props = BODY_TARGET.properties.map((p) => p.type === 'BackgroundImage'
      ? { ...p, data: [{ url: twoByTwo, data: true }] } : p);
    expect(rootBackgroundPlan(props, NO_MARGIN, false)?.uniform).toBe(false);
    // A non-PNG data URI is never "uniform by construction".
    expect(dataPngIs1x1('data:image/gif,%47%49%46%38%39%61%01%00%01%00')).toBe(false);
  });

  it('a4_contained_noPropagation', () => {
    // css-contain-2 §2: the caller's containment gate takes the root off the path.
    expect(rootBackgroundPlan([...BODY_TARGET.properties, { type: 'Contain', data: ['LAYOUT'] }], NO_MARGIN, true)).toBeNull();
  });

  it('a5_colourOnlyBodies_noImagePlan', () => {
    // Colour handling is untouched: no image layer → no plan at all.
    expect(rootBackgroundPlan(BODY_INITIAL_BG.properties, NO_MARGIN, false)).toBeNull();
    expect(rootBackgroundPlan(BODY_A98.properties, NO_MARGIN, false)).toBeNull();
    // A lone `none` layer paints nothing either.
    expect(rootBackgroundPlan([{ type: 'BackgroundImage', data: ['none'] }], NO_MARGIN, false)).toBeNull();
  });
});
