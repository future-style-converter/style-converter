export const meta = {
  name: 'wave55-build',
  description: 'Wave 55 builders (seven lanes, each in its own throwaway worktree, delivering unit + seam patches per PLAN §0 D18), one executed-repro skeptic per lane, fix lanes, re-verification; file-driven and resumable',
  phases: [
    { title: 'Build', detail: 'L1 hyphen-ios-linebox · L2 cbb-android · L3 positioned-parent-slot · L4 ua-heading-margin · L5 counter-suffix-rows · L6 android-paint-order · L7 instruments', model: 'opus' },
    { title: 'Skeptics', detail: 'one per lane: own census, patches applied to a throwaway worktree, mutation replay, revert states', model: 'opus' },
    { title: 'Fix', detail: 'must-fix defects (STATUS: PARTIAL first, COMPLETE last), then re-verification', model: 'opus' },
  ],
}
// tools/titan/results/wave55-plan/build-workflow.js — the wave-54 fan-out (wave54-plan/build-workflow.js) rewritten for the
// wave-55 lane model (PLAN.md §0 "Lanes deliver patches; they never edit the shared tree (new model, D18)" and §8 step 3):
// each lane works in its OWN throwaway worktree off HEAD (`git worktree add --detach <sp>/wave55-<role>-<lane> HEAD`, never a
// Workflow-isolated worktree), provisions it from the shared tree, writes its `TREES:` line FIRST, brackets every mutation
// with MUTATING / RESTORED sha256 lines, compiles every revert state, and delivers unit-<U>.patch + seam-<n>.patch + _note.md
// into tools/titan/results/wave55-<lane>/ ON THE SHARED TREE (the only shared-tree writes a lane makes). The evidence base is
// wave54-final, byte-identical to wave55-open (1435/1435 on all three platforms and the wire, ../wave55-gate/control-open.txt).
// args: { tree, head, sp, done?: ['L1', …], only?: ['L2'], model? }
const T = args.tree
const HEAD = args.head
const SPD = args.sp
const DONE = new Set(args.done || [])
const ONLY = args.only ? new Set(args.only) : null
const M = args.model || 'opus'
const PLAN = `${T}/tools/titan/results/wave55-plan/PLAN.md`
const EXP = `${T}/tools/titan/results/wave55-plan/expectations.json`
const MANIFEST = `${T}/tools/titan/results/wave55-plan/landing-manifest.json`
const G52 = `${T}/tools/titan/results/wave52-gate`
const REFS = `${T}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`
const L = { L1: 'hyphen-ios-linebox', L2: 'cbb-android', L3: 'positioned-parent-slot', L4: 'ua-heading-margin', L5: 'counter-suffix-rows', L6: 'android-paint-order', L7: 'instruments' }
const SECTION = Object.fromEntries(Object.entries(L).map(([id, name]) => [id, `### ${id} · ${name}`]))
const OUT = (id) => `${T}/tools/titan/results/wave55-${L[id]}`
const WT = (role, id) => `${SPD}/wave55-${role}-${id}`

