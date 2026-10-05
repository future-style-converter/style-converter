#!/usr/bin/env python3
# Wave 52 lane L4 (fix pass) — re-executes the mutations whose pinned files the
# fix pass touched (M1 BackfaceCull.decide, M3 the b″ appliers) and adds MS for
# the new kept-shadow breadcrumb. Each mutant edits only this lane's files; the
# original bytes are written back in `finally` and sha256-compared.
#
# Usage: JAVA_HOME=$(/usr/libexec/java_home -v 21) python3 mutate-refresh.py
import hashlib, pathlib, subprocess, sys, xml.etree.ElementTree as ET

# Repo root = four levels above this file (tools/titan/results/<lane>/).
ROOT = pathlib.Path(__file__).resolve().parents[4]
# The Compose runtime source root and the Gradle project that builds :runtime.
RT = "runtimes/compose/src/main/java/com/styleconverter/runtime/"
HARNESS = ROOT / "apps/android-harness"
# Where :runtime's JUnit XML lands (one file per test class).
RESULTS = ROOT / "runtimes/compose/build/test-results/testDebugUnitTest"


def head(rel):
    """The HEAD bytes of one file — M3 restores the pre-port appliers."""
    return subprocess.run(["git", "show", f"HEAD:{rel}"], cwd=ROOT, capture_output=True, check=True).stdout


def swap(rel, old, new):
    """A one-anchor text mutant, or fail loudly (a stale anchor proves nothing)."""
    def f(b):
        t = b.decode()
        if t.count(old) != 1:
            sys.exit(f"anchor matched {t.count(old)}x in {rel}: {old!r}")
        return t.replace(old, new).encode()
    return f


BC = RT + "transforms/BackfaceCull.kt"
MUTANTS = {
    # M1 (re-run): the preserve-3d split is gone — every cull hides the subtree.
    "M1": ({BC: swap(BC, "            Decision.CULL_OWN_FACE\n        else Decision.HIDE_SUBTREE",
                     "            Decision.HIDE_SUBTREE\n        else Decision.HIDE_SUBTREE")},
           ["transforms.BackfaceCullTest", "transforms.BackfaceCullChainTest"]),
    # MS: the kept box-shadow is silent again.
    "MS": ({BC: swap(BC, "if (config.effects.shadows.hasShadow) PropertyTracker.markUnhandled(SHADOW_KEPT_BREADCRUMB)",
                     "if (false) PropertyTracker.markUnhandled(SHADOW_KEPT_BREADCRUMB)")},
           ["transforms.BackfaceCullTest"]),
    # M3 (re-run): both spacing appliers back to their pre-port HEAD bytes.
    "M3": ({RT + "spacing/MarginApplier.kt": lambda b: head(RT + "spacing/MarginApplier.kt"),
            RT + "spacing/PaddingApplier.kt": lambda b: head(RT + "spacing/PaddingApplier.kt")},
           ["spacing.PercentSpacingContainingBlockLevelTest"]),
}


def run(classes):
    """Run the named classes; return {test: 'pass'|'FAIL: msg'} (compile failure reported)."""
    args = ["./gradlew", ":runtime:testDebugUnitTest", "-q"]
    for c in classes:
        (RESULTS / f"TEST-com.styleconverter.runtime.{c}.xml").unlink(missing_ok=True)
        args += ["--tests", f"com.styleconverter.runtime.{c}"]
    proc = subprocess.run(args, cwd=HARNESS, capture_output=True, text=True)
    out = {}
    for c in classes:
        xml = RESULTS / f"TEST-com.styleconverter.runtime.{c}.xml"
        if not xml.exists():
            return {"<compile>": "FAIL: " + " | ".join(l for l in (proc.stdout + proc.stderr).splitlines() if l.startswith("e: "))[:400]}
        for tc in ET.parse(xml).getroot().iter("testcase"):
            fail = tc.find("failure")
            out[f"{c}.{tc.get('name')}"] = "pass" if fail is None else "FAIL: " + (fail.get("message") or "")[:140]
    return out


def sha(b): return hashlib.sha256(b).hexdigest()[:16]


for name, (edits, classes) in MUTANTS.items():
    originals = {rel: (ROOT / rel).read_bytes() for rel in edits}
    print(f"\n== {name}: before", {pathlib.Path(r).name: sha(b) for r, b in originals.items()})
    try:
        for rel, fn in edits.items():
            (ROOT / rel).write_bytes(fn(originals[rel]))
        res = run(classes)
    finally:
        for rel, b in originals.items():
            (ROOT / rel).write_bytes(b)
    red = {k: v for k, v in res.items() if v != "pass"}
    print(f"   {len(res) - len(red)}/{len(res)} green, {len(red)} red")
    for k, v in sorted(red.items()):
        print("   ", k, "->", v)
    after = {pathlib.Path(r).name: sha((ROOT / r).read_bytes()) for r in edits}
    print("   after ", after, "(equal)" if all((ROOT / r).read_bytes() == b for r, b in originals.items()) else "(DIFFERENT!)")
res = run(["transforms.BackfaceCullTest", "transforms.BackfaceCullChainTest", "spacing.PercentSpacingContainingBlockLevelTest"])
print("\nrestored run:", sum(v == "pass" for v in res.values()), "/", len(res), "green")
