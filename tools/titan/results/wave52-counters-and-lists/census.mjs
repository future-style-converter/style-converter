#!/usr/bin/env node
// tools/titan/results/wave52-counters-and-lists/census.mjs — lane L6's OWN
// corpus census (§0 rule ii of the plan: "a corpus census over the 1435
// per-test IR docs with the grep shape its brief gives, re-derived by a
// skeptic with its own script before it is believed"). Read-only over
//   tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json   (the wire)
//   tools/titan/runs/wave51-fix/sections/*/manifest.json         (the cells, via score-gate.mjs loadRun)
//   tools/wpt/css/**                                              (the authored sources — gitignored, present on the gate host)
//   fixtures/wpt/**                                               (the extractor fixtures the T7 bake runs on)
// and writes census.json beside it. Nothing is built, captured or scored.
//
// Usage: node tools/titan/results/wave52-counters-and-lists/census.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix');
const { loadRun } = await import(path.join(ROOT, 'tools/titan/score-gate.mjs'));
const { bakeCounterStyles } = await import(path.join(ROOT, 'tools/titan/counter-style-bake.mjs'));
// The web runtime's OWN T3 rule (Node >= 22.18 strips the erasable TS types),
// so the census classifies carriers by the code that ships, not a re-statement.
const { bakedMarkerPlan } = await import(path.join(ROOT, 'runtimes/web/src/engine/lists/ListStyleTypeApplier.ts'));

// ── 1. every per-test IR document, flattened ────────────────────────────────
const docs = [];
for (const sec of fs.readdirSync(path.join(RUN, 'sections')).sort()) {
  const dir = path.join(RUN, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
    const stem = f.replace(/^wpt__/, '').replace(/\.json$/, '');   // css-counter-styles__cssom__cssom-pad-setter-invalid
    const parts = stem.split('__');
    const key = `css/${parts.join('/')}.html`;                        // the manifest key shape
    const ir = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    docs.push({ sec, stem, key, parts, comps: ir.components ?? [] });
  }
}
const byId = (comps) => new Map(comps.map((c) => [c.id, c]));
const prop = (c, type) => c.properties?.find((p) => p.type === type)?.data;
const hasProp = (c, type) => c.properties?.some((p) => p.type === type) ?? false;
const srcPath = (parts) => path.join(ROOT, 'tools/wpt/css', ...parts) + '.html';
const src = (d) => (fs.existsSync(srcPath(d.parts)) ? fs.readFileSync(srcPath(d.parts), 'utf8') : null);

// ── 2. the cells of record ──────────────────────────────────────────────────
const run = loadRun(RUN);
function cells(pattern) {
  const out = [];
  for (const [sec, s] of Object.entries(run.sections)) {
    for (const [k, v] of s.cells) {
      const [test, platform] = k.split('|');
      if (`${sec}/${test}`.includes(pattern)) out.push({ cell: `${sec}/${test} ${platform}`, verdict: v.pass ? 'P' : 'f', ssim: v.ssim });
    }
  }
  return out.sort((a, b) => a.cell.localeCompare(b.cell));
}
/** The cells of ONE test, matched EXACTLY on the manifest key (`css/<path>.html`)
 *  — `cells(pattern)` is the scorer's substring rule and over-matches a test
 *  whose name prefixes another (`cssom-pad-setter` is inside `cssom-pad-setter-invalid`). */
function cellsOf(key) {
  const out = [];
  for (const [sec, s] of Object.entries(run.sections)) {
    for (const [k, v] of s.cells) {
      const [test, platform] = k.split('|');
      if (test === key) out.push({ cell: `${sec}/${test} ${platform}`, verdict: v.pass ? 'P' : 'f', ssim: v.ssim });
    }
  }
  return out.sort((a, b) => a.cell.localeCompare(b.cell));
}
let scored = 0;
for (const s of Object.values(run.sections)) scored += s.cells.size;

// ── 3. the per-fix greps ────────────────────────────────────────────────────
const census = { _note: 'wave 52, lane L6 counters-and-lists — own census over the wave51-fix per-test IR (see header).', docs: docs.length,
  components: docs.reduce((n, d) => n + d.comps.length, 0), scoredCells: scored };