const RULES = (role, id) => `HARD RULES — shared tree ${T}, branch campaign/applier-campaign, HEAD ${HEAD} (= dev 0d0d3a3d + the committed wave-55 plan + the opening-gate records). Read ${PLAN} §0 (the contract — every bullet binds you), §8 step 3 (the lane procedure and the RESUME rule), and your lane's §2 entry.
(0) RESUME FIRST (§8 step 3): if ${OUT(id)}/_note.md exists, read it; re-open the worktree its TREES: line names (or \`git -C ${T} worktree list\` under the name ${WT(role, id)}); run \`git status\` / \`git diff\` there on your owned paths; restore every \`MUTATING <file> sha256 <pre>\` line that has no \`RESTORED\` pair; DELETE every patch in your lane dir and re-cut all of them; re-run your focused suites and every revert state on the re-cut patches; rewrite the \`patch:\` lines; only then write STATUS:. Continue, do not restart.
(1) YOUR WORKTREE, never the shared tree: \`git -C ${T} worktree add --detach ${WT(role, id)} ${HEAD}\` (if it exists, re-use it). Provision it BEFORE any build (PLAN §0 "Provisioning"): \`cp ${T}/apps/android-harness/local.properties ${WT(role, id)}/apps/android-harness/\` and \`cp ${T}/local.properties ${WT(role, id)}/\`; \`ln -s ${T}/node_modules ${WT(role, id)}/node_modules\`; write \`TREES: ${WT(role, id)}\` as the FIRST line of ${OUT(id)}/_note.md (create the dir on the SHARED tree — the lane dir is the only shared-tree path you write). Every Swift build passes \`-derivedDataPath ${WT(role, id)}/.dd\`. Never commit, checkout, stash or reset anywhere; never remove the worktree (the orchestrator removes it after the landing, §8 step 9½). The WPT sources and refs are gitignored: read them from the SHARED tree (${T}/tools/wpt/…, ${REFS}/<section>/); captures and per-test IR from ${T}/tools/titan/runs/wave54-final/sections/<section>/.
(2) OWNERSHIP: edit only your lane's "own:" paths (PLAN §2 "${SECTION[id]}") inside your worktree. SEAM FILES are never edited directly (runtimes/compose/…/core/renderer/ComponentRenderer.kt, runtimes/swiftui/…/Renderer/ComponentRenderer.swift, apps/web-harness/src/sdui/ComponentRenderer.tsx, tools/titan/extract-fixture.mjs): a lane that needs a hunk edits the seam file in its worktree ONLY to cut \`seam-<n>.patch\` (one seam file + its patch-borne test files per revert unit; the patch header names the base tree ${HEAD}, the seam file's sha256 at base and the content anchor — PLAN §3 / seam-registry.md fix the anchor and the order), then RESTORES the seam file byte-exact before cutting the unit patches. Your owned files must COMPILE without your seam patch (rule 2b): an edit that consumes a symbol only the seam creates rides inside the seam patch.
(3) HOST: NO device, NO Chromium, NO emulator / simulator / adb, no test-all.sh, no section-runner, no gate-driver, no feed-*.mjs, no puppeteer, no full suites, no smoke.sh. FOCUSED suites only, inside your worktree: Compose \`(cd ${WT(role, id)}/apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '<Class>')\` (harness code: \`:app:testDebugUnitTest\`; IC-cache errors → delete runtimes/compose/build/kotlin in YOUR worktree and retry); SwiftUI (worktree root) \`xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath ${WT(role, id)}/.dd -only-testing:StyleConverterRuntimeTests/<Class>\`; converter \`./gradlew :converter:test --tests '<filter>'\`; web \`npx vitest run <path>\`; tooling \`node --test <file>\`. Other lanes compile the same modules on this host at the same time: expect CPU contention, retry a transient failure, never touch a file you do not own. A device step you need is a WINDOW REQUEST (below), never a command you run.
(4) VERIFICATION OWED (PLAN §0 "Pins and instruments can fail", "Verbatim payloads"; your §2 entry): every pin on the VERBATIM per-test IR of ${T}/tools/titan/runs/wave54-final/sections/*/per-test-ir/ (= wave55-open's); every pin PROVEN able to fail by an EXECUTED mutation of the mechanism, BRACKETED in the note — write \`MUTATING <file> sha256 <pre>\` BEFORE the edit, run the focused test (red), restore byte-exact, write \`RESTORED <file> sha256 <post>\` (post == pre), run again (green); your own blast-radius census (write the script under ${OUT(id)}/ with its output .txt) cross-checked against ${EXP} lanes["${id}-${L[id]}"] (captureCarriers, wireCarriers, revertUnits) — a census that GROWS is reported in the note as a plan regeneration need, never hidden; re-run your lane's geometry probe(s) on wave54-final as a self-test (\`/opt/homebrew/bin/python3.12 -B ${T}/tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-open --lanes ${id} --self-test\`, exit 0: every GATING key FAILS today, every CONTROL key PASSES). LOOK AT THE PNGS (ref under ${REFS}/<section>/, captures under ${T}/tools/titan/runs/wave54-final/sections/<section>/{screenshots,ios-screenshots,android-screenshots}/) before and after reasoning about a fix.
(5) REVERT STATES COMPILE (PLAN §0): before delivery, for every chain of your units' revertOrder (${EXP} lanes["${id}-${L[id]}"].revertUnits[*].revertOrder, and the contingency units once relevant), bring your worktree to that state (apply the patches that state keeps on a clean ${HEAD} checkout of your worktree — use \`git -C ${WT(role, id)} checkout -- .\` + \`git clean -fd -- <owned paths>\` + \`git apply\` of the kept patches), compile and run the focused tests, and record \`revertStatesCompiled: <state>: <result>\` lines in the note; then restore the full state.
(6) HOUSE RULES: every line commented (the WHY; CSS spec section or parser file for extractors, platform API for appliers); new logic in NEW files ≤ 200 lines (an oversized file gets only a call site, a defaulted parameter or a one-line condition — PLAN §0 lists the files already past 300 lines and what each may receive); no silent fallthroughs (the breadcrumbs PLAN §0 names are part of your unit); "runtime", not "engine"; no scratchpad pointer in any delivered file (a \`TREES:\` line is exempt).
(7) DELIVERY (PLAN §0 "Lanes deliver patches", §4, ${MANIFEST} deliveryContract): in ${OUT(id)}/ on the SHARED tree — \`unit-<U>.patch\` per revert unit (ONLY that unit's owned paths; cut in your worktree with \`git add -N <new files>\` then \`git diff HEAD -- <that unit's paths>\`, so a new file is a creation hunk), \`seam-<n>.patch\` per unit that needs one (cut as in (2)), and \`_note.md\`: TREES: first; what changed and why with file:symbol pointers; one section per REVERT UNIT naming exactly the paths that unit's commit holds; the census with its script + output; every pin with its bracketed mutation lines; \`revertStatesCompiled:\` lines; predictions per target cell with confidence; "## ORCHESTRATOR WINDOW REQUESTS" (the exact command, its expected output, the decision it feeds — this wave's windows: stage 1 / stage 2 probes, [W-L7a], the L4 probe-time arm, the closing arms, L6 [W1]); then, AFTER the final restore, one \`patch: <name> sha256 <64 hex>\` line per delivered patch (sha256 of the file bytes — \`shasum -a 256\`); and LAST the line \`STATUS: COMPLETE\` or \`STATUS: PARTIAL — <what is left>\`. \`land-units.sh --check\` refuses a note missing TREES:, STATUS: COMPLETE, or any patch's \`patch:\` line / sha256, and any *.patch the ORDER table does not know — read ${MANIFEST} for the exact names it expects for your lane.
(8) Report DATA per the schema; be honest in notVerified.
`

