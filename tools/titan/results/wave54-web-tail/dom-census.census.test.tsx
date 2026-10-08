// tools/titan/results/wave54-web-tail/dom-census.census.test.tsx — wave 54 lane L6 (web-tail).
//
// THE REAL-CODE BLAST-RADIUS CENSUS. Renders EVERY per-test IR document of a
// gate run through the harness's real ComposedCaptureGallery (the composed
// capture surface, with the module constants stubbed to the capture URL
// `?wpt=1&wptComposed=1`), and writes one sha1 of the static markup per
// document. Run it on the tree BEFORE the lane's edits and AFTER them (seam
// patch applied); the documents whose hash differs ARE the lane's DOM radius.
// Same DOM ⇒ same Chromium paint (byte-deterministic web capture on this host,
// BACKLOG "Wave 53 lessons"), so the diff is a DOM-level twin of control-check.
//
// Run (never part of any suite — the config below is the only include):
//   CENSUS_RUN=wave53-final CENSUS_OUT=<file.json> \
//     npx vitest run --config tools/titan/results/wave54-web-tail/vitest.census.config.mts
import { it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

// Repo root = four levels above this lane directory.
const ROOT = path.resolve(__dirname, '../../../..');
// The gate run whose per-test IR is the verbatim corpus (PLAN §0 "Verbatim payloads").
const RUN = process.env.CENSUS_RUN ?? 'wave53-final';
// Where the per-document hash table lands.
const OUT = process.env.CENSUS_OUT ?? path.join(__dirname, `dom-census.${RUN}.json`);

it('hashes the composed markup of every per-test IR document', async () => {
  // The capture URL's two read-once module constants (WPT_MODE, WPT_COMPOSED_MODE).
  vi.stubGlobal('window', { location: { search: '?wpt=1&wptComposed=1' } });
  // Re-import so the constants re-evaluate under the stub (the harness tests' pattern).
  vi.resetModules();
  const { ComposedCaptureGallery } = await import(
    '../../../../apps/web-harness/src/ui/ComposedCaptureGallery');
  // Silence the runtime's no-silent-fallthrough breadcrumbs: 1435 documents of warnings.
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
  const sectionsDir = path.join(ROOT, 'tools/titan/runs', RUN, 'sections');
  const table: Record<string, string> = {};
  for (const sec of fs.readdirSync(sectionsDir).sort()) {
    // Only sections that carry per-test IR documents.
    const irDir = path.join(sectionsDir, sec, 'per-test-ir');
    if (!fs.existsSync(irDir)) continue;
    for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
      // The document exactly as the gate captured it.
      const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
      let html: string;
      try {
        html = renderToStaticMarkup(createElement(ComposedCaptureGallery, { document: doc }));
      } catch (e) {
        // A throw is recorded, not hidden — it would show up as a diff too.
        html = `THROW ${(e as Error).message}`;
      }
      table[`${sec}/${f}`] = createHash('sha1').update(html).digest('hex');
    }
  }
  fs.writeFileSync(OUT, JSON.stringify(table, null, 0) + '\n');
  // One line for the log: how many documents were hashed.
  console.log(`dom-census ${RUN}: ${Object.keys(table).length} documents → ${OUT}`);
}, 600_000);
