// RootPseudoPlacement.test.tsx — wave-28 lane PG.
//
// Pins the ROOT-SCOPE generated-box placement rule: a `pseudos` bucket on
// the synthetic body-root holds a box the CSS generated on the DOCUMENT
// ROOT (`html::before`), so a CONTAINED body's `direction: rtl` must not
// decide where it sits (css-contain-1 §3.1 takes the contained body off
// the css-writing-modes-4 §8.1 propagation channel).
//
// MEASURED shape under test — css-contain/contain-body-dir-001..004 in
// tools/titan/runs/wave27-final/sections/css-contain: the Chromium ref
// paints the orange 100×100 square at image [16,16]-[115,115]; our web
// capture painted it at [116,16]-[215,115], one box width to the right,
// because the span was placed by the rtl body's over-constrained margin
// resolution (CSS 2.1 §10.3.3).
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import {
  containmentBlocksDirectionPropagation,
  rootPseudoPlacementStyle,
  rootWritingModeSuppression,
} from '../../src/renderer/RootPseudoPlacement';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent } from '../../src/core/ir/IRModels';

// The live contain-body-dir-001 body-root shape, trimmed to what this rule
// reads: rtl direction, layout containment, and the html::before bucket
// the wave-27 A-RC2 extractor split out of the flat bag.
function bodyRoot(overrides: Partial<IRComponent> = {}): IRComponent {
  return {
    id: 'wpt__contain-body-dir-001__0-011',
    name: 'wpt__contain-body-dir-001__0',
    properties: [
      { type: 'Width', data: { type: 'length', px: 200 } },
      { type: 'Direction', data: 'RTL' },
      { type: 'Contain', data: ['LAYOUT'] },
    ],
    meta: { role: 'body-root' },
    pseudos: {
      before: {
        id: 'pb',
        // RAW declarations map — exactly what the WPT extractor emits.
        properties: {
          content: '""', width: '100px', height: '100px',
          background: 'orange', display: 'block',
        },
      },
    },
    ...overrides,
  } as IRComponent;
}
// ComposedNode wrapper + render shorthand (mirrors NodeRenderer.test.tsx).
const node = (component: IRComponent, children: ComposedNode[] = []): ComposedNode =>
  ({ component, children });
const html = (c: IRComponent) => renderToStaticMarkup(<NodeRenderer node={node(c)} />);

describe('containmentBlocksDirectionPropagation — the shared token gate', () => {
  it('is true for every real containment keyword, false for none/absent', () => {
    // The four kinds the contain-body-dir family declares, all matching the
    // same reference — the spec does not grade propagation by kind.
    for (const kind of ['LAYOUT', 'PAINT', 'CONTENT', 'STRICT']) {
      expect(containmentBlocksDirectionPropagation([{ type: 'Contain', data: [kind] }])).toBe(true);
    }
    // A bare keyword string is the tolerated defensive wire shape.
    expect(containmentBlocksDirectionPropagation([{ type: 'Contain', data: 'PAINT' }])).toBe(true);
    // `contain: none`, an empty list, and no leaf at all are NOT containment.
    expect(containmentBlocksDirectionPropagation([{ type: 'Contain', data: ['NONE'] }])).toBe(false);
    expect(containmentBlocksDirectionPropagation([{ type: 'Contain', data: [] }])).toBe(false);
    expect(containmentBlocksDirectionPropagation([])).toBe(false);
  });
});

describe('rootPseudoPlacementStyle — who gets the pin', () => {
  it('pins the contained body-root to the physical inline-start', () => {
    // `margin-right: auto` removes the §10.3.3 over-constraint so the free
    // space is absorbed on the right in BOTH directions ⇒ box flush left.
    expect(rootPseudoPlacementStyle(bodyRoot())).toEqual({ marginRight: 'auto' });
  });

  it('leaves an UNCONTAINED body-root alone (the body really does propagate)', () => {
    const uncontained = bodyRoot({
      properties: [{ type: 'Direction', data: 'RTL' }],
    });
    expect(rootPseudoPlacementStyle(uncontained)).toBeNull();
  });

  it('leaves every ordinary component alone — their ::before is their own', () => {
    // Same containment, no body-root role: the box belongs to THIS element,
    // so its own direction must keep placing it (no capture may move).
    const ordinary = bodyRoot({ meta: { sourceTag: 'div' } });
    expect(rootPseudoPlacementStyle(ordinary)).toBeNull();
  });
});

