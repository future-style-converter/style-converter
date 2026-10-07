// tools/titan/results/wave53-canvas-root/skeptic-web-census.test.tsx — the L3
// SKEPTIC's own web blast-radius census (independent of the lane's
// web-markup-census). Differences from the lane's method, on purpose:
//  - input is each section's COMBINED document (sections/<s>/out/tmpOutput.json,
//    what the capture page actually loads), not the split per-test documents —
//    so the gallery's own groupByTest split is exercised;
//  - markup is diffed PER CANVAS (data-capture-name), so a leak names its test;
//  - the three resolvers are also evaluated per per-test document and counted.
// Base gallery: `git show HEAD:` written beside this file by skeptic-web-census.sh.
import { it } from 'vitest';
import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery as Now, resolveCanvasRootBackground } from '../../../../apps/web-harness/src/ui/ComposedCaptureGallery';
import { resolveCanvasTableBody } from '../../../../apps/web-harness/src/ui/CanvasTableBody';
// @ts-ignore — generated at run time by skeptic-web-census.sh
import { ComposedCaptureGallery as Base } from './.sk-base-gallery';

const RUN = process.env.CENSUS_RUN ?? 'wave52-ship';
const ROOT = join(__dirname, '../../../..');
const OUT = join(__dirname, `skeptic-web-census.${RUN}.txt`);

/** Split a gallery's markup into { testKey → that canvas's markup }. */
function canvases(html: string): Map<string, string> {
  const m = new Map<string, string>();
  for (const part of html.split('<div data-capture-canvas').slice(1)) {
    const key = /data-capture-name="([^"]*)"/.exec(part)?.[1] ?? '?';
    m.set(key, part);
  }
  return m;
}

it('census', () => {
  const secDir = join(ROOT, 'tools/titan/runs', RUN, 'sections');
  const lines: string[] = []; let nCanvas = 0; const differ: string[] = []; const onlyOne: string[] = [];
  const plans: string[] = []; const tables: string[] = []; let nDocs = 0;
  for (const sec of readdirSync(secDir).sort()) {
    const combined = join(secDir, sec, 'out', 'tmpOutput.json');
    if (!existsSync(combined)) continue;
    const doc = JSON.parse(readFileSync(combined, 'utf8'));
    const a = canvases(renderToStaticMarkup(<Base document={doc} />));
    const b = canvases(renderToStaticMarkup(<Now document={doc} />));
    for (const k of new Set([...a.keys(), ...b.keys()])) {
      nCanvas++;
      if (!a.has(k) || !b.has(k)) onlyOne.push(`${sec}/${k}`);
      else if (a.get(k) !== b.get(k)) differ.push(`${sec}/${k}`);
    }
    // Resolver census over the split per-test documents of the same run.
    const ir = join(secDir, sec, 'per-test-ir');
    for (const f of existsSync(ir) ? readdirSync(ir).filter((x) => x.endsWith('.json')).sort() : []) {
      const d = JSON.parse(readFileSync(join(ir, f), 'utf8')); nDocs++;
      const p = resolveCanvasRootBackground(d); if (p) plans.push(`${f} uniform=${p.uniform} origins=${JSON.stringify(p.origins)}`);
      const t = resolveCanvasTableBody(d); if (t) tables.push(`${f} ${JSON.stringify(t)}`);
    }
  }
  lines.push(`run ${RUN}: ${nCanvas} canvases (combined docs), ${differ.length} differ, ${onlyOne.length} present on one side only`);
  lines.push(...differ.map((x) => `  differ ${x}`), ...onlyOne.map((x) => `  one-side ${x}`));
  lines.push(`per-test docs ${nDocs}: root-image plans ${plans.length}, table bodies ${tables.length}`);
  lines.push(...plans.map((x) => `  plan ${x}`), ...tables.map((x) => `  table ${x}`));
  writeFileSync(OUT, lines.join('\n') + '\n');
});
