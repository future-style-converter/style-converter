#!/usr/bin/env python3
# Wave 52 lane L4 (fix pass) — executed mutations for the Compose T7 pins after
# the unprovided-vs-none split (skeptic should-fix 2). Mutates this lane's
# TransformInheritance.kt only; original bytes held in memory, written back in
# `finally`, sha256 compared. Only the two T7 test classes run (rule 4).
#
# Usage: JAVA_HOME=$(/usr/libexec/java_home -v 21) python3 mutate-t7-sentinel.py
import hashlib, pathlib, subprocess, sys, xml.etree.ElementTree as ET

# Repo root = four levels above this file (tools/titan/results/<lane>/).
ROOT = pathlib.Path(__file__).resolve().parents[4]
# The resolver under mutation, and the Gradle project that builds :runtime.
SRC = ROOT / "runtimes/compose/src/main/java/com/styleconverter/runtime/transforms/TransformInheritance.kt"
HARNESS = ROOT / "apps/android-harness"
# Where :runtime's JUnit XML lands (one file per test class).
RESULTS = ROOT / "runtimes/compose/build/test-results/testDebugUnitTest"
# The two classes that pin T7.
CLASSES = ("TransformExtractorTest", "TransformInheritanceSeamTest")

MUTANTS = {
    # MU1: unprovided collapses into `none` again (the pre-fix conflation).
    "MU1": ("if (inherited == null) return properties", "if (inherited == null) return resolve(properties, NONE)"),
    # M2 (re-run of the lane's original T7 mutation): resolve is the identity.
    # A bare `return properties` no longer compiles (it makes the null check
    # below unreachable, so `inherited` loses its smart cast), hence the
    # equivalent identity on every non-empty list.
    "M2": ("if (!carriesInherit(properties)) return properties", "if (properties.isNotEmpty()) return properties"),
}


def run_pins():
    """Run the T7 classes; return {test name: 'pass'|'FAIL: msg'}."""
    # Stale XML must never pose as this mutant's result.
    for c in CLASSES:
        (RESULTS / f"TEST-com.styleconverter.runtime.transforms.{c}.xml").unlink(missing_ok=True)
    args = ["./gradlew", ":runtime:testDebugUnitTest", "-q"]
    for c in CLASSES:
        args += ["--tests", f"com.styleconverter.runtime.transforms.{c}"]
    proc = subprocess.run(args, cwd=HARNESS, capture_output=True, text=True)
    out = {}
    for c in CLASSES:
        xml = RESULTS / f"TEST-com.styleconverter.runtime.transforms.{c}.xml"
        # No XML = the mutant did not compile: report the compiler's words.
        if not xml.exists():
            errs = [l for l in (proc.stdout + proc.stderr).splitlines() if l.startswith("e: ")]
            return {"<compile>": "FAIL: no test ran — " + (" | ".join(errs)[:400] or "see gradle output")}
        for tc in ET.parse(RESULTS / f"TEST-com.styleconverter.runtime.transforms.{c}.xml").getroot().iter("testcase"):
            fail = tc.find("failure")
            out[f"{c}.{tc.get('name')}"] = "pass" if fail is None else "FAIL: " + (fail.get("message") or "")[:140]
    return out


def sha(b): return hashlib.sha256(b).hexdigest()


original = SRC.read_bytes()
print("TransformInheritance.kt sha256 before:", sha(original))
try:
    for name, (old, new) in MUTANTS.items():
        text = original.decode()
        # Exactly one anchor, or the mutant proves nothing.
        if text.count(old) != 1:
            sys.exit(f"{name}: anchor matched {text.count(old)}x")
        SRC.write_bytes(text.replace(old, new).encode())
        res = run_pins()
        red = {k: v for k, v in res.items() if v != "pass"}
        print(f"\n== {name}: {len(res) - len(red)}/{len(res)} green, {len(red)} red")
        for k, v in sorted(red.items()):
            print("  ", k, "->", v)
        SRC.write_bytes(original)
finally:
    SRC.write_bytes(original)
print("\nTransformInheritance.kt sha256 after: ", sha(SRC.read_bytes()), "(equal)" if SRC.read_bytes() == original else "(DIFFERENT!)")
res = run_pins()
print("restored run:", sum(v == "pass" for v in res.values()), "/", len(res), "green")
