#!/usr/bin/env node
// census-seam4.mjs — lane L11 fix pass: the reach of seam-4.patch
// (web-harness calibrateStyles `harnessDefaultsUnderAll`).
//
// The helper changes a component's style object ONLY when (a) the object
// carries `all` AND (b) a harness default (`width` / `maxWidth`) is also an
// author key after the reset. Which defaults exist depends on the capture
// mode (ComponentRenderer.tsx calibrateStyles):
//   composed WPT (the gate corpus) — NO defaults → reach 0 by construction;
//   per-element WPT                 — `maxWidth` only;
//   legacy fixtures                 — `width` + `maxWidth`.
// This script counts (b) per mode over the 1435 wave51-fix per-test IR docs
// (IR types after the LAST `All`: Width/MaxWidth) and the legacy fixtures
// (CSS keys after `all`), so the "corpus-neutral" claim is measured, not assumed.
// Usage: node census-seam4.mjs  → census-seam4.json beside it.
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const SECTIONS = join(ROOT, 'tools', 'titan', 'runs', 'wave51-fix', 'sections');

/** Types that follow the LAST `All` in an IR property list (the post-reset own list). */
const afterLastAll = (props) => {
  const i = props.map((p) => p.type).lastIndexOf('All');   // the last `all` governs (§6.4)
  return i < 0 ? null : props.slice(i + 1).map((p) => p.type);
};

const out = { generated: new Date().toISOString(), docs: 0, allComponents: 0, perElementReach: [], composedReach: 0, legacyFixtureReach: [] };
// 1. The corpus: every per-test IR doc of every section.
for (const sec of readdirSync(SECTIONS)) {
  let files = [];
  try { files = readdirSync(join(SECTIONS, sec, 'per-test-ir')); } catch { continue; }   // a section without per-test IR
  for (const f of files.filter((n) => n.endsWith('.json'))) {
    out.docs++;
    const doc = JSON.parse(readFileSync(join(SECTIONS, sec, 'per-test-ir', f), 'utf8'));
    for (const c of doc.components ?? []) {
      const after = afterLastAll(c.properties ?? []);
      if (after === null) continue;
      out.allComponents++;
      // per-element WPT has only the maxWidth default; composed has none (reach stays 0).
      if (after.includes('MaxWidth')) out.perElementReach.push(`${sec}/${f} ${c.name}`);
    }
  }
}
// 2. The legacy fixtures (CSS source; `all` keys and what follows them).
const walkFixtures = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
  e.isDirectory() ? (e.name === 'wpt' ? [] : walkFixtures(join(dir, e.name))) : e.name.endsWith('.json') ? [join(dir, e.name)] : []);
for (const file of walkFixtures(join(ROOT, 'fixtures'))) {
  let doc; try { doc = JSON.parse(readFileSync(file, 'utf8')); } catch { continue; }   // non-JSON fixture helpers
  const walk = (comps, path) => {
    for (const [name, c] of Object.entries(comps ?? {})) {
      if (!c || typeof c !== 'object') continue;
      const keys = Object.keys(c.properties ?? {});
      const i = keys.lastIndexOf('all');
      // legacy defaults are width + maxWidth: an author key of either name after `all` is in reach.
      if (i >= 0 && keys.slice(i + 1).some((k) => k === 'width' || k === 'max-width')) {
        out.legacyFixtureReach.push(`${file.slice(ROOT.length + 1)} ${path}${name}`);
      }
      walk(c.children, `${path}${name}/`);
    }
  };
  walk(doc.components, '');
}
writeFileSync(join(HERE, 'census-seam4.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`docs ${out.docs} · All components ${out.allComponents} · composed reach ${out.composedReach} · ` +
  `per-element reach ${out.perElementReach.length} · legacy fixture reach ${out.legacyFixtureReach.length}`);
for (const r of [...out.perElementReach, ...out.legacyFixtureReach]) console.log(`  ${r}`);
