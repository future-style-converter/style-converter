#!/usr/bin/env node
// tools/titan/results/wave54-plan/label-chrome-all-reset.census.mjs — READ-ONLY census for the wave-54 family
// "label-chrome-all-reset" (BACKLOG obligation 0(e)). Writes label-chrome-all-reset.census.json beside itself and
// prints a short summary. Reads only tracked sources and the frozen gate of record:
//   (A) every tracked fixture under fixtures/** — which components declare `all`, which captures are LABEL-EXEMPT by
//       the shared chrome predicate (docs/DYNAMIC_CAPTURE.md §5 "When": composed root has children OR non-empty text);
//   (B) the 130 committed baseline stems under tools/visual/baseline/ — owner fixture by (index, name), label-due;
//   (C) the six all-then-color captures from the TRACKED converted IR
//       (tools/titan/results/wave52-all-reset-postload-colour/convert-out/all-then-color/tmpOutput.json) walked with the
//       device flatten rules (tools/visual/expected-captures.mjs parentCreatesContext / dependsOnBackdrop);
//   (D) the WPT corpus radius: per-test IR documents in tools/titan/runs/wave53-final/sections/*/per-test-ir/ that carry
//       an `All` property (WPT mode is label-free on all three platforms, so none of them can carry chrome).
// Usage: node tools/titan/results/wave54-plan/label-chrome-all-reset.census.mjs
import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parentCreatesContext, dependsOnBackdrop } from '../../../visual/expected-captures.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../../../..');
const rel = (p) => path.relative(ROOT, p);
const read = (p) => JSON.parse(readFileSync(path.join(ROOT, p), 'utf8'));

// ── (A) fixtures ────────────────────────────────────────────────────────────────────────────────────────────────
const tracked = execFileSync('git', ['ls-files', 'fixtures'], { cwd: ROOT, encoding: 'utf8' })
  .split('\n').filter((f) => f.endsWith('.json'));
const gateList = readFileSync(path.join(ROOT, 'tools/visual/gate-fixtures.txt'), 'utf8')
  .split('\n').map((l) => l.split('#')[0].trim()).filter(Boolean);
const baselineFiles = readdirSync(path.join(ROOT, 'tools/visual/baseline')).filter((f) => f.endsWith('.png'));
const baselineNames = new Set(baselineFiles.map((f) => /^(?:iOS|Android|web)__\d+_(.+)\.png$/.exec(f)?.[1]).filter(Boolean));

const hasText = (v) => typeof v?._text === 'string' && v._text.length > 0;           // web/iOS/Android: "" is no text
const hasKids = (v) => !!(v && v.children && typeof v.children === 'object' && Object.keys(v.children).length > 0);
const allDecls = [];
const fixtures = [];
for (const fx of tracked) {
  let doc;
  try { doc = read(fx); } catch { continue; }
  if (!doc || typeof doc.components !== 'object' || Array.isArray(doc.components)) continue;
  const nodes = [];
  const walk = (map, parentPath) => {
    for (const [name, v] of Object.entries(map ?? {})) {
      const p = parentPath ? `${parentPath}/${name}` : name;
      const props = v?.properties && typeof v.properties === 'object' ? Object.keys(v.properties) : [];
      const node = { name, path: p, depth: p.split('/').length - 1, kids: hasKids(v), text: hasText(v) };
      node.labelDue = !node.kids && !node.text;
      nodes.push(node);
      if (props.includes('all')) {
        allDecls.push({
          fixture: fx, path: p, keyword: v.properties.all, position: `${props.indexOf('all') + 1}/${props.length}`,
          before: props.slice(0, props.indexOf('all')), after: props.slice(props.indexOf('all') + 1),
          capturedRootHasChildren: node.kids, capturedRootHasText: node.text, labelDueOnOwnCapture: node.labelDue,
          gateListed: gateList.includes(fx), nameHasCommittedBaseline: baselineNames.has(name),
        });
      }
      if (hasKids(v)) walk(v.children, p);
    }
  };
  walk(doc.components, '');
  const exempt = nodes.filter((n) => !n.labelDue);
  fixtures.push({
    fixture: fx, nodes: nodes.length, exempt: exempt.length,
    exemptContainers: exempt.filter((n) => n.kids).length, exemptTextRoots: exempt.filter((n) => !n.kids && n.text).length,
    gateListed: gateList.includes(fx), hasCommittedBaseline: nodes.some((n) => baselineNames.has(n.name)),
  });
}

// ── (B) the committed baseline stems: owner fixture by (index, top-level name) ─────────────────────────────────────
const stems = [...new Set(baselineFiles.map((f) => /^(?:iOS|Android|web)__(\d{3}_.+)\.png$/.exec(f)?.[1]).filter(Boolean))].sort();
const flatOwners = fixtures.filter((f) => f.hasCommittedBaseline).map((f) => [f.fixture, Object.entries(read(f.fixture).components)]);
const stemRows = stems.map((stem) => {
  const [, nnn, name] = /^(\d{3})_(.+)$/.exec(stem);
  const owners = flatOwners.filter(([, entries]) => entries[+nnn]?.[0] === name).map(([fx, entries]) => {
    const v = entries[+nnn][1];
    return { fixture: fx, labelDue: !hasKids(v) && !hasText(v) };
  });
  return { stem, owners };
});
const stemSummary = {
  stems: stemRows.length,
  ownedExactlyOnce: stemRows.filter((r) => r.owners.length === 1).length,
  labelDue: stemRows.filter((r) => r.owners.length && r.owners.every((o) => o.labelDue)).length,
  byOwner: Object.fromEntries([...new Set(stemRows.flatMap((r) => r.owners.map((o) => o.fixture)))]
    .map((fx) => [fx, stemRows.filter((r) => r.owners.some((o) => o.fixture === fx)).length])),
  unowned: stemRows.filter((r) => r.owners.length === 0).map((r) => r.stem),
  // Stems more than one fixture claims at the same (index, name) — the reason a label-exempt manifest must NAME the
  // fixture instead of inferring it (wave-54 brief "Ownership"): the owners can disagree on label-due.
  multiOwner: stemRows.filter((r) => r.owners.length > 1),
};

