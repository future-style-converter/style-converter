export const meta = {
  name: 'wave52-build',
  description: 'Wave 52 builders: twelve lanes from tools/titan/results/wave52-plan/PLAN.md in the shared tree with disjoint ownership and locked seam-patch verification; then one skeptic per lane with executed repros; fixers; re-verification',
  phases: [
    { title: 'Build A', detail: 'L1 L2 L3 L4 L5 L6 L8 L9 L11 L12 — independent lanes' },
    { title: 'Build B', detail: 'L7 L10 — cut seam patches against the L2/L3-patched renderers' },
    { title: 'Skeptics', detail: 'one per lane, executed repros, own census, mutation re-execution' },
    { title: 'Fix', detail: 'must-fix defects per lane' },
    { title: 'Re-verify', detail: 'each fix confirmed by an executed check' },
  ],
}
const T = args.tree
const PLAN = `${T}/tools/titan/results/wave52-plan/PLAN.md`
const OUT = (lane) => `${T}/tools/titan/results/wave52-${lane}`
const LOCK = `${T}/tools/titan/runs/wave52-lock`

const RULES = `HARD RULES — the shared tree ${T}, branch campaign/wave52 (HEAD = dev 5d9ed628 + the committed plan). Eleven other lanes edit OTHER files in this tree right now.
(1) OWNERSHIP: touch only the paths in your lane's "own:" list in ${PLAN} §2 (and your results dir). If a change is needed elsewhere, it is a HUNK for that file's owner (§5) or a SEAM PATCH (§3) — never an edit. Verify at the end with \`git status --short\` that every changed path is yours; anything else you must undo by restoring the file byte-exact from HEAD via \`git show HEAD:<path> > <path>\` (NEVER git checkout/restore/stash/reset/clean/rebase/commit — the orchestrator commits).
(2) SEAM FILES (runtimes/compose/…/core/renderer/ComponentRenderer.kt, runtimes/swiftui/…/Renderer/ComponentRenderer.swift, apps/web-harness/src/sdui/ComponentRenderer.tsx, tools/titan/extract-fixture.mjs) are never left edited. Deliver each change as a unified diff \`${'${OUT}'}/seam-<n>.patch\` that applies clean with \`git apply --check\` on HEAD (L7/L10: on HEAD with the named prior lanes' patches applied first — say which). To VERIFY a seam patch (your pins green with it applied): take the per-file lock — \`mkdir ${LOCK}/<basename>\` succeeds = yours (poll every 30 s up to 20 min; if never free, record "seam verification blocked by lock" in notVerified and move on) — then \`shasum -a 256\` the file, \`git apply\` your patch, run your focused tests, restore with \`git show HEAD:<path> > <path>\`, re-shasum (must equal), \`rmdir\` the lock. Never hold a lock while doing anything else.
(3) NO DEVICES: no emulator, simulator boot, test-all.sh, feed-*.mjs, capture-browser-ref on the corpus (L12 is the one exception for the re-freeze — see its brief). Focused tests only, always with filters (gradle --tests, xcodebuild -only-testing, vitest <file>, node --test <file>); the host runs twelve lanes.
(4) VERIFICATION OWED (§0 of the plan): pins on the VERBATIM per-test IR named in your brief, each proven able to fail by an EXECUTED mutation (restore byte-exact, sha-verified, record the mutation in the test header); a corpus census over the 1435 per-test IR docs (tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json) with a committed script under your results dir; a PNG replay where your brief asks for one; the watchlist lines for your predicted flips and at-risk cells already exist in tools/titan/results/wave52-plan/watchlist.txt — add any you discover to \`${'${OUT}'}/watchlist-additions.txt\` (same format).
(5) HOUSE RULES: every line commented (the WHY; spec section for extractors, platform API for appliers), new files ≤ 200 lines, no silent fallthrough (PropertyTracker / Log / console.warn once), say "runtime". The ring-fenced test filter-effects/backdrop-filter-basic-blur is never named in code.
(6) DURABLE ARTIFACTS: write \`${'${OUT}'}/_note.md\` (what changed, why, the census numbers, predicted flips with confidence, what you could not verify, hand-offs delivered/received), your census scripts + JSON, seam patches, hunks for other owners (\`hunk-for-<lane>-<n>.patch\`). Nothing goes to a scratchpad.
(7) Report DATA per the schema. Be honest in notVerified and designDeviations.

`
const LANE = {
  type: 'object',
  properties: {
    lane: { type: 'string' }, summary: { type: 'string' },
    filesChanged: { type: 'array', items: { type: 'string' } },
    seamPatches: { type: 'array', items: { type: 'string' } },
    hunksDelivered: { type: 'array', items: { type: 'string' } },
    pins: { type: 'array', items: { type: 'object', properties: { file: { type: 'string' }, test: { type: 'string' }, mutation: { type: 'string' }, result: { type: 'string' } }, required: ['file', 'test', 'mutation', 'result'] } },
    census: { type: 'string' },
    predictedFlips: { type: 'array', items: { type: 'string' } },
    commandsRun: { type: 'array', items: { type: 'string' } },
    notVerified: { type: 'array', items: { type: 'string' } },
    designDeviations: { type: 'array', items: { type: 'string' } },
    notePath: { type: 'string' },
  },
  required: ['lane', 'summary', 'filesChanged', 'seamPatches', 'pins', 'census', 'predictedFlips', 'commandsRun', 'notVerified', 'notePath'],
}
const SKEPTIC = {
  type: 'object',
  properties: {
    skeptic: { type: 'string' }, verdict: { type: 'string' },
    executedRepros: { type: 'array', items: { type: 'string' } },
    defects: { type: 'array', items: { type: 'object', properties: { severity: { type: 'string', enum: ['must-fix', 'should-fix', 'nit'] }, file: { type: 'string' }, claim: { type: 'string' }, evidence: { type: 'string' } }, required: ['severity', 'file', 'claim', 'evidence'] } },
    mustFixBeforeGate: { type: 'boolean' },
    censusRederived: { type: 'string' },
  },
  required: ['skeptic', 'verdict', 'executedRepros', 'defects', 'mustFixBeforeGate'],
}
const L = {
  L1: 'web-tail-colour-vt', L2: 'composed-canvas', L3: 'failure-ink', L4: 'small-fixes', L5: 'extractor-cascade', L6: 'counters-and-lists',
  L7: 'static-position', L8: 'vertical-wedges', L9: 'inline-run-wall', L10: 'flex-nowrap-gaps', L11: 'all-reset-postload-colour', L12: 'instrument-and-calibration',
}
const build = (id, extra) => agent(RULES.replaceAll('${OUT}', OUT(L[id])) + `YOUR LANE: ${id} · ${L[id]}. Read ${PLAN} §0, §1, your §2 section "### ${id} ·", §3 (your seam hunks), §4 (where you land), §5 (what you yielded / receive), §9 (the plan-skeptic corrections — they override §2 where they conflict), then the brief(s) your section cites under ${T}/tools/titan/results/wave52-plan/. Build exactly what your section says, in priority order (cheapest, highest-confidence targets first), and stop at the effort your section budgets. Results dir: ${OUT(L[id])}/. ${extra || ''}`, { label: `build:${id}`, phase: extra ? 'Build B' : 'Build A', schema: LANE })

