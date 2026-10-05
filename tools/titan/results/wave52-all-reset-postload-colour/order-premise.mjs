#!/usr/bin/env node
// order-premise.mjs — lane L11 pin 4 (brief colour-not-reaching-text §7):
// F1 rests on "IR property order = source declaration order". This script
// runs the REAL converter (:converter, `--to ir`) on fixtures and asserts the
// order of the emitted `All` entry relative to its neighbours:
//   - fixtures/combinations/all-then-color.json (L11's fixture): `all` FIRST in
//     ATC_AllThenProps, LAST in the PropsThenAll child, AFTER Direction in
//     ATC_DirectionSurvives, and (skeptic M1 fix) `Display` IMMEDIATELY after
//     `All` in the three box-keeping components — `all: initial` resets display
//     to `inline` (css-display-3 §2), so the box survives only if the converter
//     keeps `display: block` AFTER `all`;
//   - fixtures/properties/global/longtail.json: `all` LAST in every All_*
//     component, so the order-aware reset leaves them byte-identical (brief
//     pin 7 — "assert none moved");
//   - the executed MUTATION: a temp copy of the L11 fixture with the
//     AllThenProps keys REVERSED must fail the first assertion (proving the
//     check can fail; the temp copy lives in this directory and is removed).
// Usage: node order-premise.mjs   (JDK 21; writes only under ./convert-out/)
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const JAVA_HOME = execFileSync('/usr/libexec/java_home', ['-v', '21']).toString().trim();

/** Convert one fixture with the real converter; returns name → property types. */
function convert(fixture, tag) {
  const out = join(HERE, 'convert-out', tag);
  mkdirSync(out, { recursive: true });
  execFileSync(join(ROOT, 'gradlew'), ['-q', ':converter:run',
    `--args=convert --from css --to ir -i ${fixture} -o ${out}`],
    { cwd: ROOT, env: { ...process.env, JAVA_HOME }, stdio: ['ignore', 'pipe', 'pipe'] });
  const doc = JSON.parse(readFileSync(join(out, 'tmpOutput.json'), 'utf8'));
  // v2 flat list: index every component by its `name` (the fixture key).
  return new Map(doc.components.map((c) => [c.name, (c.properties ?? []).map((p) => p.type)]));
}

/** Find a component whose name ends with `suffix` (child names carry their parent's). */
const pick = (m, suffix) => [...m].find(([n]) => n === suffix || n.endsWith(`__${suffix}`) || n.endsWith(suffix))?.[1];

const results = [];
const check = (label, cond, detail) => { results.push({ label, ok: !!cond, detail }); };

// 1. L11's fixture.
const atc = convert(join(ROOT, 'fixtures/combinations/all-then-color.json'), 'all-then-color');
const allThen = pick(atc, 'ATC_AllThenProps');
check('ATC_AllThenProps: All FIRST', allThen?.[0] === 'All', allThen);
// Children keep their bare fixture key as `name` (`reset`), slotted under the parent.
const child = atc.get('reset');
check('PropsThenAll child: All LAST', child?.at(-1) === 'All', child);
const dir = pick(atc, 'ATC_DirectionSurvives');
check('ATC_DirectionSurvives: Direction then All', dir?.[0] === 'Direction' && dir?.[1] === 'All', dir);
// Skeptic M1: `Display` must sit RIGHT AFTER `All` wherever the fixture keeps a
// box — the reset rule keeps own(i,end], so this is the IR the runtimes see.
const displayAfterAll = (types) => types && types[types.indexOf('All') + 1] === 'Display';
check('ATC_AllThenProps: Display right after All', displayAfterAll(allThen), allThen);
const span = atc.get('span');   // ATC_InitialUnderRedParent's child, bare fixture key
check('InitialUnderRedParent child: All then Display', span?.[0] === 'All' && displayAfterAll(span), span);
check('ATC_DirectionSurvives: Display right after All', displayAfterAll(dir), dir);

// 2. The longtail fixture: `all` LAST everywhere ⇒ the reset leaves them identical.
const lt = convert(join(ROOT, 'fixtures/properties/global/longtail.json'), 'longtail');
for (const [name, types] of lt) {
  if (types.includes('All')) check(`longtail ${name}: All LAST`, types.at(-1) === 'All', types);
}

// 3. Executed mutation: reverse AllThenProps' key order → assertion 1 must fail.
const src = JSON.parse(readFileSync(join(ROOT, 'fixtures/combinations/all-then-color.json'), 'utf8'));
const p = src.components.ATC_AllThenProps.properties;
src.components.ATC_AllThenProps.properties = Object.fromEntries(Object.entries(p).reverse());
const tmp = join(HERE, 'convert-out', 'mutated-all-then-color.json');
writeFileSync(tmp, JSON.stringify(src));
const mut = pick(convert(tmp, 'mutated'), 'ATC_AllThenProps');
rmSync(tmp);
check('MUTATION reversed keys: All no longer FIRST (the check CAN fail)', mut?.[0] !== 'All', mut);

for (const r of results) console.log(`${r.ok ? 'ok  ' : 'FAIL'} ${r.label}  ${JSON.stringify(r.detail)}`);
writeFileSync(join(HERE, 'order-premise.json'), JSON.stringify({ generated: new Date().toISOString(), results }, null, 1) + '\n');
process.exit(results.every((r) => r.ok) ? 0 : 1);
