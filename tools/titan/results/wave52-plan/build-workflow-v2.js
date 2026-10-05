export const meta = {
  name: 'wave52-build-v2',
  description: 'Wave 52 builders, resumable: each lane audits and continues its own partial edits, every stage reads the durable lane note instead of in-memory results; build → skeptic → fix → re-verify per lane, Opus lanes',
  phases: [
    { title: 'Build', detail: 'twelve lanes; L7 after L2+L3, L10 after L2; lanes already COMPLETE are skipped' },
    { title: 'Skeptics', detail: 'one per lane, executed repros, own census' },
    { title: 'Fix', detail: 'must-fix defects per lane' },
    { title: 'Re-verify', detail: 'each fix confirmed by an executed check' },
  ],
}
const T = args.tree
const DONE = new Set(args.done || [])
const ONLY = args.only ? new Set(args.only) : null
const M = args.model || 'opus'
const PLAN = `${T}/tools/titan/results/wave52-plan/PLAN.md`
const LOCK = `${T}/tools/titan/runs/wave52-lock`
const L = {
  L1: 'web-tail-colour-vt', L2: 'composed-canvas', L3: 'failure-ink', L4: 'small-fixes', L5: 'extractor-cascade', L6: 'counters-and-lists',
  L7: 'static-position', L8: 'vertical-wedges', L9: 'inline-run-wall', L10: 'flex-nowrap-gaps', L11: 'all-reset-postload-colour', L12: 'instrument-and-calibration',
}
const OUT = (id) => `${T}/tools/titan/results/wave52-${L[id]}`

