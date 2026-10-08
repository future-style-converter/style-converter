// tools/titan/results/wave53-canvas-root/web-markup-census.test.tsx — L3's WEB
// blast-radius census (kept, not part of any suite). Renders EVERY per-test IR
// document of a run through the gallery twice — once through the tree's
// ComposedCaptureGallery.tsx, once through a copy of `git show <BASE>:` of the
// same file (imports re-pointed, written by web-markup-census.sh) — and lists
// the documents whose static markup differs. The set it prints IS the web
// capture carrier set the change can reach (a capture can only move if its
// markup moved). Run: bash tools/titan/results/wave53-canvas-root/web-markup-census.sh
import { it } from 'vitest';
import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { renderToStaticMarkup } from 'react-dom/server';
// The tree's (edited) gallery …
import { ComposedCaptureGallery as Now } from '../../../../apps/web-harness/src/ui/ComposedCaptureGallery';
// … and the base copy the driver script wrote beside the out file.
// @ts-ignore — generated at run time
import { ComposedCaptureGallery as Base } from './.census-base-gallery';

const RUN = process.env.CENSUS_RUN ?? 'wave52-ship';
const ROOT = join(__dirname, '../../../..');
const OUT = process.env.CENSUS_OUT ?? join(__dirname, 'web-markup-census.out.txt');

it('census', () => {
  const secDir = join(ROOT, 'tools/titan/runs', RUN, 'sections');
  const diffs: string[] = []; let n = 0;
  for (const sec of readdirSync(secDir).sort()) {
    const ir = join(secDir, sec, 'per-test-ir');
    if (!existsSync(ir)) continue;
    for (const f of readdirSync(ir).filter((x) => x.endsWith('.json')).sort()) {
      const doc = JSON.parse(readFileSync(join(ir, f), 'utf8'));
      n++;
      // Same element, same document: only the gallery source differs.
      const a = renderToStaticMarkup(<Base document={doc} />);
      const b = renderToStaticMarkup(<Now document={doc} />);
      if (a !== b) diffs.push(f.replace(/\.json$/, ''));
    }
  }
  writeFileSync(OUT, `run ${RUN}: ${n} documents rendered, ${diffs.length} differ\n${diffs.join('\n')}\n`);
});
