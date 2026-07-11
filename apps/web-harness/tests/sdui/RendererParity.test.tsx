// RendererParity.test.tsx — the harness-parity gate for issue #41 (the
// "one renderer core, two skins" refactor).
//
// CONTRACT: the harness ComponentRenderer used to be a self-contained
// implementation; it is now a thin calibration wrapper around the package
// renderer core (@style-converter/web/renderer). The wrapper MUST produce
// BYTE-IDENTICAL HTML to the pre-refactor renderer for every capture-
// relevant shape — that is what keeps the committed visual baselines
// (tools/visual/baseline/) byte-stable without re-rendering a pixel.
//
// The golden file (__fixtures__/renderer-parity-golden.json) was generated
// by running THIS file with REGEN_PARITY_GOLDEN=1 against the ORIGINAL
// pre-refactor renderer (git history: the last commit where
// ComponentRenderer.tsx carried its own render body). Regenerating it
// against the wrapper would make the test tautological — never regenerate
// unless the harness DOM contract itself intentionally changes (which is
// a visual-baseline event, not a refactor).
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

// Golden path is anchored to this test file so vitest cwd never matters.
const GOLDEN_PATH = join(
  dirname(fileURLToPath(import.meta.url)),
  '__fixtures__',
  'renderer-parity-golden.json',
);

// Helper: v2 IRComponent without spelling out the full scaffolding.
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'parity-id', name: 'Parity_Comp', properties: [], ...overrides };
}
// Helper: wrap into the ComposedNode shape the renderer consumes.
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
// Helper: typed IR property envelope.
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}
// Spec-02 pre-resolved sRGB color payload.
const srgb = (r: number, g: number, b: number) => ({ srgb: { r, g, b } });
const len = (px: number) => ({ type: 'length', px });

/**
 * The representative fixture set — every calibrated harness behaviour that
 * shapes capture DOM gets at least one exact-HTML pin:
 * fit-content/floor defaults, placeholder text + contrast, mixed content,
 * tag allowlist + demotion, the img placeholder branch, both aspect-ratio
 * carve-outs, bucket-declared-sizing floor collapse, empty grid demotion,
 * min/max floor interplay, variables, pseudos, display:none suppression.
 * (WPT_MODE / FORCE_STATE are module-load URL constants — their branches
 * are pinned by ComponentRenderer.swarm003.test.tsx / DynamicRules.test.tsx.)
 */