// T1: ListStyleType data "korean-hangul-formal" (the Compose twin's carriers).
census.T1_korean_hangul_formal = { carriers: docs.flatMap((d) => d.comps.filter((c) => String(prop(c, 'ListStyleType') ?? '').toLowerCase() === 'korean-hangul-formal')
  .map((c) => `${d.key} ${c.name}`)), cells: cells('css-counter-styles/counter-suffix') };

// T2: a child with a visible bottom/right border whose PARENT declares an exact px Height
// smaller than that border width and the child has no own exact Height → Compose's layout
// height falls below the band width (the BorderSideApplier clamp's carriers).
const px = (v) => (v && typeof v === 'object' && typeof v.px === 'number' ? v.px : null);
const bw = (c, side) => {
  const style = String(prop(c, `Border${side}Style`) ?? '').toUpperCase();
  if (!style || style === 'NONE' || style === 'HIDDEN') return 0;
  const w = px(prop(c, `Border${side}Width`));
  return w ?? 3;                                                     // `medium` when only the style is declared
};
const t2 = [];
for (const d of docs) {
  const ids = byId(d.comps);
  for (const c of d.comps) {
    const parent = c.slot?.parent ? ids.get(c.slot.parent) : null;
    if (!parent) continue;
    const ph = px(prop(parent, 'Height'));
    if (ph === null || hasProp(c, 'Height')) continue;
    const bottom = bw(c, 'Bottom');
    if (bottom > ph) t2.push({ test: d.key, component: c.name, side: 'BOTTOM', via: 'parent Height', parentPx: ph, borderPx: bottom });
  }
}
// The END-side twin of the same clamp: a parent's exact Width below the
// child's right border (the first census compared the RIGHT border with the
// parent's HEIGHT — wrong axis), and a box whose OWN exact Height / Width is
// below its own far border (box-sizing: border-box clamps its content to 0,
// css-sizing-3 §5.1, so Compose may lay it out shorter than the stroke).
const t2b = [];
for (const d of docs) {
  const ids = byId(d.comps);
  for (const c of d.comps) {
    const parent = c.slot?.parent ? ids.get(c.slot.parent) : null;
    const pw = parent ? px(prop(parent, 'Width')) : null;
    const right = bw(c, 'Right'), bottom = bw(c, 'Bottom');
    if (pw !== null && !hasProp(c, 'Width') && right > pw) t2b.push({ test: d.key, component: c.name, side: 'END', via: 'parent Width', parentPx: pw, borderPx: right });
    const oh = px(prop(c, 'Height')), ow = px(prop(c, 'Width'));
    if (oh !== null && bottom > oh) t2b.push({ test: d.key, component: c.name, side: 'BOTTOM', via: 'own Height', ownPx: oh, borderPx: bottom });
    if (ow !== null && right > ow) t2b.push({ test: d.key, component: c.name, side: 'END', via: 'own Width', ownPx: ow, borderPx: right });
  }
}
census.T2_zero_tall_border_band = { rows: t2.length, tests: [...new Set(t2.map((r) => r.test))], rowsDetail: t2,
  extended: { rows: t2b.length, tests: [...new Set(t2b.map((r) => r.test))], rowsDetail: t2b,
    cells: [...new Set(t2b.map((r) => r.test))].flatMap((k) => cellsOf(k)).filter((x) => x.cell.endsWith(' android')) },
  cells: [...cells('CSS2/floats-clear/floats-clear-multicol-003'), ...cells('CSS2/floats-clear/floats-clear-multicol-balancing-003'), ...cells('css-display/display-contents-dynamic-generated-content-fieldset-001')] };

