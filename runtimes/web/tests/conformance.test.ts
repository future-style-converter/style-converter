// conformance.test.ts — web-runtime side of the IR wire contract.
//
// Dual-contract suite for the v2 freeze window (schema/spec/05-versioning.md):
//
//   - IR v2 (primary): every golden under schema/conformance/fixtures/v2/
//     decodes through the REAL web IR path (JSON.parse → decodeIRDocument →
//     buildStyles) and sentinel extractions are asserted per shape family.
//     The component list is FLAT; composition is slot refs (spec 03).
//   - IR v1 (deprecation window): the legacy goldens under
//     schema/conformance/fixtures/ still decode through the SAME gate,
//     which translates them (children→flat+slot, _text→text, _tag/_role→
//     meta, _pseudo→pseudos) with a console deprecation warning.
//
// If this suite breaks, either the goldens changed (a freeze event — see
// schema/spec/05-versioning.md) or the web runtime drifted from the wire.
import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { IRDocument, IRComponent } from '../src/core/ir/IRModels';
import { decodeIRDocument, WIRE_READER_VERSION } from '../src/core/ir/IRDecode';
import { buildStyles } from '../src/core/renderer/StyleBuilder';

// v1 goldens live at repo-root/schema/conformance/fixtures; the v2 mirrors
// (plus the two v2-only goldens) live in its v2/ subdirectory. This file is
// at repo-root/runtimes/web/tests, so hop three levels up.
const FIXTURES_DIR_V1 = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../schema/conformance/fixtures',
);
const FIXTURES_DIR_V2 = path.join(FIXTURES_DIR_V1, 'v2');

// Load one golden through the REAL web load path: fetch-equivalent read,
// JSON.parse, then the irVersion gate (exactly what App.tsx does).
function load(dir: string, name: string): IRDocument {
  return decodeIRDocument(JSON.parse(readFileSync(path.join(dir, name), 'utf8')));
}
const loadV2 = (name: string) => load(FIXTURES_DIR_V2, name);
const loadV1 = (name: string) => load(FIXTURES_DIR_V1, name);

// Find a component in the FLAT decoded list (goldens use unique names) —
// after the gate there is no nesting to walk in either version.
function byName(doc: IRDocument, name: string): IRComponent {
  const found = doc.components.find((c) => c.name === name);
  if (!found) throw new Error(`component ${name} not in fixture`);
  return found;
}

const ALL_V2 = readdirSync(FIXTURES_DIR_V2).filter((f) => f.endsWith('.json')).sort();
const ALL_V1 = readdirSync(FIXTURES_DIR_V1).filter((f) => f.endsWith('.json')).sort();

// The v1 translation path intentionally console.warns per document; keep
// the suite output readable by silencing it for the whole file (the warn
// behaviour itself is asserted in the deprecation-window block below).
beforeAll(() => vi.spyOn(console, 'warn').mockImplementation(() => {}));
afterAll(() => vi.restoreAllMocks());