phase('Build A')
const A = ['L1', 'L2', 'L3', 'L4', 'L5', 'L6', 'L8', 'L9', 'L11', 'L12']
const builtA = (await parallel(A.map(id => () => build(id)))).filter(Boolean)
log(`build A: ${builtA.length}/${A.length} lanes reported`)

phase('Build B')
const seams = (id) => (builtA.find(b => b.lane && b.lane.startsWith(id)) || {}).seamPatches || []
const builtB = (await parallel([
  () => build('L7', `DEPENDENCY: your Compose and iOS seam hunks overlap L2's and L3's (§3). Cut your seam patches against HEAD with these patches applied first, in order — L2: ${JSON.stringify(seams('L2'))}; L3: ${JSON.stringify(seams('L3'))} — state that base in the patch header and in _note.md; verify under the lock by applying theirs then yours, running your pins, restoring byte-exact. Also read ${OUT(L['L3'])}/_note.md and ${OUT(L['L2'])}/_note.md first.`),
  () => build('L10', `DEPENDENCY: your 027 natives need L2's M1 (§4 "L2 before L10"). Read ${OUT(L['L2'])}/_note.md and its seam patches ${JSON.stringify(seams('L2'))}; cut your patches against HEAD with L2's applied first and say so.`),
])).filter(Boolean)
const built = [...builtA, ...builtB]
log(`build B: ${builtB.length}/2; total ${built.length}/12`)