// T3: web consumes meta.markerText for (a) the five range-limited additive styles, (b) author names.
// The effective type is the component's own ListStyleType else its nearest ancestor's (css-lists-3 §3.1).
const RANGE_LIMITED = new Set(['armenian', 'upper-armenian', 'lower-armenian', 'georgian', 'hebrew']);
const PREDEFINED_KW = new Set(['none', 'disc', 'circle', 'square', 'disclosure-open', 'disclosure-closed', 'decimal', 'decimal-leading-zero', 'arabic-indic', 'armenian', 'upper-armenian', 'lower-armenian', 'bengali', 'cambodian', 'khmer', 'cjk-decimal', 'devanagari', 'georgian', 'gujarati', 'gurmukhi', 'hebrew', 'kannada', 'lao', 'malayalam', 'mongolian', 'myanmar', 'oriya', 'persian', 'lower-roman', 'upper-roman', 'tamil', 'telugu', 'thai', 'tibetan', 'lower-alpha', 'lower-latin', 'upper-alpha', 'upper-latin', 'lower-greek', 'hiragana', 'hiragana-iroha', 'katakana', 'katakana-iroha', 'japanese-informal', 'japanese-formal', 'korean-hangul-formal', 'korean-hanja-informal', 'korean-hanja-formal', 'simp-chinese-informal', 'simp-chinese-formal', 'trad-chinese-informal', 'trad-chinese-formal', 'cjk-earthly-branch', 'cjk-heavenly-stem', 'ethiopic-numeric']);
function effectiveType(c, ids) {
  for (let n = c; n; n = n.slot?.parent ? ids.get(n.slot.parent) : null) {
    const t = prop(n, 'ListStyleType');
    if (typeof t === 'string') return t.toLowerCase();
  }
  return undefined;
}
const markerCarriers = [], t3Narrow = [], t3Author = [], t3Plan = [];
for (const d of docs) {
  const ids = byId(d.comps);
  const parents = new Set(d.comps.map((c) => c.slot?.parent).filter(Boolean));
  for (const c of d.comps) {
    if (typeof c.meta?.markerText !== 'string' || !c.meta.markerText) continue;
    const t = effectiveType(c, ids);
    markerCarriers.push({ test: d.key, component: c.name, effectiveType: t ?? null, markerText: c.meta.markerText });
    if (t && RANGE_LIMITED.has(t)) t3Narrow.push(`${d.key} ${c.name} ${t} ${c.meta.markerText}`);
    if (t && !PREDEFINED_KW.has(t) && !/^["']|^symbols\(/.test(t)) t3Author.push(`${d.key} ${c.name} ${t}`);
    // What the SHIPPED runtime rule does with this carrier (form or decline).
    const plan = bakedMarkerPlan(t, effectivePositionRaw(c, ids), c.meta.markerText, parents.has(c.id));
    if (plan) t3Plan.push({ test: d.key, component: c.name, form: plan.inlineMarkerText !== undefined ? 'inline' : 'string', listStyleType: plan.listStyleType });
  }
}
function effectivePositionRaw(c, ids) {
  for (let n = c; n; n = n.slot?.parent ? ids.get(n.slot.parent) : null) {
    const p = prop(n, 'ListStylePosition');
    if (typeof p === 'string') return p;
  }
  return undefined;
}
const tests = (rows) => [...new Set(rows.map((r) => (typeof r === 'string' ? r.split(' ')[0] : r.test)))];
census.T3_web_markerText = {
  markerTextCarriers: { components: markerCarriers.length, tests: tests(markerCarriers).length, webCells: tests(markerCarriers).flatMap((k) => cellsOf(k).filter((x) => x.cell.endsWith(' web'))) },
  shape1_rangeLimited: { components: t3Narrow.length, tests: tests(t3Narrow), rows: t3Narrow,
    cells: tests(t3Narrow).flatMap((k) => cellsOf(k)) },
  shape2_authorNames_in_wave51fix_wire: { components: t3Author.length, tests: tests(t3Author), note: 'zero by construction before T7: the bake bailed every @counter-style document, so no author-named item carried markerText in wave51-fix' },
  shippedRule: { components: t3Plan.length, inline: t3Plan.filter((r) => r.form === 'inline').length, string: t3Plan.filter((r) => r.form === 'string').length,
    tests: tests(t3Plan), webCells: tests(t3Plan).flatMap((k) => cellsOf(k)).filter((x) => x.cell.endsWith(' web')), rows: t3Plan },
};

// T5: outside-position list items — sourceTag li under a list container, effective ListStylePosition
// OUTSIDE or absent (the initial value), effective type not none.
function effectivePosition(c, ids) {
  for (let n = c; n; n = n.slot?.parent ? ids.get(n.slot.parent) : null) {
    const p = prop(n, 'ListStylePosition');
    if (typeof p === 'string') return p.toUpperCase();
  }
  return 'OUTSIDE';
}
const outsideItems = [], insideItems = [];
for (const d of docs) {
  const ids = byId(d.comps);
  for (const c of d.comps) {
    if ((c.meta?.sourceTag ?? '').toLowerCase() !== 'li') continue;
    const parent = c.slot?.parent ? ids.get(c.slot.parent) : null;
    if (!parent || !['ol', 'ul', 'menu', 'dir'].includes((parent.meta?.sourceTag ?? '').toLowerCase())) continue;
    if (effectiveType(c, ids) === 'none') continue;
    // An absolutely / fixed positioned <li> takes RenderAbsoluteChild on
    // Compose (no marker row at all) — not a hang carrier.
    if (['ABSOLUTE', 'FIXED'].includes(String(prop(c, 'Position') ?? '').toUpperCase())) continue;
    (effectivePosition(c, ids) === 'INSIDE' ? insideItems : outsideItems).push(`${d.key} ${c.name}`);
  }
}
census.T5_outside_markers = { items: outsideItems.length, tests: tests(outsideItems).length, insideItems: insideItems.length,
  cells: tests(outsideItems).flatMap((k) => cellsOf(k)).filter((x) => !x.cell.endsWith(' web')),
  thinNativePasses: tests(outsideItems).flatMap((k) => cellsOf(k)).filter((x) => !x.cell.endsWith(' web') && x.verdict === 'P' && x.ssim < 0.98),
  testsList: tests(outsideItems),
  // The brief's 127 / 26 also counted `Display LIST_ITEM` boxes; those take
  // RenderOwnListMarker / ownListMarker, whose gate excludes `outside`
  // (ListItemMarkerGate), so the hang cannot reach them — counted here.
  displayListItemNonLi: docs.reduce((n, d) => n + d.comps.filter((c) => String(prop(c, 'Display') ?? '').toUpperCase().includes('LIST_ITEM') && (c.meta?.sourceTag ?? '').toLowerCase() !== 'li').length, 0) };

// T7: sources containing @counter-style; what the NEW bake does with each (re-run on the extractor fixtures).
const t7 = [];
for (const d of docs) {
  const s = src(d);
  if (!s || !/@counter-style/i.test(s)) continue;
  const fx = path.join(ROOT, 'fixtures/wpt', d.parts[0], d.parts.slice(1).join('__') + '.json');
  let outcome = { status: 'no-fixture' };
  const markers = [];
  if (fs.existsSync(fx)) {
    const fixture = JSON.parse(fs.readFileSync(fx, 'utf8'));
    outcome = bakeCounterStyles(fixture, s);
    const walk = (n) => { if (n?._markerText) markers.push(n._markerText); for (const c of Object.values(n?.children ?? {})) walk(c); };
    for (const r of Object.values(fixture.components ?? {})) walk(r);
  }
  t7.push({ test: d.key, status: outcome.status, reason: outcome.reason ?? null, stamped: outcome.stamped ?? 0, declined: outcome.declined ?? 0, markers,
    cells: cellsOf(d.key) });
}
census.T7_author_counter_style = { tests: t7.length, byStatus: Object.fromEntries(['baked', 'bailed', 'skipped', 'no-fixture'].map((s) => [s, t7.filter((r) => r.status === s).length])),
  newlyStamped: t7.filter((r) => r.stamped > 0).map((r) => `${r.test} → ${r.markers.join(' ')}`), scriptMutationWall: t7.filter((r) => /requires-script-mutation/.test(r.reason ?? '')).map((r) => r.test), rows: t7 };

// T7 native half (ListMarkerEmptyItem, seam-3/seam-4): an `inside` <li> with no text, no children, no
// pseudos and no declared block size takes the Row instead of the zero-size overlay. Counted on the wire
// as it is AND as L5's F-E leaves it (the 100x100 placeholder pair stripped — seam-6-FE-li.patch).
const isPlaceholder = (c) => (c.properties ?? []).length === 2 && ['Width', 'Height'].every((t) => px(prop(c, t)) === 100);
const emptyItems = { now: [], afterFE: [] };
for (const d of docs) {
  const ids = byId(d.comps), parents = new Set(d.comps.map((c) => c.slot?.parent).filter(Boolean));
  for (const c of d.comps) {
    const parent = c.slot?.parent ? ids.get(c.slot.parent) : null;
    if ((c.meta?.sourceTag ?? '').toLowerCase() !== 'li' || !['ol', 'ul', 'menu', 'dir'].includes((parent?.meta?.sourceTag ?? '').toLowerCase())) continue;
    if (effectivePosition(c, ids) !== 'INSIDE' || effectiveType(c, ids) === 'none' || c.text || parents.has(c.id) || (c.pseudos && Object.keys(c.pseudos).length)) continue;
    const declared = ['Height', 'MinHeight', 'BlockSize', 'MinBlockSize'].some((t) => hasProp(c, t));
    if (!declared) emptyItems.now.push(`${d.key} ${c.name}`);
    if (!declared || isPlaceholder(c)) emptyItems.afterFE.push(`${d.key} ${c.name}`);
  }
}
census.T7_native_empty_items = { now: { items: emptyItems.now.length, tests: tests(emptyItems.now) },
  afterFE: { items: emptyItems.afterFE.length, tests: tests(emptyItems.afterFE), cells: tests(emptyItems.afterFE).flatMap((k) => cellsOf(k)) } };

// The lane's named flips / at-risk cells, as the manifests read them today.
census.laneCells = {
  flips: [...cells('css-counter-styles/armenian/css3-counter-styles-008'), ...cells('css-counter-styles/cssom/cssom-pad-setter-invalid'),
    ...cells('css-counter-styles/cssom/cssom-prefix-suffix-setter-invalid'), ...cells('css-counter-styles/cssom/cssom-negative-setter-invalid'), ...cells('css-counter-styles/counter-suffix')],
  atRisk: [...cells('css-counter-styles/armenian/css3-counter-styles-006'), ...cells('css-counter-styles/armenian/css3-counter-styles-007'), ...cells('css-counter-styles/armenian/css3-counter-styles-009'),
    ...cells('css-lists/content-property/marker-text-matches-armenian'), ...cells('css-lists/content-property/marker-text-matches-georgian'),
    ...cells('css-counter-styles/counter-style-at-rule/broken-symbols'), ...cells('css-counter-styles/counter-style-at-rule/descriptor-suffix'),
    ...cells('css-lists/content-property/marker-text-matches-circle'), ...cells('css-lists/content-property/marker-text-matches-disc'), ...cells('css-lists/content-property/marker-text-matches-square')],
};

fs.writeFileSync(path.join(HERE, 'census.json'), JSON.stringify(census, null, 1) + '\n');
const c = census;
console.log(`census: ${c.docs} docs, ${c.components} components, ${c.scoredCells} scored cells`);
console.log(`T1 korean carriers: ${c.T1_korean_hangul_formal.carriers.length} components in ${tests(c.T1_korean_hangul_formal.carriers.map((s) => ({ test: s.split(' ')[0] }))).length} test(s)`);
console.log(`T2 zero-tall band rows: ${c.T2_zero_tall_border_band.rows} in ${c.T2_zero_tall_border_band.tests.length} tests: ${c.T2_zero_tall_border_band.tests.join(', ')}`);
console.log(`T2 extended (END via parent Width, own Height/Width below own border): ${c.T2_zero_tall_border_band.extended.rows} rows in ${c.T2_zero_tall_border_band.extended.tests.length} tests: ${c.T2_zero_tall_border_band.extended.tests.join(', ')}`);
console.log(`T3 markerText carriers: ${c.T3_web_markerText.markerTextCarriers.components} components / ${c.T3_web_markerText.markerTextCarriers.tests} tests; shape 1: ${c.T3_web_markerText.shape1_rangeLimited.components} components / ${c.T3_web_markerText.shape1_rangeLimited.tests.length} tests; shape 2 in wire today: ${c.T3_web_markerText.shape2_authorNames_in_wave51fix_wire.components}`);
console.log(`T3 shipped rule (bakedMarkerPlan): ${c.T3_web_markerText.shippedRule.components} components (inline ${c.T3_web_markerText.shippedRule.inline}, string ${c.T3_web_markerText.shippedRule.string}) in ${c.T3_web_markerText.shippedRule.tests.length} tests`);
console.log(`T5 outside items: ${c.T5_outside_markers.items} in ${c.T5_outside_markers.tests} tests (inside: ${c.T5_outside_markers.insideItems}); thin native passes: ${c.T5_outside_markers.thinNativePasses.length}`);
console.log(`T7 native empty inside items: ${c.T7_native_empty_items.now.items} now (${c.T7_native_empty_items.now.tests.length} tests), ${c.T7_native_empty_items.afterFE.items} after F-E (${c.T7_native_empty_items.afterFE.tests.length} tests)`);
console.log(`T7 @counter-style tests: ${c.T7_author_counter_style.tests} → ${JSON.stringify(c.T7_author_counter_style.byStatus)}; newly stamped: ${c.T7_author_counter_style.newlyStamped.length}; script-mutation wall: ${c.T7_author_counter_style.scriptMutationWall.length}`);