const RULES = (id) => `HARD RULES — the shared tree ${T}, branch campaign/wave52 (HEAD 7d9c22a7 = dev 5d9ed628 + the committed plan). Up to eleven other lanes edit OTHER files in this tree right now.
(0) RESUME FIRST. This wave's lanes were started twice before and killed by a usage limit, so the working tree already holds UNCOMMITTED edits from every lane — possibly yours. Before writing anything: read your results dir ${OUT(id)}/ (a _note.md, logs, census files, seam patches may already exist), run \`git status --short\` and \`git diff -- <each path in your "own:" list>\` and list untracked files under your owned directories, and read what your earlier self wrote. Verify it against your plan section, KEEP what is right, finish what is missing. Do not restart from scratch and do not revert your own partial work without reading it.
(1) OWNERSHIP: touch only the paths in your lane's "own:" list in ${PLAN} §2 (and your results dir). A change needed elsewhere is a HUNK for that file's owner (§5, written as ${OUT(id)}/hunk-for-<lane>-<n>.patch) or a SEAM PATCH (§3) — never an edit. If YOU accidentally edited a file that is not yours, restore exactly that file with \`git show HEAD:<path> > <path>\`. NEVER run git checkout / restore / stash / reset / clean / rebase / commit / add — the orchestrator commits. Never revert another lane's files.
(2) SEAM FILES (runtimes/compose/…/core/renderer/ComponentRenderer.kt, runtimes/swiftui/…/Renderer/ComponentRenderer.swift, apps/web-harness/src/sdui/ComponentRenderer.tsx, tools/titan/extract-fixture.mjs) are never left edited. Deliver each change as a unified diff ${OUT(id)}/seam-<n>.patch that passes \`git apply --check\` on HEAD (L7/L10: on HEAD with the named prior lanes' patches applied first — say which in the patch header). To VERIFY a seam patch (your pins green with it applied): take the per-file lock — \`mkdir ${LOCK}/<basename>\` succeeding means it is yours (poll every 30 s for up to 20 min; if it never frees, record "seam verification blocked by lock" in notVerified) — then \`shasum -a 256\` the file, \`git apply\` the patch, run your focused tests, restore with \`git show HEAD:<path> > <path>\`, re-shasum (must be equal), \`rmdir\` the lock. Hold a lock only for that sequence; if you find a lock directory older than 30 minutes, check the seam file with \`git diff --quiet -- <path>\`: if clean, rmdir the stale lock and note it.
(3) THE MODULE MAY NOT COMPILE because of another lane's in-flight edit. If a compile error is in a file you do not own, do not touch it: wait 3 minutes and retry, up to 6 times; if it persists, write the exact error and file into ${OUT(id)}/_note.md under "blocked by" and continue with what you can verify. Your FIRST duty is that your own files compile.
(4) NO DEVICES: no emulator, no simulator boot, no test-all.sh, no feed-*.mjs. Focused tests only, always filtered (gradle --tests, xcodebuild -only-testing on the Mac Catalyst destination, vitest <file>, node --test <file>); the host runs twelve lanes.
(5) VERIFICATION OWED (${PLAN} §0): pins on the VERBATIM per-test IR named in your brief, each proven able to fail by an EXECUTED mutation (restore byte-exact, sha-verified, mutation recorded in the test header); a corpus census over the 1435 per-test IR docs (tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json) with a committed script in your results dir; a PNG replay where your brief asks for one; any newly discovered at-risk or flip cell goes into ${OUT(id)}/watchlist-additions.txt ("<section>/<test> <platform>" per line, must match a scored cell — check with node tools/titan/results/wave52-plan/watchlist-check.mjs conventions).
(6) HOUSE RULES: every line commented (the WHY; spec section for extractors, platform API for appliers), new files ≤ 200 lines, no silent fallthrough (PropertyTracker / Log / console.warn once), say "runtime", never name the ring-fenced test filter-effects/backdrop-filter-basic-blur in code.
(7) DURABLE NOTE: ${OUT(id)}/_note.md — what changed and why, census numbers, predicted flips with confidence, what you could not verify, hand-offs delivered/received, seam patches and their verification. Its LAST line must be exactly "STATUS: COMPLETE" when (and only when) the work, pins, census and seam verification are finished; otherwise "STATUS: PARTIAL — <what remains>". Nothing goes to a scratchpad.
(8) Report DATA per the schema; be honest in notVerified.

`
const LANE = {
  type: 'object',
  properties: {
    lane: { type: 'string' }, status: { type: 'string', enum: ['COMPLETE', 'PARTIAL'] }, summary: { type: 'string' },
    filesChanged: { type: 'array', items: { type: 'string' } },
    seamPatches: { type: 'array', items: { type: 'string' } },
    pins: { type: 'array', items: { type: 'object', properties: { test: { type: 'string' }, mutation: { type: 'string' }, result: { type: 'string' } }, required: ['test', 'mutation', 'result'] } },
    predictedFlips: { type: 'array', items: { type: 'string' } },
    notVerified: { type: 'array', items: { type: 'string' } },
    remaining: { type: 'array', items: { type: 'string' } },
  },
  required: ['lane', 'status', 'summary', 'filesChanged', 'seamPatches', 'pins', 'predictedFlips', 'notVerified'],
}
const SKEPTIC = {
  type: 'object',
  properties: {
    skeptic: { type: 'string' }, verdict: { type: 'string' },
    executedRepros: { type: 'array', items: { type: 'string' } },
    defects: { type: 'array', items: { type: 'object', properties: { severity: { type: 'string', enum: ['must-fix', 'should-fix', 'nit'] }, file: { type: 'string' }, claim: { type: 'string' }, evidence: { type: 'string' } }, required: ['severity', 'file', 'claim', 'evidence'] } },
    mustFixBeforeGate: { type: 'boolean' },
  },
  required: ['skeptic', 'verdict', 'executedRepros', 'defects', 'mustFixBeforeGate'],
}
const EXTRA = {
  L7: `DEPENDENCY: your Compose and iOS seam hunks overlap L2's and L3's (${PLAN} §3 and §9). Read ${OUT('L2')}/_note.md and ${OUT('L3')}/_note.md and list their seam-*.patch files; cut your seam patches against HEAD with THOSE applied first (L2 then L3), state that base in each patch header and in your note, and verify under the lock by applying theirs then yours.`,
  L10: `DEPENDENCY: your 027 natives need L2's M1 (${PLAN} §4 "L2 before L10"). Read ${OUT('L2')}/_note.md and its seam-*.patch files; cut your patches against HEAD with L2's applied first and say so.`,
  L12: `L12 ONLY: the re-freeze of exactly the references your section lists, via tools/titan/capture-browser-ref.mjs (headless Chromium, no emulator or simulator), IS allowed — sha1 every OTHER frozen ref before and after and record the two digests of the whole set. The injector rewrites manifests IN PLACE: never run it against tools/titan/runs/wave52-open or wave51-fix — copy the run to tools/titan/runs/wave52-calib/ first and re-score THERE, then diff with score-gate.mjs wave52-open wave52-calib and record the per-cell result with the capture-hash check (identical capture bytes → instrument-only).`,
}
const build = (id) => agent(RULES(id) + `YOUR LANE: ${id} · ${L[id]}. Read ${PLAN} §0, §1, your §2 section "### ${id} ·", §3 (your seam hunks), §4 (where you land), §5 (what you yielded / receive), §9 (the plan-skeptic corrections — they override §2 where they conflict), then the brief(s) your section cites under ${T}/tools/titan/results/wave52-plan/. Build exactly what your section says, cheapest and highest-confidence targets first, within the effort your section budgets. Results dir: ${OUT(id)}/. ${EXTRA[id] || ''}`, { label: `build:${id}`, phase: 'Build', schema: LANE, model: M })

