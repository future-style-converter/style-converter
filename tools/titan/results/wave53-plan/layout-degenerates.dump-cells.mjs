import { loadRun, resolveRunDir } from '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/score-gate.mjs';
const out = {};
for (const r of process.argv.slice(2)) {
  const run = loadRun(resolveRunDir(r));
  const cells = {};
  for (const s of Object.values(run.sections)) for (const [k, c] of s.cells) cells[k] = c;
  out[r] = cells;
}
console.log(JSON.stringify(out));
