// tools/titan/results/wave54-web-tail/rs-pairs.census.test.tsx — wave 54 lane L6 (RS): the REAL-CODE pair census.
// Needs seam-1.patch applied (it imports the lifted wsAfterSeparator) — run it only through seam-verify.sh.
// For every per-test IR document: the composed root forest, the gallery's container read, and the separators
// interleaveRootSeparators emits with the real predicate; plus how many documents hit each decline.
import { it, vi } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(__dirname, '../../../..');
const RUN = process.env.CENSUS_RUN ?? 'wave53-final';

it('counts root separators per document with the real predicate', async () => {
  vi.stubGlobal('window', { location: { search: '?wpt=1&wptComposed=1' } });
  vi.resetModules();
  const { wsAfterSeparator } = await import('../../../../apps/web-harness/src/sdui/ComponentRenderer');
  const { composeTree } = await import('../../../../apps/web-harness/src/sdui/Composer');
  const { interleaveRootSeparators, rootSeparatorContainer } = await import('../../../../apps/web-harness/src/ui/ComposedRootSeparator');
  const { resolveCanvasTableBody } = await import('../../../../apps/web-harness/src/ui/CanvasTableBody');
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  const sectionsDir = path.join(ROOT, 'tools/titan/runs', RUN, 'sections');
  const rows: string[] = []; let docs = 0, pairs = 0, tableBodies = 0, wsBodies = 0, markedRootPairsDeclined = 0;
  for (const sec of fs.readdirSync(sectionsDir).sort()) {
    const irDir = path.join(sectionsDir, sec, 'per-test-ir');
    if (!fs.existsSync(irDir)) continue;
    for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
      docs++;
      const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
      const table = resolveCanvasTableBody(doc);
      const container = rootSeparatorContainer(doc, table);
      if (table) tableBodies++;
      if (container.whiteSpace !== undefined) wsBodies++;
      const roots = composeTree(doc);
      const out = interleaveRootSeparators(roots, (r: { component: { id: string } }) => r.component.id, container, wsAfterSeparator);
      const n = out.filter((x: unknown) => x === ' ').length;
      // Pairs the predicate accepts in a flow-root that a decline (table / white-space) withheld here.
      const flow = interleaveRootSeparators(roots, (r: { component: { id: string } }) => r.component.id, { display: 'flow-root' }, wsAfterSeparator)
        .filter((x: unknown) => x === ' ').length;
      if (flow > n) markedRootPairsDeclined += flow - n;
      if (n > 0) { pairs += n; rows.push(`${sec}/${f.replace(/\.json$/, '')} ${n}`); }
    }
  }
  const head = `rs-pairs ${RUN}: documents ${docs} · documents with ≥1 root separator ${rows.length} · separators ${pairs} · table bodies ${tableBodies} · bodies declaring white-space ${wsBodies} · separators withheld by a decline ${markedRootPairsDeclined}`;
  fs.writeFileSync(path.join(__dirname, `rs-pairs.${RUN}.out.txt`), [head, ...rows].join('\n') + '\n');
  console.log(head);
});
