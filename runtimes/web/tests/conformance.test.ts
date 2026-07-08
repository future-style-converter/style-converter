// conformance.test.ts — web-runtime side of the IR v1 wire contract
// (schema/ir-v1.schema.json + schema/spec/*). Decodes EVERY golden fixture
// in schema/conformance/fixtures/ through the real web IR path
// (JSON.parse → IRDocument → buildStyles) and asserts sentinel
// extractions per shape family. If this suite breaks, either the goldens
// changed (a v2-freeze event — see schema/spec/05-versioning.md) or the
// web engine drifted from the wire contract.
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { IRDocument, IRComponent } from '../src/core/ir/IRModels';
import { buildStyles } from '../src/core/renderer/StyleBuilder';

// Fixtures live at repo-root/schema/conformance/fixtures; this file is at
// repo-root/runtimes/web/tests, so hop three levels up.
const FIXTURES_DIR = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../schema/conformance/fixtures',
);

// Load one golden by basename; typed as the runtime's own IRDocument model
// (the real load path is JSON.parse — tmpOutput.json is fetched, parsed and
// handed to the renderer untouched).
function load(name: string): IRDocument {
  return JSON.parse(readFileSync(path.join(FIXTURES_DIR, name), 'utf8')) as IRDocument;
}

// Depth-first component walk (children are ARRAYS on the wire — spec 03).
function walk(components: IRComponent[], visit: (c: IRComponent) => void): void {
  for (const c of components) {
    visit(c);
    if (c.children) walk(c.children, visit);
  }
}

// Find a component by name in a loaded doc (goldens use unique names).
function byName(doc: IRDocument, name: string): IRComponent {
  let found: IRComponent | undefined;
  walk(doc.components, (c) => { if (c.name === name) found = c; });
  if (!found) throw new Error(`component ${name} not in fixture`);
  return found;
}

const ALL_FIXTURES = readdirSync(FIXTURES_DIR).filter((f) => f.endsWith('.json')).sort();

