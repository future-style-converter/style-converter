#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/mutate.py — wave 53 lane L2's EXECUTED mutation runner.
#
# For each named mutation: sha256 the target file, apply ONE exact-string replacement (refused unless the
# old text occurs exactly once), run the focused suite, read the JUnit XML for the failing testcases, restore
# the file from the in-memory original, assert the sha256 equals the pre-mutation hash, then re-run the same
# suite and require it green. The JSON result (one record per mutation) is the evidence the lane note cites.
#
# Usage:
#   python3 mutate.py <tree-root> <platform:compose|swift> <out.json> <mutation-id>...
# <tree-root> is the checkout (or export tree) whose sources are mutated and built.
import hashlib, json, os, re, subprocess, sys, glob, time

MUTATIONS = {
    # ── F1 (Compose PreBreakPipeline) ────────────────────────────────────────────────────────────────────
    'M-a': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt',
                old="if (text.indexOf(' ') < 0 && text.indexOf('\\u00AD') < 0) return identity",
                new="if (text.indexOf(' ') < 0) return identity",
                tests=['*PreBreakPipelineTest'], why='restore the space-only guard (pre-F1)'),
    'M-b': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt',
                old="        if (!unbreakableOverflow && !tookSoftHyphen) {\n            // The platform's own greedy breaking already agrees with CSS\n            // here — leave the frozen behaviour alone.\n            return identity\n        }\n",
                new="",
                tests=['*PreBreakPipelineTest'], why='drop the !unbreakableOverflow && !tookSoftHyphen early return'),
    'M-c': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt',
                old="val tookSoftHyphen = !dictionaryHyphenation && tookSoftHyphenBreak(text, lines)",
                new="val tookSoftHyphen = tookSoftHyphenBreak(text, lines)",
                tests=['*PreBreakPipelineTest'], why='remove !dictionaryHyphenation && from the F5 trigger'),
    'M-over': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt',
                old="if (text.indexOf(' ') < 0 && text.indexOf('\\u00AD') < 0) return identity",
                new="",
                tests=['*PreBreakPipelineTest'], why='over-widening: delete the space-less guard outright'),
    # ── F2 (Compose InlineRunFold / InertOutOfFlowMember) ────────────────────────────────────────────────
    'M-F2-off': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt',
                old="} else if (tag in TEXT_MEMBER_TAGS && InertOutOfFlowMember.admits(child)) {",
                new="} else if (false) {",
                tests=['*InlineRunFoldTest'], why='remove the F2 arm (the pre-F2 fold)'),
    'M-F2-wide': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="    fun admits(member: IRComponent): Boolean {\n",
                new="    fun admits(member: IRComponent): Boolean {\n        if (member.properties.any { it.type == \"Position\" && ValueExtractors.extractKeyword(it.data)?.uppercase() in OUT_OF_FLOW_POSITIONS }) return true\n",
                tests=['*InlineRunFoldTest'], why='widen the predicate to any abspos/fixed member'),
    # ── Fix pass (skeptic D2/D3): the predicate halves the builder's pins left unproven. Strings are the skeptic's
    #    verbatim (skeptic-evidence/mut-x*.json) so the before/after runs replay exactly his mutations.
    'x1': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="        if (!member.children.isNullOrEmpty() || !member.runs.isNullOrEmpty() ||\n            !member.decorations.isNullOrEmpty()\n        ) return false",
                new="",
                tests=['*InlineRunFoldTest'], why='delete the children/runs/decorations structure check'),
    'x2': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="        return a <= 0f",
                new="        return true",
                tests=['*InlineRunFoldTest'], why='any explicit alpha counts as transparent'),
    'x3': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="        val a = srgb[\"a\"]?.jsonPrimitive?.floatOrNull ?: return false",
                new="        val a = srgb[\"a\"]?.jsonPrimitive?.floatOrNull ?: return true",
                tests=['*InlineRunFoldTest'], why='a missing alpha counts as transparent'),
    'x4': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt',
                old="} else if (tag in TEXT_MEMBER_TAGS && InertOutOfFlowMember.admits(child)) {",
                new="} else if (InertOutOfFlowMember.admits(child)) {",
                tests=['*InlineRunFoldTest'], why='drop the TEXT_MEMBER_TAGS test from the F2 arm'),
    'x5': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="            \"Color\" -> if (alphaIsZero(prop.data)) transparentInk = true else return false",
                new="            \"Color\" -> transparentInk = true",
                tests=['*InlineRunFoldTest'], why='any declared Color counts as transparent ink'),
    # x3 above is the skeptic's PRE-fix line; the fix pass hardened that line (D7 safe cast), so the post-fix replay of
    # the same mutation ("a missing alpha counts as transparent") is x3p on the new text.
    'x3p': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="        val a = (srgb[\"a\"] as? JsonPrimitive)?.floatOrNull ?: return false",
                new="        val a = (srgb[\"a\"] as? JsonPrimitive)?.floatOrNull ?: return true",
                tests=['*InlineRunFoldTest'], why='a missing alpha counts as transparent (post-D7 line)'),
    # ── Fix pass (D3 FIXED, D7, D4): the fix pass's own new pins ──────────────────────────────────────────────
    'x6': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="    private val OUT_OF_FLOW_POSITIONS = setOf(\"ABSOLUTE\", \"FIXED\")",
                new="    private val OUT_OF_FLOW_POSITIONS = setOf(\"ABSOLUTE\")",
                tests=['*InlineRunFoldTest'], why='drop FIXED from the out-of-flow positions'),
    'x7': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="        val a = (srgb[\"a\"] as? JsonPrimitive)?.floatOrNull ?: return false",
                new="        val a = (srgb[\"a\"] as JsonPrimitive?)?.floatOrNull ?: return false",
                tests=['*InlineRunFoldTest'], why='the unsafe (throwing) alpha cast D7 replaced'),
    'x8': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt',
                old="            \"Hyphens\" -> {}\n",
                new="            \"Hyphens\" -> {}\n            \"BorderTopColor\", \"BorderRightColor\", \"BorderBottomColor\", \"BorderLeftColor\" -> {}\n",
                tests=['*InlineRunFoldTest'], why='re-admit the border-*-color tolerance D4 removed'),
    # ── F1-iOS (Swift SoftHyphenPolicy.admitsPreBreak) — run with platform `swift` ─────────────────────────
    'S-shy': dict(file='runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/SoftHyphenPolicy.swift',
                old="        guard text.unicodeScalars.contains(softHyphenScalar) else { return false }\n",
                new="        guard false else { return false }\n",
                tests=['SoftHyphenPolicyTests', 'GreedyLineBreakerTests'], why='drop the U+00AD clause (the pre-wave-53 precondition)'),
    'S-d3': dict(file='runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/SoftHyphenPolicy.swift',
                old="        guard horizontal else {\n",
                new="        guard horizontal || true else {\n",
                tests=['SoftHyphenPolicyTests', 'GreedyLineBreakerTests'], why='drop the D3 horizontal-only gate'),
    # WordBreakOpportunities.kt is READ-ONLY for L2: M-d runs ONLY in an export tree (never the shared tree).
    'M-d': dict(file='runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/WordBreakOpportunities.kt',
                old="            if (ch == '\\u00AD') {",
                new="            if (false && ch == '\\u00AD') {",
                tests=['*PreBreakPipelineTest'], why='WordBreakOpportunities:87 treats U+00AD as an ordinary character',
                exportOnly=True),
}


