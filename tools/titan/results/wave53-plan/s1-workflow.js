export const meta = {
  name: 'wave53-s1-combined-tree',
  description: 'Wave 53 S1: the combined-tree skeptic over the integrated tree (seam-hunk audit, full-sweep counts, git hygiene, pointer audit, every lane census replayed), then a fix lane for must-fix items and a re-check',
  phases: [
    { title: 'S1', detail: 'combined-tree skeptic: hunks, sweep, hygiene, pointers, censuses', model: 'opus' },
    { title: 'Fix', detail: 'must-fix items, then re-check', model: 'opus' },
  ],
}
// args: { tree, sweepLog }  — the orchestrator has ALREADY run the single-writer full sweep and points S1 at its log.
const T = args.tree
const SWEEP = args.sweepLog
const PLAN = `${T}/tools/titan/results/wave53-plan/PLAN.md`
const OUT = `${T}/tools/titan/results/wave53-S1`

const RULES = `HARD RULES — shared tree ${T}, branch campaign/applier-campaign; the five lanes' edits AND their seam patches are applied (integrate-seams.sh ran). You modify nothing in the tree except ${OUT}/ (your report) unless you are the fix lane. No device, Chromium, emulator, simulator; the full sweep was run by the orchestrator (log: ${SWEEP}) — you READ it, you may run FOCUSED suites to re-check a specific claim. No git commit/checkout/stash/reset. Absolute paths rooted at ${T}.`

const REPORT = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['CLAIM-HOLDS', 'MIXED', 'DEFECT-CONFIRMED'] },
    executedChecks: { type: 'array', items: { type: 'string' } },
    defects: { type: 'array', items: { type: 'object', properties: { severity: { type: 'string', enum: ['must-fix', 'should-fix', 'nit'] }, lane: { type: 'string' }, file: { type: 'string' }, what: { type: 'string' }, evidence: { type: 'string' }, fix: { type: 'string' } }, required: ['severity', 'lane', 'file', 'what', 'evidence', 'fix'] } },
    sweepCounts: { type: 'string', description: 'converter / web / web-harness / compose / android-harness / swiftui / tooling / conformance as the log shows them' },
    mustFixBeforeGate: { type: 'boolean' },
  },
  required: ['verdict', 'executedChecks', 'defects', 'sweepCounts', 'mustFixBeforeGate'],
}

phase('S1')
const s1 = await agent(`You are S1, the COMBINED-TREE skeptic of wave 53 of the Style-Converter applier campaign (wave skill Phase 3). ${RULES}
Read ${PLAN} §1–§4 and §6, then every lane note tools/titan/results/wave53-{lists-bakes,soft-hyphen,canvas-root,float-avoid,harness-hygiene}/_note.md (and skeptic.md). Then, BY EXECUTION:
1. SEAM-HUNK AUDIT: \`git -C ${T} diff -- runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift apps/web-harness/src/sdui/ComponentRenderer.tsx tools/titan/extract-fixture.mjs\` — every hunk maps to exactly one §3 registry row and one lane's patch file (compare with the patch files' hunks); no hunk is unregistered, no two overlap, every hunk is commented.
2. SWEEP: read ${SWEEP}; every suite green with its count; counts vs the committed doc tables (README.md, CLAUDE.md, docs/STATUS.md rows; tree READMEs) — list the rows that must be restamped. If any suite is red, that is a must-fix with the failing test named.
3. GIT HYGIENE: \`git -C ${T} status --short\` — every modified/untracked path maps to one lane's ownership list (PLAN §2 own:) or to a seam patch or to a lane results dir; zero probe files in source trees; no stray build files; \`git stash list\` empty; no lock dir under tools/titan/runs/wave53-lock/.
4. POINTER AUDIT: every evidence pointer in the five lane notes (paths, test names, cells) resolves — paths exist, cells re-derived with \`node ${T}/tools/titan/results/wave52-gate/cells.mjs '<terms>' wave53-open\`.
5. CENSUS REPLAY: for EACH lane, re-derive its carrier set with your own script (over tools/titan/runs/wave53-open/sections/*/per-test-ir and the WPT sources) and compare with tools/titan/results/wave53-plan/expectations.json lanes.<lane>.captureCarriers / wireCarriers; an under-reported radius is a must-fix.
6. MUTATIONS: pick two pins per lane and replay their mutations (red → restore byte-exact → green).
7. HONESTY: comments vs measurements; DEGENERATE labels kept; "runtime" not "engine"; no file over 200 lines of NEW logic; every new line commented.
Write ${OUT}/_note.md (create the dir; verdict, every executed check with its output, defects ranked, what you could not check; end with STATUS: COMPLETE) and return the schema.`, { label: 'S1-combined-tree', phase: 'S1', model: 'opus', schema: REPORT })

if (!s1 || !s1.mustFixBeforeGate) return { s1 }
phase('Fix')
const must = s1.defects.filter((d) => d.severity !== 'nit')
const fixed = await agent(`You are the FIX lane for wave 53's combined-tree skeptic findings. ${RULES.replace('You modify nothing in the tree except', 'You may edit, within the owning lane\'s ownership list (PLAN §2) or the seam patch files, what these defects require — and nothing else; a seam file is edited only through its lane\'s patch file (re-cut the patch, re-apply):')}
DEFECTS (executed evidence in ${OUT}/_note.md): ${JSON.stringify(must, null, 1)}
Re-run each check first; fix every must-fix (and should-fix when safe); re-run the touched focused suites; append "## Fix pass" to ${OUT}/_note.md (defect → action or refusal). Return the same schema with your post-fix re-check of each defect.`, { label: 'S1-fix', phase: 'Fix', model: 'opus', schema: REPORT })
return { s1, fixed }