describe('IR v2 golden fixtures — web runtime conformance', () => {
  it('golden set is complete (12 v1 mirrors + 2 v2-only composition goldens)', () => {
    expect(ALL_V2.length).toBeGreaterThanOrEqual(14);
    // The two v2-only goldens exercise slot composition + placement claims.
    expect(ALL_V2).toContain('slot-composition.json');
    expect(ALL_V2).toContain('placement-claims.json');
  });

  // The umbrella guarantee: every component of every golden decodes and
  // styles without throwing — including unknown property types (spec 05
  // tolerance rule 1) and edge-case data payloads. The list is FLAT and
  // slot refs, when present, point at ids that exist (goldens are checked
  // dangle-free — spec 03 §2).
  it.each(ALL_V2)('%s: every component decodes flat and builds styles', (file) => {
    const doc = loadV2(file);
    expect(doc.irVersion).toBe(2);
    expect(doc.minReaderVersion).toBe(2);
    expect(Array.isArray(doc.components)).toBe(true);
    const ids = new Set(doc.components.map((c) => c.id));
    for (const c of doc.components) {
      expect(typeof c.id).toBe('string');
      expect(c.id.length).toBeGreaterThan(0);
      expect(Array.isArray(c.properties)).toBe(true);
      // v2 components never carry children (hard error at decode).
      expect('children' in (c as unknown as Record<string, unknown>)).toBe(false);
      // slot round-trip: parent resolves, default name reconstructed.
      if (c.slot) {
        expect(ids.has(c.slot.parent)).toBe(true);
        expect(c.slot.name).toBe('content');
      }
      // Must not throw for ANY golden component.
      const styles = buildStyles(c.properties);
      expect(styles).toBeTypeOf('object');
    }
  });

  // ---- per-family sentinels (mirroring the v1 suite on the v2 wire) ----

  it('lengths-all-forms: 16px padding → paddingTop "16px"; percent escape survives', () => {
    const doc = loadV2('lengths-all-forms.json');
    const px = buildStyles(byName(doc, 'Lengths_PxOnly').properties);
    expect(px.paddingTop).toBe('16px');           // {"px":16} → Exact → toCssLength
    expect(px.width).toBe('200px');               // typed {"type":"length","px":200}
    const dual = buildStyles(byName(doc, 'Lengths_DualStorage').properties);
    expect(dual.paddingRight).toBe('16px');       // 12pt normalized to 16px, px wins
    const esc = buildStyles(byName(doc, 'Lengths_PercentAndKeyword').properties);
    expect(esc.paddingLeft).toBe('10%');          // bare number == percentage (spec 02)
  });

  it('colors: hex dual storage → rgba(); dynamic currentColor does not crash', () => {
    const doc = loadV2('colors.json');
    const s = buildStyles(byName(doc, 'Colors_StaticForms').properties);
    // srgb {0.2039,0.5961,0.8588} → 52,152,219 (the #3498db sentinel).
    expect(String(s.backgroundColor)).toMatch(/^rgba\(52, 152, 219/);
    // Special keywords build without throwing (AccentColor has NO srgb).
    const special = buildStyles(byName(doc, 'Colors_SpecialKeywords').properties);
    expect(special).toBeTypeOf('object');
  });

  it('shape-radius: keyword survives verbatim, length radius was deep-flattened', () => {
    const doc = loadV2('shape-radius.json');
    const kw = buildStyles(byName(doc, 'Shape_Circle_Keyword').properties);
    expect(JSON.stringify(kw)).toContain('farthest-side');   // keyword form reaches CSS
    const len = buildStyles(byName(doc, 'Shape_Circle_Length').properties);
    expect(JSON.stringify(len)).toContain('40px');           // {"type":"circle","px":40}
  });

  it('text-and-role: text round-trips; role lives in meta (v2 close of the spec-04 gap)', () => {
    const doc = loadV2('text-and-role.json');
    const body = byName(doc, 'TextRole_BodyRoot');
    expect(body.text).toBe('Test passes if this text is green');
    // v2 groups the droppable hints: _role's successor is meta.role, now a
    // first-class typed field (the v1 model gap documented in spec 04).
    expect(body.meta?.role).toBe('body-root');
    // Empty-string text is a legal, meaningful value — not collapsed to null.
    expect(byName(doc, 'TextRole_EmptyStringText').text).toBe('');
    expect(byName(doc, 'TextRole_NoMetadata').text).toBeUndefined();
    expect(byName(doc, 'TextRole_NoMetadata').meta).toBeUndefined();
  });

  it('children-nesting (v2 mirror): flat entries, slot chain replaces nesting', () => {
    const doc = loadV2('children-nesting.json');
    // Every composition level is a standalone flat entry now.
    const parent = byName(doc, 'Parent');
    const child = byName(doc, 'child__0');            // extractor map key becomes name
    const grandchild = byName(doc, 'grandchild__0__0');
    const sibling = byName(doc, 'LeafSibling');
    // The slot chain expresses what nesting used to: child→parent→(root).
    expect(parent.slot).toBeUndefined();              // root: slot omitted entirely
    expect(child.slot?.parent).toBe(parent.id);
    expect(grandchild.slot?.parent).toBe(child.id);
    expect(sibling.slot).toBeUndefined();             // sibling is a root too
    // Styles still build at depth, and leaf text uses the v2 rename.
    expect(buildStyles(child.properties).width).toBe('50px');
    expect(grandchild.text).toBe('leaf text');
    // Sibling-order rule: parent precedes its slotted child in the flat
    // array (pre-order flattener output — spec 03 §1).
    const idx = (c: IRComponent) => doc.components.findIndex((x) => x.id === c.id);
    expect(idx(parent)).toBeLessThan(idx(child));
    expect(idx(child)).toBeLessThan(idx(grandchild));
  });

  it('selectors-media: buckets carry full property envelopes', () => {
    const doc = loadV2('selectors-media.json');
    const c = byName(doc, 'SelMedia_Both');
    expect(c.selectors![0].condition).toBe('hover');           // no leading colon (spec 01)
    expect(buildStyles(c.selectors![0].properties).backgroundColor).toMatch(/^rgba\(0, 0, 0/);
    expect(c.media![0].query).toBe('(min-width: 768px)');
    expect(buildStyles(c.media![0].properties).width).toBe('100px');
  });

  it('opacity-and-numbers: alpha extracts; FQ discriminator wart is opaque, not fatal', () => {
    const doc = loadV2('opacity-and-numbers.json');
    const s = buildStyles(byName(doc, 'Numbers_Opacity').properties);
    expect(Number(s.opacity)).toBe(0.5);
    // The FlexGrow FQ-class-name nested type (spec 02 known defect 2) must
    // not crash the pipeline.
    expect(buildStyles(byName(doc, 'Numbers_FlexGrow_FqDiscriminatorWart').properties))
      .toBeTypeOf('object');
  });

  it('transform-list: function list serializes into a transform declaration', () => {
    const doc = loadV2('transform-list.json');
    const s = buildStyles(byName(doc, 'Transform_FunctionList').properties);
    const css = JSON.stringify(s);
    expect(css).toContain('translateX(10px)');
    expect(css).toContain('rotate(45deg)');
  });

  it('unknown-property-tolerance: unknown types are skipped, known neighbours still extract', () => {
    const doc = loadV2('unknown-property-tolerance.json');
    const mixed = buildStyles(byName(doc, 'Unknown_MixedWithKnown').properties);
    expect(mixed.width).toBe('100px');                        // Width survives the unknown sibling
    const prim = buildStyles(byName(doc, 'Unknown_PrimitiveData').properties);
    expect(prim.paddingTop).toBe('16px');
    // Generic (_unmapped) envelope builds without output and without throwing.
    expect(buildStyles(byName(doc, 'Unknown_GenericEnvelope').properties)).toBeTypeOf('object');
  });

  it('property-envelope-edge: primitive/array/empty data payloads all decode', () => {
    const doc = loadV2('property-envelope-edge.json');
    const prim = buildStyles(byName(doc, 'Envelope_PrimitiveData').properties);
    expect(prim.marginLeft).toBe('auto');                     // raw-string keyword escape
    expect(prim.fontWeight).toBe(700);                        // raw-number weight passes through
    // Empty-object data and empty properties list are non-events.
    expect(buildStyles(byName(doc, 'Envelope_EmptyObjectData').properties)).toBeTypeOf('object');
    expect(buildStyles(byName(doc, 'Envelope_EmptyPropertiesList').properties)).toEqual({});
  });

  it('background-gradient: layer array renders a linear-gradient declaration', () => {
    const doc = loadV2('background-gradient.json');
    const s = buildStyles(byName(doc, 'Gradient_LinearTwoStop').properties);
    expect(String(s.backgroundImage)).toContain('linear-gradient');
  });

  it('font-weight-keywords: dual-storage keyword and numeric primitive both extract to 700', () => {
    const doc = loadV2('font-weight-keywords.json');
    const numeric = buildStyles(byName(doc, 'FontWeight_NumericPrimitive').properties);
    expect(numeric.fontWeight).toBe(700);
    // Keyword dual-storage form must at minimum not crash; if the engine
    // maps it, it must map to bold/700 (no other value is acceptable).
    const bold = buildStyles(byName(doc, 'FontWeight_BoldKeyword').properties);
    if (bold.fontWeight !== undefined) {
      expect([700, '700', 'bold']).toContain(bold.fontWeight);
    }
  });

  // ---- wave-34 lane F2: document-level @font-face (spec 01 §5) ----

  it('font-faces: the document-level list decodes, order + descriptors intact', () => {
    const doc = loadV2('font-faces.json');
    const faces = doc.fontFaces!;
    // ORDER is payload: css-fonts-4 §4.1 makes a later face with the same
    // (family, weight, style) win, so a reordering decode would silently
    // change which file renders.
    expect(faces).toHaveLength(2);
    expect(faces[0]).toEqual({
      family: 'test',
      src: 'css/css-text/boundary-shaping/resources/LinLibertine_Re-4.7.5.woff',
    });
    // Omitted descriptors stay ABSENT — never substituted with the
    // §4.4/§4.5 initial literal, so a consumer applying the spec initial and
    // one reading an explicit value land on the same face.
    expect(faces[0].weight).toBeUndefined();
    // The descriptors that cannot be normalised ride AS AUTHORED: a weight
    // RANGE has no numeric 100–900 form, an oblique ANGLE no keyword form.
    expect(faces[1].weight).toBe('400 700');
    expect(faces[1].style).toBe('oblique 20deg');
  });

  it('font-faces: absent on every pre-wave-34 golden (omit-when-empty)', () => {
    // The additive-key guarantee — a document that declares no face must be
    // byte-identical to before the key existed.
    expect(loadV2('text-and-role.json').fontFaces).toBeUndefined();
    expect(loadV1('text-and-role.json').fontFaces).toBeUndefined();
  });

  it('font-faces: malformed entries are dropped, never half-decoded', () => {
    // Decode-side tolerance posture (schema validation is CI's job): an
    // entry missing a REQUIRED descriptor names no renderable face, so
    // dropping beats inventing a default. Whole-list absence when nothing
    // survives keeps a re-encode shape-faithful.
    const decode = (fontFaces: unknown) =>
      decodeIRDocument({ irVersion: 2, minReaderVersion: 2, components: [], fontFaces })
        .fontFaces;
    expect(decode([{ src: 'a.woff' }])).toBeUndefined();          // no family
    expect(decode([{ family: 'x' }])).toBeUndefined();            // no src
    expect(decode([{ family: '', src: 'a.woff' }])).toBeUndefined();
    expect(decode([])).toBeUndefined();
    expect(decode({})).toBeUndefined();                            // wrong container
    // A valid entry alongside a malformed one survives alone.
    expect(decode([{ family: 'x' }, { family: 'y', src: 'b.woff' }]))
      .toEqual([{ family: 'y', src: 'b.woff' }]);
    // Non-string weight/style are dropped rather than coerced — "400" and
    // 400 would otherwise become the same face descriptor.
    expect(decode([{ family: 'y', src: 'b.woff', weight: 700, style: '' }]))
      .toEqual([{ family: 'y', src: 'b.woff' }]);
  });

  // ---- v2-only composition goldens ----

  it('slot-composition: 3-level tree via slots, 6 components, order-stable', () => {
    const doc = loadV2('slot-composition.json');
    expect(doc.components).toHaveLength(6);
    const root = byName(doc, 'SlotRoot');
    // Roots are slot-free; every other entry chains to the root.
    expect(root.slot).toBeUndefined();
    expect(byName(doc, 'band__0').slot?.parent).toBe(root.id);
    expect(byName(doc, 'cell__0__0').slot?.parent).toBe(byName(doc, 'band__0').id);
    expect(byName(doc, 'band__1').slot?.parent).toBe(root.id);
    expect(byName(doc, 'cell__1__0').slot?.parent).toBe(byName(doc, 'band__1').id);
    // Engine stays composition-agnostic: styles build per component with
    // no knowledge of the slot graph.
    expect(buildStyles(root.properties).display).toBeDefined();
  });

  it('placement-claims: ITEM-scoped props are ordinary envelopes on the CHILD', () => {
    const doc = loadV2('placement-claims.json');
    // Grid claims: grid-row/column lines live on the child component.
    const line = byName(doc, 'line-claim__1');
    expect(line.slot?.parent).toBe(byName(doc, 'PlacementGridHost').id);
    expect(line.properties.map((p) => p.type)).toContain('GridColumnStart');
    // Flex claims: grow/shrink/basis/align-self/order on the child.
    const flex = byName(doc, 'flex-claim__0');
    const flexTypes = flex.properties.map((p) => p.type);
    for (const t of ['FlexGrow', 'FlexShrink', 'FlexBasis', 'AlignSelf', 'Order']) {
      expect(flexTypes).toContain(t);
    }
    // The web engine emits them as the component's OWN flat declarations —
    // the browser cascade performs the container/item resolution natively.
    const s = buildStyles(flex.properties);
    expect(JSON.stringify(s)).toContain('flexGrow');
    // Paint + positioned claims build too (z-index, insets on the child).
    expect(buildStyles(byName(doc, 'paint-claim__1').properties)).toBeTypeOf('object');
    expect(buildStyles(byName(doc, 'abs-claim__2').properties)).toBeTypeOf('object');
  });

  // wave-32 lane R — the ordered inline-content list. The DECODE is the
  // unit here (the render plan is pinned in InlineRuns.test.ts): a reader
  // that dropped or rejected `meta.runs` would make the renderer's whole
  // interleave path dead and silent, which is the failure this catches.
  it('inline-runs: meta.runs decodes verbatim, in document order', () => {
    const doc = loadV2('inline-runs.json');
    // `the quick <u>brown</u> fox` — a child GLUED between two text runs,
    // the shape `text` + sibling order cannot express (spec 03 §4.1).
    const glued = byName(doc, 'Runs_TextGluedAcrossChild');
    expect(glued.meta?.runs).toEqual([
      { text: 'the quick ' },
      { child: 'Runs_UnderlinedWord' },
      { text: ' fox' },
    ]);
    // `text` is STILL on the wire carrying the pre-wave-32 concatenation —
    // that is exactly what makes meta.runs additive rather than a break.
    expect(glued.text).toBe('the quick fox');
    // The reference is the AUTHORING KEY (the child's `name`), never the
    // converter-minted id — pin both spellings so a future id-based
    // rewrite of the emitter fails here instead of in a capture.
    const underlined = byName(doc, 'Runs_UnderlinedWord');
    expect(glued.meta?.runs?.[1].child).toBe(underlined.name);
    expect(glued.meta?.runs?.[1].child).not.toBe(underlined.id);
    // A child BEFORE the text: the CSS2 static-inside-inline shape, where
    // the whole assertion under test is box-vs-text order.
    expect(byName(doc, 'Runs_ChildBeforeText').meta?.runs?.[0]).toEqual({
      child: 'Runs_AbsposBox',
    });
    // A whitespace-only run between two children is the inter-run word
    // space (rule 6) and must survive decode un-trimmed.
    expect(byName(doc, 'Runs_WhitespaceOnlyRunBetweenChildren').meta?.runs?.[1]).toEqual({ text: ' ' });
    // Omit-when-absent: a component with no interleave carries no key.
    expect(underlined.meta?.runs).toBeUndefined();
  });

  // ---- gate rules (spec 05) ----

  it('gate: refuses a document whose minReaderVersion exceeds the runtime', () => {
    expect(() =>
      decodeIRDocument({ irVersion: 3, minReaderVersion: 3, components: [] }),
    ).toThrow(/reader version 3/);
    // Sanity: this runtime implements exactly wire version 2.
    expect(WIRE_READER_VERSION).toBe(2);
  });

  it('gate: children inside a v2 document is a hard decode error', () => {
    expect(() =>
      decodeIRDocument({
        irVersion: 2,
        minReaderVersion: 2,
        components: [{ id: 'a-001', name: 'A', properties: [], children: [] }],
      }),
    ).toThrow(/children/);
  });

  it('gate: is idempotent (decoding a decoded doc is a no-op)', () => {
    const once = loadV2('slot-composition.json');
    const twice = decodeIRDocument(once as unknown);
    expect(twice).toEqual(once);
  });
});

describe('IR v1 golden fixtures — deprecation-window translation', () => {
  it('v1 golden set is still present and complete during the window', () => {
    expect(ALL_V1.length).toBeGreaterThanOrEqual(12);
  });

  // Every legacy golden must still decode through the gate: translated to
  // the flat v2 shape, styles building for every (now flat) component.
  it.each(ALL_V1)('%s: translates to flat v2 and builds styles', (file) => {
    const doc = loadV1(file);
    expect(doc.irVersion).toBe(2);                     // canonical output
    for (const c of doc.components) {
      expect('children' in (c as unknown as Record<string, unknown>)).toBe(false);
      expect(buildStyles(c.properties)).toBeTypeOf('object');
    }
  });

  it('children-nesting: nesting becomes pre-order flat entries + slot refs', () => {
    const doc = loadV1('children-nesting.json');
    // 4 nested components flatten to 4 flat entries (pre-order).
    const parent = byName(doc, 'Parent');
    const child = byName(doc, 'child__0');
    const grandchild = doc.components.find((c) => c.text === 'leaf text')!;
    expect(child.slot).toEqual({ parent: parent.id, name: 'content' });
    expect(grandchild.slot).toEqual({ parent: child.id, name: 'content' });
    expect(byName(doc, 'LeafSibling').slot).toBeUndefined();
    // Pre-order: parent before child before grandchild (IRFlattener parity).
    const idx = (c: IRComponent) => doc.components.indexOf(c);
    expect(idx(parent)).toBeLessThan(idx(child));
    expect(idx(child)).toBeLessThan(idx(grandchild));
  });

  it('text-and-role: _text/_role translate to text/meta.role', () => {
    const doc = loadV1('text-and-role.json');
    const body = byName(doc, 'TextRole_BodyRoot');
    expect(body.text).toBe('Test passes if this text is green');
    expect(body.meta?.role).toBe('body-root');
    // Empty-string _text stays a meaningful empty string after translation.
    expect(byName(doc, 'TextRole_EmptyStringText').text).toBe('');
    expect(byName(doc, 'TextRole_NoMetadata').text).toBeUndefined();
  });

  it('emits exactly one deprecation warning per translated document', () => {
    const warn = vi.mocked(console.warn);
    warn.mockClear();
    loadV1('colors.json');
    // One document → one warning, mentioning the legacy wire.
    expect(warn).toHaveBeenCalledTimes(1);
    expect(String(warn.mock.calls[0][0])).toMatch(/v1 document/);
  });
});