const SK_RULES = `SKEPTIC RULES. Shared tree ${T}, branch campaign/wave52. Read-only EXCEPT: executing a mutation and restoring byte-exact (sha-verified), and applying a lane's seam patch under the per-file lock (mkdir ${LOCK}/<basename>; apply; test; \`git show HEAD:<path> > <path>\`; re-sha; rmdir). NEVER git checkout/restore/stash/reset/clean/commit/rebase. No devices. Focused tests only. You MUST produce EXECUTED repros: re-run the lane's pins; re-execute at least two of its mutations independently; re-derive its corpus census with your OWN script (never its JSON) and compare; apply each seam patch (--check, then under the lock with the pins); open the PNG of every predicted-flip cell and say whether the predicted picture is what the fix would plausibly produce; check the lane touched only its owned paths (git diff --name-only against HEAD, filtered to its "own:" list — anything else is must-fix); check every-line comments / ≤200 lines on new files; check no silent fallthrough; check the standing constraints (ring-fence, no test-name carve-outs, device A/B hash sentence where a device A/B is staged). A defect is must-fix if it would produce a wrong render, break a build or suite, leave a pin vacuous, touch another lane's file, or claim an unmeasured blast radius. Write your working to ${T}/tools/titan/results/wave52-<lane>/skeptic.md (durable). Output DATA per the schema.

`
phase('Skeptics')
const sk = (await parallel(built.map(b => () => {
  const id = Object.keys(L).find(k => b.lane && b.lane.startsWith(k)) || b.lane
  return agent(SK_RULES + `LANE UNDER AUDIT: ${b.lane}. Its report:\n${JSON.stringify(b, null, 1)}\n\nIts plan section: ${PLAN} "### ${id} ·"; its results dir: ${OUT(L[id] || b.lane)}/.`, { label: `skeptic:${id}`, phase: 'Skeptics', schema: SKEPTIC })
}))).filter(Boolean)
const mustFix = sk.flatMap(s => s.defects.filter(d => d.severity === 'must-fix').map(d => ({ ...d, skeptic: s.skeptic })))
log(`skeptics: ${sk.length}/${built.length}; must-fix ${mustFix.length}`)

phase('Fix')
const laneOf = (d) => Object.keys(L).find(k => (d.skeptic || '').startsWith(k) || (d.skeptic || '').includes(L[k]))
const fixed = (await parallel(Object.keys(L).map(id => () => {
  const items = mustFix.filter(d => laneOf(d) === id)
  if (!items.length) return Promise.resolve(null)
  return agent(RULES.replaceAll('${OUT}', OUT(L[id])) + `YOUR LANE: ${id} · ${L[id]} (FIX PASS). Fix EVERY must-fix defect below within your owned paths (seam changes → re-cut your patch), re-run the affected pins and mutations, update _note.md, report.\n${JSON.stringify(items, null, 1)}`, { label: `fix:${id}`, phase: 'Fix', schema: LANE })
}))).filter(Boolean)
log(`fix passes: ${fixed.length}`)

phase('Re-verify')
const re = (await parallel(Object.keys(L).map(id => () => {
  const items = mustFix.filter(d => laneOf(d) === id)
  if (!items.length) return Promise.resolve(null)
  return agent(SK_RULES + `LANE: ${id} · ${L[id]} (RE-VERIFY). Confirm each must-fix below is fixed with an EXECUTED check and look for regressions. Append "## Re-verify" to ${OUT(L[id])}/skeptic.md.\nDefects:\n${JSON.stringify(items, null, 1)}\nFix report:\n${JSON.stringify(fixed.find(f => (f.lane || '').startsWith(id)) || null, null, 1)}`, { label: `reverify:${id}`, phase: 'Re-verify', schema: SKEPTIC })
}))).filter(Boolean)
const still = re.flatMap(s => s.defects.filter(d => d.severity === 'must-fix'))
log(`re-verify: remaining must-fix ${still.length}`)
return { built: built.map(b => ({ lane: b.lane, files: b.filesChanged.length, seams: b.seamPatches, pins: b.pins.length, flips: b.predictedFlips, notVerified: b.notVerified })), mustFix, fixed: fixed.map(f => f.lane), stillMustFix: still, skeptics: sk.map(s => ({ skeptic: s.skeptic, verdict: s.verdict.slice(0, 300), defects: s.defects.length, mustFixBeforeGate: s.mustFixBeforeGate })) }
