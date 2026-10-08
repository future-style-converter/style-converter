// tools/titan/results/wave54-web-tail/w1-joins.census.test.tsx — wave 54 lane L6 (W1): the REAL-CODE join census.
// Walks every composed node of every per-test IR document of a gate run, and for each `meta.runs`
// host calls resolveRuns exactly as NodeRenderer does (the host's OWN computed hyphens arms the join),
// recording every host whose plan joined ≥ 1 member — the unit's whole runtime reach. Also counts
// the out-of-flow (abspos/fixed) run members and how many the predicate calls inert.
//   CENSUS_RUN=wave53-final npx vitest run --config tools/titan/results/wave54-web-tail/vitest.census.config.mts w1-joins
import { it, vi } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { composeTree, type ComposedNode } from '../../../../runtimes/web/src/renderer/Composer';
import { resolveRuns } from '../../../../runtimes/web/src/renderer/InlineRuns';
import { isInertOutOfFlowMember } from '../../../../runtimes/web/src/renderer/InertOutOfFlowWordJoin';
import { buildStyles } from '../../../../runtimes/web/src/core/renderer/StyleBuilder';

const ROOT = path.resolve(__dirname, '../../../..');
const RUN = process.env.CENSUS_RUN ?? 'wave53-final';

it('lists every runs host the W1 join reaches', () => {
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  const sectionsDir = path.join(ROOT, 'tools/titan/runs', RUN, 'sections');
  let docs = 0, hosts = 0, oofMembers = 0, inert = 0, autoHosts = 0;
  const joins: string[] = [];
  const walk = (n: ComposedNode, file: string) => {
    const runs = n.component.meta?.runs;
    if (Array.isArray(runs) && runs.length > 0) {
      hosts++;
      const auto = buildStyles(n.component.properties).hyphens === 'auto';
      if (auto) autoHosts++;
      const p = resolveRuns(runs, n.children, n.component.id, { hyphensAuto: auto });
      // Out-of-flow members named by the runs (abspos/fixed), and the predicate's verdict on each.
      for (const r of runs) {
        const i = n.children.findIndex((c) => c.component.name === r.child || c.component.id === r.child);
        if (i < 0) continue;
        const pos = n.children[i].component.properties.find((q) => q.type === 'Position')?.data;
        if (pos === 'ABSOLUTE' || pos === 'FIXED') { oofMembers++; if (isInertOutOfFlowMember(n.children[i])) inert++; }
      }
      if (p && p.joinedOutOfFlowMembers > 0) joins.push(`${file} ${n.component.id} joined ${p.joinedOutOfFlowMembers}`);
    }
    n.children.forEach((c) => walk(c, file));
  };
  for (const sec of fs.readdirSync(sectionsDir).sort()) {
    const irDir = path.join(sectionsDir, sec, 'per-test-ir');
    if (!fs.existsSync(irDir)) continue;
    for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
      docs++;
      const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
      composeTree(doc).forEach((r) => walk(r, `${sec}/${f}`));
    }
  }
  const lines = [`w1-joins ${RUN}: documents ${docs} · runs hosts ${hosts} (own hyphens:auto ${autoHosts}) · abspos/fixed run members ${oofMembers} (inert ${inert}) · hosts joined ${joins.length}`, ...joins];
  fs.writeFileSync(path.join(__dirname, `w1-joins.${RUN}.out.txt`), lines.join('\n') + '\n');
  console.log(lines.join('\n'));
});
