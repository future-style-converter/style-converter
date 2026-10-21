// own independent snapshot: imports the scorer's loader by absolute path, writes to argv[3]
import { writeFileSync } from 'node:fs';
const T='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/score-gate.mjs';
const { loadRun, resolveRunDir } = await import(T);
const runId=process.argv[2]; const out=process.argv[3];
const run=loadRun(resolveRunDir(runId));
const cells={}; let n=0;
for (const s of Object.values(run.sections)) for (const [k,c] of s.cells){ const [t,p]=k.split('|'); cells[`${t.replace(/^css\//,'')} ${p}`]=`${c.pass?'P':'f'} ${c.ssim}`; n++; }
writeFileSync(out, JSON.stringify(cells));
console.log(runId, n);