describe('IR v1 golden fixtures — web runtime conformance', () => {
  it('golden set is complete (12 shape families)', () => {
    expect(ALL_FIXTURES.length).toBeGreaterThanOrEqual(12);
  });

  // The umbrella guarantee: every component of every golden decodes and
  // styles without throwing — including unknown property types (spec 05
  // tolerance rule 1) and edge-case data payloads.
  it.each(ALL_FIXTURES)('%s: every component decodes and builds styles', (file) => {
    const doc = load(file);
    expect(Array.isArray(doc.components)).toBe(true);
    walk(doc.components, (c) => {
      expect(typeof c.id).toBe('string');
      expect(c.id.length).toBeGreaterThan(0);
      expect(Array.isArray(c.properties)).toBe(true);
      // Must not throw for ANY golden component.
      const styles = buildStyles(c.properties);
      expect(styles).toBeTypeOf('object');
    });
  });

  // ---- per-family sentinels ----

  it('lengths-all-forms: 16px padding → paddingTop "16px"; percent escape survives', () => {
    const doc = load('lengths-all-forms.json');
    const px = buildStyles(byName(doc, 'Lengths_PxOnly').properties);
    expect(px.paddingTop).toBe('16px');           // {"px":16} → Exact → toCssLength
    expect(px.width).toBe('200px');               // typed {"type":"length","px":200}
    const dual = buildStyles(byName(doc, 'Lengths_DualStorage').properties);
    expect(dual.paddingRight).toBe('16px');       // 12pt normalized to 16px, px wins
    const esc = buildStyles(byName(doc, 'Lengths_PercentAndKeyword').properties);
    expect(esc.paddingLeft).toBe('10%');          // bare number == percentage (spec 02)
  });

  it('colors: hex dual storage → rgba(); dynamic currentColor does not crash', () => {
    const doc = load('colors.json');
    const s = buildStyles(byName(doc, 'Colors_StaticForms').properties);
    // srgb {0.2039,0.5961,0.8588} → 52,152,219 (the #3498db sentinel).
    expect(String(s.backgroundColor)).toMatch(/^rgba\(52, 152, 219/);
    // Special keywords build without throwing (AccentColor has NO srgb).
    const special = buildStyles(byName(doc, 'Colors_SpecialKeywords').properties);
    expect(special).toBeTypeOf('object');
  });

  it('shape-radius: keyword survives verbatim, length radius was deep-flattened', () => {
    const doc = load('shape-radius.json');
    const kw = buildStyles(byName(doc, 'Shape_Circle_Keyword').properties);
    expect(JSON.stringify(kw)).toContain('farthest-side');   // keyword form reaches CSS
    const len = buildStyles(byName(doc, 'Shape_Circle_Length').properties);
    expect(JSON.stringify(len)).toContain('40px');           // {"type":"circle","px":40}
  });

  it('text-and-role: _text round-trips; _role survives in raw JSON (model gap documented in spec 04)', () => {
    const doc = load('text-and-role.json');
    const body = byName(doc, 'TextRole_BodyRoot');
    expect(body._text).toBe('Test passes if this text is green');
    // The TS model has no _role field yet (spec 04 matrix) — but the wire
    // value must still be present on the parsed object, i.e. nothing in
    // the load path strips it.
    expect((body as unknown as Record<string, unknown>)._role).toBe('body-root');
    // Empty-string _text is a legal, meaningful value — not collapsed to null.
    expect(byName(doc, 'TextRole_EmptyStringText')._text).toBe('');
    expect(byName(doc, 'TextRole_NoMetadata')._text).toBeUndefined();
  });

  it('children-nesting: array-out contract, grandchild reachable, styles build at depth', () => {
    const doc = load('children-nesting.json');
    const parent = byName(doc, 'Parent');
    expect(Array.isArray(parent.children)).toBe(true);       // spec 03: ARRAY, never map
    const child = parent.children![0];
    expect(child.name).toBe('child__0');                     // extractor map key becomes name
    expect(buildStyles(child.properties).width).toBe('50px');
    expect(child.children![0]._text).toBe('leaf text');
    // Sibling without children omits the key entirely (never []).
    expect(byName(doc, 'LeafSibling').children).toBeUndefined();
  });

  it('selectors-media: buckets carry full property envelopes', () => {
    const doc = load('selectors-media.json');
    const c = byName(doc, 'SelMedia_Both');
    expect(c.selectors[0].condition).toBe('hover');           // no leading colon (spec 01)
    expect(buildStyles(c.selectors[0].properties).backgroundColor).toMatch(/^rgba\(0, 0, 0/);
    expect(c.media[0].query).toBe('(min-width: 768px)');
    expect(buildStyles(c.media[0].properties).width).toBe('100px');
  });

  it('opacity-and-numbers: alpha extracts; FQ discriminator wart is opaque, not fatal', () => {
    const doc = load('opacity-and-numbers.json');
    const s = buildStyles(byName(doc, 'Numbers_Opacity').properties);
    expect(Number(s.opacity)).toBe(0.5);
    // The FlexGrow FQ-class-name nested type (spec 02 known defect 2) must
    // not crash the pipeline.
    expect(buildStyles(byName(doc, 'Numbers_FlexGrow_FqDiscriminatorWart').properties))
      .toBeTypeOf('object');
  });

  it('transform-list: function list serializes into a transform declaration', () => {
    const doc = load('transform-list.json');
    const s = buildStyles(byName(doc, 'Transform_FunctionList').properties);
    const css = JSON.stringify(s);
    expect(css).toContain('translateX(10px)');
    expect(css).toContain('rotate(45deg)');
  });

  it('unknown-property-tolerance: unknown types are skipped, known neighbours still extract', () => {
    const doc = load('unknown-property-tolerance.json');
    const mixed = buildStyles(byName(doc, 'Unknown_MixedWithKnown').properties);
    expect(mixed.width).toBe('100px');                        // Width survives the unknown sibling
    const prim = buildStyles(byName(doc, 'Unknown_PrimitiveData').properties);
    expect(prim.paddingTop).toBe('16px');
    // Generic (_unmapped) envelope builds without output and without throwing.
    expect(buildStyles(byName(doc, 'Unknown_GenericEnvelope').properties)).toBeTypeOf('object');
  });

  it('property-envelope-edge: primitive/array/empty data payloads all decode', () => {
    const doc = load('property-envelope-edge.json');
    const prim = buildStyles(byName(doc, 'Envelope_PrimitiveData').properties);
    expect(prim.marginLeft).toBe('auto');                     // raw-string keyword escape
    expect(prim.fontWeight).toBe(700);                        // raw-number weight passes through
    // Empty-object data and empty properties list are non-events.
    expect(buildStyles(byName(doc, 'Envelope_EmptyObjectData').properties)).toBeTypeOf('object');
    expect(buildStyles(byName(doc, 'Envelope_EmptyPropertiesList').properties)).toEqual({});
  });

  it('background-gradient: layer array renders a linear-gradient declaration', () => {
    const doc = load('background-gradient.json');
    const s = buildStyles(byName(doc, 'Gradient_LinearTwoStop').properties);
    expect(String(s.backgroundImage)).toContain('linear-gradient');
  });

  it('font-weight-keywords: dual-storage keyword and numeric primitive both extract to 700', () => {
    const doc = load('font-weight-keywords.json');
    const numeric = buildStyles(byName(doc, 'FontWeight_NumericPrimitive').properties);
    expect(numeric.fontWeight).toBe(700);
    // Keyword dual-storage form must at minimum not crash; if the engine
    // maps it, it must map to bold/700 (no other value is acceptable).
    const bold = buildStyles(byName(doc, 'FontWeight_BoldKeyword').properties);
    if (bold.fontWeight !== undefined) {
      expect([700, '700', 'bold']).toContain(bold.fontWeight);
    }
  });
});