const LANE = {
  type: 'object',
  properties: {
    lane: { type: 'string' }, status: { type: 'string', enum: ['COMPLETE', 'PARTIAL'] }, summary: { type: 'string' },
    worktree: { type: 'string' },
    patches: { type: 'array', items: { type: 'object', properties: { name: { type: 'string' }, unit: { type: 'string' }, sha256: { type: 'string' }, paths: { type: 'array', items: { type: 'string' } } }, required: ['name', 'unit', 'sha256', 'paths'] } },
    revertStatesCompiled: { type: 'array', items: { type: 'string' } },
    windowRequests: { type: 'array', items: { type: 'string' }, description: 'commands the orchestrator must run in a device-idle window, verbatim, each with its expected output and the decision it feeds' },
    pins: { type: 'array', items: { type: 'object', properties: { test: { type: 'string' }, mutation: { type: 'string' }, result: { type: 'string' } }, required: ['test', 'mutation', 'result'] } },
    censusDelta: { type: 'string', description: 'how your own census compares with expectations.json (identical / grew by … / shrank by …)' },
    predictedFlips: { type: 'array', items: { type: 'string' } },
    notVerified: { type: 'array', items: { type: 'string' } },
    remaining: { type: 'array', items: { type: 'string' } },
  },
  required: ['lane', 'status', 'summary', 'worktree', 'patches', 'revertStatesCompiled', 'windowRequests', 'pins', 'censusDelta', 'predictedFlips', 'notVerified'],
}
const SKEPTIC = {
  type: 'object',
  properties: {
    skeptic: { type: 'string' }, verdict: { type: 'string', enum: ['CLAIM-HOLDS', 'MIXED', 'DEFECT-CONFIRMED'] },
    executedRepros: { type: 'array', items: { type: 'string' } },
    defects: { type: 'array', items: { type: 'object', properties: { severity: { type: 'string', enum: ['must-fix', 'should-fix', 'nit'] }, file: { type: 'string' }, what: { type: 'string' }, evidence: { type: 'string' }, fix: { type: 'string' } }, required: ['severity', 'file', 'what', 'evidence', 'fix'] } },
    mustFixBeforeGate: { type: 'boolean' },
  },
  required: ['skeptic', 'verdict', 'executedRepros', 'defects', 'mustFixBeforeGate'],
}