describe('NodeRenderer wiring — the pin reaches the span, and loses to authors', () => {
  it('emits margin-right:auto on the root-scope ::before span', () => {
    const out = html(bodyRoot());
    // One span, carrying both the pin and the author declarations.
    expect(out).toContain('data-pseudo="before"');
    expect(out).toMatch(/margin-right:auto/);
    // The author's own box is untouched by the pin.
    expect(out).toMatch(/width:100px/);
    expect(out).toMatch(/background:orange/);
  });

  it('the author rule outranks the pin (declaration order is precedence)', () => {
    const authored = bodyRoot({
      pseudos: {
        before: {
          id: 'pb',
          properties: { width: '100px', display: 'block', 'margin-right': '20px' },
        },
      },
    } as Partial<IRComponent>);
    const out = html(authored);
    expect(out).toMatch(/margin-right:20px/);
    expect(out).not.toMatch(/margin-right:auto/);
  });

  it('an uncontained body-root span is byte-identical to wave 27', () => {
    const uncontained = bodyRoot({ properties: [{ type: 'Direction', data: 'RTL' }] });
    expect(html(uncontained)).not.toMatch(/margin-right/);
  });
});

// ── wave-48 lane W5: the BLOCK axis (writing-mode) ─────────────────────────
//
// The w-m twins of the dir family declare `writing-mode: vertical-rl` on the
// body; css-contain-1 §3.1 takes the contained body off the css-writing-
// modes-4 §3.2 propagation channel exactly as for `direction`, but the pin
// above is inline-axis-only — in vertical-rl the FIRST BLOCK CHILD stacks
// from the RIGHT edge (measured on wave48-cal: orange square at
// [118,16]-[216,115] vs the ref's [16,16]-[115,115], web-ref 0.9101 on all
// 8 w-m cells while both natives — whose Column/VStack always stack
// top-to-bottom — score 0.9984+). The suppression overrides the merged
// body-root element itself back to horizontal-tb.
//
// The VERBATIM wave48-cal per-test-ir property list of
// wpt__css-contain__contain-body-w-m-001__0 (tools/titan/runs/wave48-cal/
// sections/css-contain/per-test-ir/), trimmed of the four zero margins the
// rule never reads.
function wmBodyRoot(overrides: Partial<IRComponent> = {}): IRComponent {
  return bodyRoot({
    properties: [
      { type: 'Width', data: { type: 'length', px: 200 } },
      { type: 'Height', data: { type: 'length', px: 200 } },
      { type: 'WritingMode', data: 'VERTICAL_RL' },
      { type: 'Contain', data: ['LAYOUT'] },
    ],
    ...overrides,
  });
}

describe('rootWritingModeSuppression — who gets the block-axis override', () => {
  it('overrides a contained vertical body-root back to horizontal-tb', () => {
    expect(rootWritingModeSuppression(wmBodyRoot())).toEqual({ writingMode: 'horizontal-tb' });
  });

  it('covers all four containment kinds of the w-m family (001..004)', () => {
    // contain-body-w-m-001..004 declare layout/paint/size/style respectively
    // (VERIFIED against tools/wpt/css/css-contain sources) and share one ref.
    for (const kind of ['LAYOUT', 'PAINT', 'SIZE', 'STYLE']) {
      const c = wmBodyRoot({
        properties: [
          { type: 'WritingMode', data: 'VERTICAL_RL' },
          { type: 'Contain', data: [kind] },
        ],
      });
      expect(rootWritingModeSuppression(c)).toEqual({ writingMode: 'horizontal-tb' });
    }
  });

  it('leaves an UNCONTAINED vertical body-root alone (propagation is real)', () => {
    const uncontained = wmBodyRoot({
      properties: [{ type: 'WritingMode', data: 'VERTICAL_RL' }],
    });
    expect(rootWritingModeSuppression(uncontained)).toBeNull();
  });

  it('leaves a contained HORIZONTAL body-root alone — the dir family DOM must not move', () => {
    // The 8 contain-{body,html}-dir cells carry Direction but no vertical
    // WritingMode; their pin is the inline-axis one, nothing more.
    expect(rootWritingModeSuppression(bodyRoot())).toBeNull();
  });

  it('leaves every ordinary component alone, containment or not', () => {
    const ordinary = wmBodyRoot({ meta: { sourceTag: 'div' } });
    expect(rootWritingModeSuppression(ordinary)).toBeNull();
  });
});

describe('NodeRenderer wiring — the override beats the emitted writing-mode', () => {
  it('emits writing-mode:horizontal-tb on the contained body-root element', () => {
    const out = html(wmBodyRoot());
    // The element carries the override, not the wire's vertical-rl.
    expect(out).toMatch(/writing-mode:horizontal-tb/);
    expect(out).not.toMatch(/writing-mode:vertical-rl/);
    // The root-scope span still renders with its author box untouched.
    expect(out).toContain('data-pseudo="before"');
    expect(out).toMatch(/background:orange/);
  });

  it('an uncontained vertical body-root keeps its emitted vertical-rl', () => {
    const uncontained = wmBodyRoot({
      properties: [{ type: 'WritingMode', data: 'VERTICAL_RL' }],
    });
    expect(html(uncontained)).toMatch(/writing-mode:vertical-rl/);
  });
});