def sha256(path):
    # Byte hash of the file as it sits on disk.
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


def run_compose(root, tests):
    # The focused JVM suite through the harness build (rootDir = apps/android-harness).
    env = dict(os.environ)
    env['JAVA_HOME'] = subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip()
    cmd = ['./gradlew', ':runtime:testDebugUnitTest', '-q']
    for t in tests:
        cmd += ['--tests', t]
    t0 = time.time()
    p = subprocess.run(cmd, cwd=os.path.join(root, 'apps/android-harness'), env=env,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out = p.stdout.decode(errors='replace')
    # Failing testcases from the JUnit XML written by THIS run (mtime after t0).
    failed, total = [], 0
    keys = [t.strip('*') for t in tests]
    for x in glob.glob(os.path.join(root, 'runtimes/compose/build/test-results/testDebugUnitTest/*.xml')):
        # Only THIS run's classes: another lane's concurrent run writes into the same build dir.
        if os.path.getmtime(x) < t0 or not any(os.path.basename(x).endswith(k + '.xml') for k in keys):
            continue
        s = open(x, encoding='utf-8', errors='replace').read()
        total += len(re.findall(r'<testcase ', s))
        for m in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<failure', s):
            failed.append(f'{m.group(2).split(".")[-1]}.{m.group(1)}')
    compile_error = p.returncode != 0 and total == 0
    return dict(exit=p.returncode, total=total, failed=sorted(failed), compileError=compile_error,
                tail=out[-1500:] if compile_error else '')


def main():
    root, platform, out_path, ids = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
    shared = os.path.realpath(root).endswith('trusting-bohr-bd6fbf')
    results = []
    for mid in ids:
        m = MUTATIONS[mid]
        if m.get('exportOnly') and shared:
            sys.exit(f'{mid} edits a file L2 does not own; run it in an export tree only')
        path = os.path.join(root, m['file'])
        before = sha256(path)
        original = open(path, encoding='utf-8').read()
        if original.count(m['old']) != 1:
            sys.exit(f'{mid}: old text occurs {original.count(m["old"])}x in {m["file"]}')
        try:
            # Apply the mutation and run the suite (expected RED).
            with open(path, 'w', encoding='utf-8') as f:
                f.write(original.replace(m['old'], m['new']))
            mutated = sha256(path)
            red = (run_swift if platform == 'swift' else run_compose)(root, m['tests'])
        finally:
            # Restore byte-exact from the in-memory original, whatever happened.
            with open(path, 'w', encoding='utf-8') as f:
                f.write(original)
        after = sha256(path)
        assert after == before, f'{mid}: restore mismatch {after} != {before}'
        # Re-run on the restored file (expected GREEN).
        green = (run_swift if platform == 'swift' else run_compose)(root, m['tests'])
        rec = dict(id=mid, why=m['why'], file=m['file'], sha256Before=before, sha256Mutated=mutated,
                   sha256Restored=after, red=red, green=green,
                   verdict='RED->GREEN' if (red['failed'] or red['compileError']) and not green['failed'] and green['exit'] == 0 else 'NOT-PROVEN')
        results.append(rec)
        print(json.dumps({k: rec[k] for k in ('id', 'verdict')}), 'red:', red['failed'] or red['tail'][-300:],
              'green:', green['exit'], green['total'], flush=True)
    with open(out_path, 'w') as f:
        json.dump(results, f, indent=1)



# ── Swift (Catalyst) runner — appended for the iOS half ──────────────────────────────────────────────────────────
# The ONLY local way to run StyleConverterRuntimeTests is the Mac Catalyst destination (memory: swiftui-tests-catalyst).
# A PRIVATE -derivedDataPath keeps this lane's builds off the DerivedData other lanes' xcodebuilds may hold open.
SWIFT_DD = '/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/dd-l2'


def run_swift(root, classes):
    cmd = ['xcodebuild', 'test', '-scheme', 'StyleConverterRuntime',
           '-destination', 'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', SWIFT_DD]
    for c in classes:
        cmd.append('-only-testing:StyleConverterRuntimeTests/' + c)
    p = subprocess.run(cmd, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out = p.stdout.decode(errors='replace')
    # XCTest prints one line per failing case and a per-bundle summary.
    failed = sorted(set(re.findall(r"Test Case '-\[\S+ (test\w+)\]' failed", out)))
    m = re.findall(r'Executed (\d+) tests?, with (\d+) failures?', out)
    total = int(m[-1][0]) if m else 0
    compile_error = p.returncode != 0 and total == 0
    return dict(exit=p.returncode, total=total, failed=failed, compileError=compile_error,
                tail=out[-2500:] if (compile_error or p.returncode != 0) else '')


if __name__ == '__main__':
    main()