const buildPrompt = (id) => `You are builder lane ${id} (${L[id]}) of wave 55 of the Style-Converter applier campaign — seven lanes, each aimed at a picture that is wrong today (or an instrument that cannot fail), each with the mechanism traced in a brief and pre-registered in the plan.
${RULES('build', id)}
YOUR LANE: ${PLAN} "${SECTION[id]}" is your contract — targets, mechanism, the change, spec citation, ownership, revert units (with their revertOrder chains), carrier set, pins + mutations, predictions, must-not-move cells, window requests, what you may run. The brief behind it is ${T}/tools/titan/results/wave55-plan/${L[id]}.md (with its census, replays, looks and probe draft beside it); the generated contract is ${EXP} lanes["${id}-${L[id]}"] and ${MANIFEST} (your expected patch names and order). Do the work in your worktree; deliver per rule (7). Return the schema.`

const skepticPrompt = (id) => `You are the adversarial SKEPTIC of builder lane ${id} (${L[id]}) of wave 55. Read ${OUT(id)}/_note.md, the lane's §2 contract in ${PLAN} ("${SECTION[id]}"), its brief, ${EXP} lanes["${id}-${L[id]}"] and ${MANIFEST}.
${RULES('skeptic', id).replace('You are builder lane', 'You are the skeptic of')}
MUST DO, by execution in YOUR OWN worktree ${WT('skeptic', id)} (create it per rule (1); apply the lane's delivered patches there with \`git apply\` in landing order — a patch that does not apply on ${HEAD} is a must-fix): (a) replay every pin's mutation yourself (apply, run the focused test, confirm red, restore byte-exact, confirm green); (b) re-derive the lane's blast-radius census with YOUR OWN script over the per-test IR / sources (never the lane's script) and compare with ${EXP} lanes["${id}-${L[id]}"] captureCarriers / wireCarriers and each revertUnits entry — an under-reported radius is the dangerous direction; (c) check every claimed verdict/score with \`node ${G52}/cells.mjs '<terms>' wave54-final\`; (d) open the PNGs the lane reasoned from and check the described geometry; (e) look for silent fallthroughs, untested branches, files over 200 lines of new logic, uncommented lines, scratchpad pointers (TREES: exempt), probe leftovers, edits outside ownership (the patches' paths vs the own: list), a seam patch whose header does not name ${HEAD} and the seam file's sha256 at base, an owned file that does not compile without the seam patch (rule 2b); (f) re-run every revert state the note claims compiled (\`revertStatesCompiled:\`) on your worktree; (g) check each unit patch's path list equals the note's unit section and the manifest's expectation, and that every \`patch:\` sha256 line matches the file bytes; (h) honesty: are predicted flips labelled DEGENERATE where the picture would still be wrong? Write ${OUT(id)}/skeptic.md (verdict, repros with outputs, defects ranked, what you could not check) and return the schema. Never edit the lane's patches or note.`

