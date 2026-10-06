#!/usr/bin/env python3
# Wave 52 lane L4 (fix pass) — executed mutations for the Compose T5 WIRING
# pins (BackfaceCullChainTest W1-W4, skeptic must-fix 1). Each mutant is a
# text substitution on StyleApplier.kt (this lane's file); the original bytes
# are held in memory and written back in `finally`, and the sha256 before and
# after every mutant is printed and compared, so a crash cannot leave a mutant
# in the shared tree. Only the two backface test classes run (rule 4: focused).
#
# Usage: JAVA_HOME=$(/usr/libexec/java_home -v 21) python3 mutate-t5-wiring.py
import hashlib, pathlib, re, subprocess, sys, xml.etree.ElementTree as ET

# Repo root = four levels above this file (tools/titan/results/<lane>/).
ROOT = pathlib.Path(__file__).resolve().parents[4]
# The applier under mutation, and the Gradle project that builds :runtime.
SRC = ROOT / "runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt"
HARNESS = ROOT / "apps/android-harness"
# Where :runtime's JUnit XML lands (one file per test class).
RESULTS = ROOT / "runtimes/compose/build/test-results/testDebugUnitTest"
# The fully-qualified decision enum prefix as StyleApplier spells it.
D = "com.styleconverter.runtime.transforms.BackfaceCull.Decision"

# M4a: the gate alpha's every culled element again (CULL_OWN_FACE included).
GATE = (f"if (backface == {D}.HIDE_SUBTREE) {{", f"if (backface != {D}.NONE) {{")
# M4b: the decoration steps paint the unstripped config (the face comes back).
PAINT = (re.compile(r"val paint =\n\s*if \(backface == [\w.]+\.CULL_OWN_FACE\)\n\s*[\w.]+\.stripOwnFace\(config\)\n\s*else config"),
         "val paint = config")
# M4c: only the self-paint route's three calls read `config.` again.
SELF = [("result, paint.borders.radius, paint.borders.sides,\n                paint.colors.backgroundColor,",
         "result, config.borders.radius, config.borders.sides,\n                config.colors.backgroundColor,"),
        (".applyOutline(result, paint.borders.outline)", ".applyOutline(result, config.borders.outline)"),
        ("ColorApplier.applyColors(result, paint.colors.copy(backgroundColor = null))",
         "ColorApplier.applyColors(result, config.colors.copy(backgroundColor = null))")]


def sub_once(text, old, new):
    """Replace exactly one occurrence, or fail loudly (a stale mutant is no proof)."""
    if isinstance(old, re.Pattern):
        out, n = old.subn(new, text)
    else:
        n = text.count(old)
        out = text.replace(old, new)
    if n != 1:
        sys.exit(f"mutant anchor matched {n}x, expected 1: {old!r}")
    return out


def m4a(t): return sub_once(t, *GATE)
def m4b(t): return sub_once(t, *PAINT)
def m4(t): return m4b(m4a(t))
def m4c(t):
    for old, new in SELF:
        t = sub_once(t, old, new)
    return t


def run_pins():
    """Run the two backface classes; return {test name: 'pass'|'FAIL: msg'}."""
    # Stale XML from a previous run must never pose as this mutant's result
    # (a mutant that fails to COMPILE writes none — that then raises below).
    for old in RESULTS.glob("TEST-com.styleconverter.runtime.transforms.BackfaceCull*.xml"):
        old.unlink()
    subprocess.run(["./gradlew", ":runtime:testDebugUnitTest", "-q",
                    "--tests", "com.styleconverter.runtime.transforms.BackfaceCull*"],
                   cwd=HARNESS, capture_output=True, text=True)
    out = {}
    # Gradle exits non-zero on red tests; the XML is the record either way.
    for cls in ("BackfaceCullChainTest", "BackfaceCullTest"):
        f = RESULTS / f"TEST-com.styleconverter.runtime.transforms.{cls}.xml"
        for tc in ET.parse(f).getroot().iter("testcase"):
            fail = tc.find("failure")
            out[f"{cls}.{tc.get('name')}"] = "pass" if fail is None else "FAIL: " + (fail.get("message") or "")[:160]
    return out


def sha(b): return hashlib.sha256(b).hexdigest()


original = SRC.read_bytes()
print("StyleApplier.kt sha256 before:", sha(original))
try:
    for name, fn in (("M4", m4), ("M4a", m4a), ("M4b", m4b), ("M4c", m4c)):
        SRC.write_bytes(fn(original.decode()).encode())
        res = run_pins()
        red = {k: v for k, v in res.items() if v != "pass"}
        print(f"\n== {name}: {len(res) - len(red)}/{len(res)} green, {len(red)} red")
        for k, v in sorted(red.items()):
            print("  ", k, "->", v)
        # Restore after EVERY mutant so the shared tree is never left mutated.
        SRC.write_bytes(original)
        assert sha(SRC.read_bytes()) == sha(original), "restore failed"
finally:
    SRC.write_bytes(original)
print("\nStyleApplier.kt sha256 after: ", sha(SRC.read_bytes()), "(equal)" if SRC.read_bytes() == original else "(DIFFERENT!)")
res = run_pins()
print("restored run:", sum(v == "pass" for v in res.values()), "/", len(res), "green")