const CASES: Record<string, ComposedNode> = {
  // 327-pair placeholder baseline: name text + fit-content + 50/30 floors.
  plain_placeholder: node(comp()),
  // Placeholder over a light background with an explicit text color —
  // exercises the (untouched) PlaceholderContent contrast/inherit logic.
  light_bg_explicit_color: node(comp({
    id: 'light-1',
    properties: [prop('BackgroundColor', srgb(1, 1, 1)), prop('Color', srgb(0.906, 0.298, 0.235))],
  })),
  // Childless with real text — text wins over the placeholder name.
  childless_text: node(comp({ id: 'text-1', text: 'Test passes if this is green' })),
  // Mixed content: leading parent text in an inheriting <span>, then child.
  mixed_content: node(
    comp({ id: 'mix-p', name: 'Mix_Parent', text: 'abc def' }),
    [node(comp({ id: 'mix-c', name: 'Mix_Child', text: 'x' }))],
  ),
  // Two-level nesting, children only (the composed-recursion baseline).
  nested_children: node(
    comp({ id: 'n-root', name: 'Nest_Root' }),
    [node(comp({ id: 'n-mid', name: 'Nest_Mid' }), [node(comp({ id: 'n-leaf', name: 'Nest_Leaf' }))])],
  ),
  // sourceTag mapping: <ol> hosting an <li> child.
  source_tag_list: node(
    comp({ id: 'ol-1', name: 'List', meta: { sourceTag: 'ol' } }),
    [node(comp({ id: 'li-1', name: 'Item', meta: { sourceTag: 'li' }, text: 'first' }))],
  ),
  // sourceTag: inline span (swarm-003 Bug 2 allowlist entry).
  source_tag_span: node(comp({ id: 'span-1', meta: { sourceTag: 'span' }, text: 'inline' })),
  // sourceTag outside the harness allowlist demotes to <div>.
  source_tag_demoted: node(comp({ id: 'demote-1', meta: { sourceTag: 'iframe' } })),
  // The issue-#36 img branch: real <img>, deterministic placeholder src.
  img_placeholder: node(comp({
    id: 'img-1', name: 'Img_Basic', meta: { sourceTag: 'img' }, text: 'a kitten',
    properties: [prop('Width', len(120)), prop('Height', len(80))],
  })),
  // Aspect-ratio inline-axis carve-out (block-aspect-ratio-032 shape).
  aspect_inline_unconstrained: node(comp({
    id: 'ar-1',
    properties: [
      prop('AspectRatio', { ratio: { w: 4, h: 1 }, normalizedRatio: 4 }),
      prop('Height', len(300)), prop('MaxHeight', len(25)),
    ],
  })),
  // Aspect-ratio block-axis carve-out WITH a child (min-content lift, 015).
  aspect_block_lift: node(
    comp({
      id: 'ar-2',
      properties: [
        prop('AspectRatio', { ratio: { w: 2, h: 1 }, normalizedRatio: 2 }),
        prop('Width', len(100)),
      ],
    }),
    [node(comp({ id: 'ar-2c', properties: [prop('Height', len(80))] }))],
  ),
  // Empty grid container demotes to display:block (childless demotion).
  empty_grid_demoted: node(comp({ id: 'grid-1', properties: [prop('Display', 'grid')] })),
  // Grid WITH children keeps its display (no demotion).
  grid_with_children: node(
    comp({ id: 'grid-2', properties: [prop('Display', 'grid')] }),
    [node(comp({ id: 'grid-2c' }))],
  ),
  // Selector bucket redeclares sizing → size-derived floors collapse to 0.
  bucket_sizing_floor: node(comp({
    id: 'bucket-1',
    properties: [prop('Width', len(200)), prop('Height', len(40))],
    selectors: [{ condition: 'hover', properties: [prop('Width', len(120))] }],
  })),
  // width+max-width: floor must resolve to '0' so max-width wins the clamp.
  max_width_clamp: node(comp({
    id: 'clamp-1',
    properties: [prop('Width', len(300)), prop('MaxWidth', len(50))],
  })),
  // explicit min-height beats the height-derived floor.
  min_height_explicit: node(comp({
    id: 'minh-1',
    properties: [prop('Height', len(20)), prop('MinHeight', len(120))],
  })),
  // Custom-property definitions land as --name inline keys on the element.
  variables_inline: node(comp({
    id: 'vars-1',
    variables: { '--brand': '#e74c3c', '--pad': '12px' },
  })),
  // Pseudo trio around host text on a <li> (swarm-003 Bug 4 ordering).
  pseudos_trio: node(comp({
    id: 'pseudo-1', text: 'item text', meta: { sourceTag: 'li' },
    pseudos: {
      marker: { id: 'm-1', properties: [], _text: '1.' },
      before: { id: 'b-1', properties: [prop('Color', srgb(1, 0, 0))], _text: 'B' },
      after: { id: 'a-1', properties: [], _text: 'E' },
    },
  })),
  // display:none renders nothing at all (harness suppression).
  display_none: node(comp({ id: 'none-1', properties: [prop('Display', 'none')] })),
};

// Render every case through the CURRENT harness renderer.
function renderAll(): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [name, n] of Object.entries(CASES)) {
    out[name] = renderToStaticMarkup(<ComponentRenderer node={n} />);
  }
  return out;
}

describe('harness renderer parity (issue #41 — one core, two skins)', () => {
  if (process.env.REGEN_PARITY_GOLDEN === '1') {
    // Regeneration mode — run ONLY against the pre-refactor renderer (see
    // the header comment). Writes the golden and passes vacuously.
    it('regenerates the golden file', () => {
      writeFileSync(GOLDEN_PATH, JSON.stringify(renderAll(), null, 2) + '\n');
      expect(true).toBe(true);
    });
    return;
  }

  const golden: Record<string, string> = JSON.parse(readFileSync(GOLDEN_PATH, 'utf8'));

  it('covers every golden case (no silent fixture drift)', () => {
    expect(Object.keys(renderAll()).sort()).toEqual(Object.keys(golden).sort());
  });

  for (const name of Object.keys(CASES)) {
    it(`byte-identical HTML: ${name}`, () => {
      // EXACT string equality — attribute order, style-key order, text,
      // everything. This is the byte-parity contract for the captures.
      expect(renderToStaticMarkup(<ComponentRenderer node={CASES[name]} />)).toBe(golden[name]);
    });
  }
});