// ── (C) the six all-then-color captures from the tracked IR, device flatten order ─────────────────────────────────
const IR = 'tools/titan/results/wave52-all-reset-postload-colour/convert-out/all-then-color/tmpOutput.json';
const wire = read(IR);
const childrenOf = new Map();
for (const c of wire.components) if (c.slot?.parent) (childrenOf.get(c.slot.parent) ?? childrenOf.set(c.slot.parent, []).get(c.slot.parent)).push(c);
const captures = [];
const visit = (c) => {
  const kids = childrenOf.get(c.id) ?? [];
  const allProp = (c.properties ?? []).find((p) => p.type === 'All');
  captures.push({
    stem: `${String(captures.length).padStart(3, '0')}_${c.name}`, id: c.id,
    rootAll: allProp ? `${allProp.data} @${(c.properties ?? []).indexOf(allProp) + 1}/${c.properties.length}` : null,
    composedChildren: kids.map((k) => k.name), text: typeof c.text === 'string' && c.text.length > 0 ? c.text : null,
    labelDue: kids.length === 0 && !(typeof c.text === 'string' && c.text.length > 0),
  });
  if (kids.length && !parentCreatesContext(c)) for (const k of kids) if (!dependsOnBackdrop(k)) visit(k);
};
for (const c of wire.components) if (!c.slot?.parent) visit(c);

// ── (D) the WPT corpus radius at the gate of record ─────────────────────────────────────────────────────────────────
const RUN = 'tools/titan/runs/wave53-final/sections';
const corpus = { run: 'wave53-final', docs: 0, docsWithAll: [] };
for (const sec of readdirSync(path.join(ROOT, RUN))) {
  const dir = path.join(ROOT, RUN, sec, 'per-test-ir');
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((n) => n.endsWith('.json'))) {
    corpus.docs += 1;
    const txt = readFileSync(path.join(dir, f), 'utf8');
    if (txt.includes('"type":"All"')) {
      const doc = JSON.parse(txt);
      const kw = doc.components.flatMap((c) => (c.properties ?? []).filter((p) => p.type === 'All').map((p) => p.data));
      corpus.docsWithAll.push({ section: sec, doc: f, keywords: kw });
    }
  }
}

const out = {
  _generated_by: rel(fileURLToPath(import.meta.url)),
  _sources: { fixtures: `git ls-files fixtures (${tracked.length} json)`, gateList: 'tools/visual/gate-fixtures.txt',
    baselines: `tools/visual/baseline (${baselineFiles.length} png)`, ir: IR, corpus: RUN },
  allDecls,
  fixturesWithExemptCaptures: fixtures.filter((f) => f.exempt > 0),
  gateFixtures: fixtures.filter((f) => f.gateListed),
  fixtureTotals: {
    fixtures: fixtures.length, nodes: fixtures.reduce((a, f) => a + f.nodes, 0), exempt: fixtures.reduce((a, f) => a + f.exempt, 0),
    fixturesWithExempt: fixtures.filter((f) => f.exempt > 0).length,
  },
  baselineStems: stemSummary,
  allThenColorCaptures: captures,
  corpus,
};
writeFileSync(path.join(HERE, 'label-chrome-all-reset.census.json'), JSON.stringify(out, null, 1) + '\n');

console.log(`(A) fixtures with \`all\`: ${allDecls.length} declarations in ${new Set(allDecls.map((d) => d.fixture)).size} fixtures`);
for (const d of allDecls) console.log(`    ${d.fixture} ${d.path} all:${d.keyword} @${d.position} root-children=${d.capturedRootHasChildren} text=${d.capturedRootHasText} labelDue=${d.labelDueOnOwnCapture} gate=${d.gateListed} baselined=${d.nameHasCommittedBaseline}`);
console.log(`(A) label-exempt nodes: ${out.fixtureTotals.exempt} of ${out.fixtureTotals.nodes} in ${out.fixtureTotals.fixturesWithExempt} of ${out.fixtureTotals.fixtures} fixtures`);
for (const f of out.gateFixtures) console.log(`    gate ${f.fixture}: ${f.nodes} nodes, ${f.exempt} exempt (${f.exemptContainers} containers, ${f.exemptTextRoots} text roots), baselined=${f.hasCommittedBaseline}`);
console.log(`(B) baseline stems: ${stemSummary.stems}, owned once ${stemSummary.ownedExactlyOnce}, label-due in every owner ${stemSummary.labelDue}, unowned ${stemSummary.unowned.length}`, stemSummary.byOwner);
for (const r of stemSummary.multiOwner) console.log(`    multi-owner ${r.stem}: ${r.owners.map((o) => `${o.fixture} labelDue=${o.labelDue}`).join(' | ')}`);
console.log('(C) all-then-color captures (device flatten order over the tracked IR):');
for (const c of captures) console.log(`    ${c.stem.padEnd(36)} rootAll=${String(c.rootAll).padEnd(14)} children=[${c.composedChildren}] text=${JSON.stringify(c.text)} labelDue=${c.labelDue}`);
console.log(`(D) ${corpus.run}: ${corpus.docsWithAll.length} of ${corpus.docs} per-test IR docs carry \`All\``);
for (const d of corpus.docsWithAll) console.log(`    ${d.section}/${d.doc} ${d.keywords.join(',')}`);