const fixPrompt = (id, defects) => `You are the FIX lane for builder lane ${id} (${L[id]}) of wave 55. The skeptic found these defects with executed evidence (${OUT(id)}/skeptic.md has the full text):
${JSON.stringify(defects, null, 1)}
${RULES('fix', id).replace('You are builder lane', 'You are the fix lane of')}
FIRST write \`STATUS: PARTIAL — fix-r1 in progress\` as a new last line of ${OUT(id)}/_note.md (PLAN §8 step 3 "Fix lanes"), then work in your worktree ${WT('fix', id)} (apply the builder's patches there first). Re-run each skeptic check first; fix every must-fix (and should-fix when safe) inside the lane's ownership; re-run the touched focused suites; re-execute the affected mutations (bracketed); re-cut EVERY patch of the lane (delete the old ones), re-run the revert states, rewrite the \`patch:\` lines; append "## Fix pass" to the note (defect → action, outputs); and LAST write \`STATUS: COMPLETE\` (or PARTIAL — why). Return the schema.`

const reverifyPrompt = (id) => `You are the RE-VERIFIER of builder lane ${id} (${L[id]}) of wave 55. ${OUT(id)}/skeptic.md lists must-fix defects and ${OUT(id)}/_note.md "## Fix pass" claims each is fixed. For EACH must-fix: re-run the skeptic's check in your own worktree ${WT('reverify', id)} with the lane's CURRENT patches applied; confirm the fix or report it unfixed with evidence. Also confirm the note's \`patch:\` sha256 lines match the current patch bytes and the last STATUS line is COMPLETE.
${RULES('reverify', id).replace('You are builder lane', 'You are the re-verifier of')}`

const ids = Object.keys(L).filter((id) => !DONE.has(id) && (!ONLY || ONLY.has(id)))
log(`lanes this run: ${ids.join(' ')} (done: ${[...DONE].join(' ') || 'none'})`)

phase('Build')
const results = await parallel(ids.map((id) => async () => {
  const built = await agent(buildPrompt(id), { label: `build:${id}-${L[id]}`, phase: 'Build', model: M, schema: LANE })
  if (!built) return { id, built: null }
  const sk = await agent(skepticPrompt(id), { label: `skeptic:${id}`, phase: 'Skeptics', model: M, schema: SKEPTIC })
  if (!sk || !sk.mustFixBeforeGate) return { id, built, skeptic: sk }
  const must = sk.defects.filter((d) => d.severity !== 'nit')
  const fixed = await agent(fixPrompt(id, must), { label: `fix:${id}`, phase: 'Fix', model: M, schema: LANE })
  const re = await agent(reverifyPrompt(id), { label: `reverify:${id}`, phase: 'Fix', model: M, schema: SKEPTIC })
  return { id, built, skeptic: sk, fixed, reverified: re }
}))
return { lanes: results.filter(Boolean).map((r) => ({ id: r.id, status: r.fixed?.status || r.built?.status, summary: (r.fixed || r.built)?.summary?.slice(0, 500), patches: (r.fixed || r.built)?.patches, windowRequests: (r.fixed || r.built)?.windowRequests, censusDelta: (r.fixed || r.built)?.censusDelta, skeptic: r.skeptic && { verdict: r.skeptic.verdict, mustFix: r.skeptic.mustFixBeforeGate, defects: r.skeptic.defects.length }, reverified: r.reverified && { verdict: r.reverified.verdict, mustFix: r.reverified.mustFixBeforeGate }, notVerified: (r.fixed || r.built)?.notVerified })) }