const SK = (id) => `SKEPTIC RULES. Shared tree ${T}, branch campaign/wave52, HEAD 7d9c22a7; other lanes' uncommitted work is in the tree. Read-only EXCEPT: executing a mutation and restoring byte-exact (sha-verified), and applying a lane's seam patch under the per-file lock (mkdir ${LOCK}/<basename>; shasum; git apply; test; \`git show HEAD:<path> > <path>\`; re-shasum; rmdir). NEVER git checkout/restore/stash/reset/clean/commit/rebase/add. No devices. Focused tests only. A compile error in a file the audited lane does not own is NOT its defect: wait 3 minutes and retry up to 6 times, then record it.
LANE UNDER AUDIT: ${id} · ${L[id]}. Read ${PLAN} "### ${id} ·" (+ §3, §5, §9), then ${OUT(id)}/_note.md and everything in ${OUT(id)}/. If the note does not end with "STATUS: COMPLETE", your first defect is must-fix "lane incomplete" listing exactly what remains.
You MUST produce EXECUTED repros: re-run the lane's pins; re-execute at least two of its mutations independently (restore, sha-verify); re-derive its corpus census with your OWN script (never its JSON) and compare numbers; \`git apply --check\` each seam patch on its stated base and run the pins with it applied under the lock; open the wave51-fix PNG (tools/titan/runs/wave51-fix/sections/<sec>/{web,ios,android}-screenshots/) and the frozen ref (tools/wpt/refs/) of every predicted-flip cell and judge whether the fix would plausibly produce the predicted picture; check the lane changed only paths in its "own:" list (git status / git diff --name-only filtered to what its note claims) — a foreign edit is must-fix; check every-line comments and ≤ 200 lines on new files, no silent fallthrough, the ring-fence, no test-name carve-outs, and the device-A/B hash sentence wherever a device A/B is staged. A defect is must-fix if it would produce a wrong render, break a build or suite, leave a pin vacuous, touch another lane's file, or claim an unmeasured blast radius. Write your working to ${OUT(id)}/skeptic.md (durable; append if it exists). Output DATA per the schema.
`
const ids = Object.keys(L).filter(id => !ONLY || ONLY.has(id))
const bp = {}
const start = (id) => DONE.has(id) ? Promise.resolve({ lane: id, status: 'COMPLETE', skipped: true }) : build(id)
for (const id of ids) if (id !== 'L7' && id !== 'L10') bp[id] = start(id)
const dep = (id) => bp[id] || Promise.resolve({ lane: id, status: 'COMPLETE', skipped: true })
if (ids.includes('L7')) bp.L7 = Promise.all([dep('L2'), dep('L3')]).then(([a, b]) => (a && b) ? start('L7') : null)
if (ids.includes('L10')) bp.L10 = dep('L2').then(a => a ? start('L10') : null)

const lanes = await Promise.all(ids.map(async (id) => {
  let b = null
  try { b = await bp[id] } catch (e) { b = null }
  if (!b) return { id, stage: 'build-died' }
  let s = null
  try { s = await agent(SK(id), { label: `skeptic:${id}`, phase: 'Skeptics', schema: SKEPTIC, model: M }) } catch (e) { s = null }
  if (!s) return { id, stage: 'skeptic-died', buildStatus: b.status }
  const mf = s.defects.filter(d => d.severity === 'must-fix')
  const other = s.defects.filter(d => d.severity !== 'must-fix').length
  if (!mf.length) return { id, stage: 'clean', buildStatus: b.status, verdict: s.verdict.slice(0, 400), shouldFix: other, flips: b.predictedFlips || [] }
  let f = null
  try { f = await agent(RULES(id) + `YOUR LANE: ${id} · ${L[id]} (FIX PASS). A skeptic audited your lane (${OUT(id)}/skeptic.md). Fix EVERY must-fix defect below within your owned paths (seam changes → re-cut and re-verify your patch), re-run the affected pins and mutations, update ${OUT(id)}/_note.md (keep its last line a STATUS line), and report.\n${JSON.stringify(mf, null, 1)}`, { label: `fix:${id}`, phase: 'Fix', schema: LANE, model: M }) } catch (e) { f = null }
  if (!f) return { id, stage: 'fix-died', mustFix: mf }
  let r = null
  try { r = await agent(SK(id) + `\nRE-VERIFY: these must-fix defects were reported and a fix pass ran. Confirm each with an EXECUTED check and look for regressions; append "## Re-verify" to ${OUT(id)}/skeptic.md.\n${JSON.stringify(mf, null, 1)}`, { label: `reverify:${id}`, phase: 'Re-verify', schema: SKEPTIC, model: M }) } catch (e) { r = null }
  if (!r) return { id, stage: 'reverify-died', mustFix: mf }
  const still = r.defects.filter(d => d.severity === 'must-fix')
  return { id, stage: still.length ? 'must-fix-remains' : 'fixed', buildStatus: f.status, remaining: still, verdict: r.verdict.slice(0, 400), flips: f.predictedFlips || b.predictedFlips || [] }
}))
log(lanes.map(l => `${l.id}:${l.stage}`).join(' '))
return lanes
