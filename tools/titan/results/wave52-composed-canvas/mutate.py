#!/usr/bin/env python3
"""wave-52 lane L2 — executed-mutation runner (proof that a pin CAN fail).

Usage: mutate.py <id> <file> <old> <new> --evidence <kind> -- <test command...>
  Replaces the single occurrence of <old> with <new> in <file> (refuses when
  the count is not exactly 1), runs the focused test command, then restores
  the file BYTE-EXACT from an in-memory copy and checks sha256 before == after.

  The verdict needs ASSERTION evidence, never a bare exit code (skeptic
  should-fix 3: a compile error or a runner crash proves nothing):
    junit:<dir>[:<Class>]  RED iff a JUnit XML under <dir> (of <Class>) written DURING this run
                 records failures/errors > 0 (no fresh XML = the build
                 broke = INCONCLUSIVE); evidence = the first <failure> message.
    xcode        RED iff xcodebuild prints "Executed N tests, with M failures"
                 with M > 0; evidence = the first `error: -[` assertion line.
    vitest       RED iff vitest prints "Tests  N failed" with N > 0;
                 evidence = the first `AssertionError` / `expected` line.
  Anything else with a non-zero exit is INCONCLUSIVE; exit 0 is GREEN.

  Appends ONE replayable line to mutations.log beside this script:
    <id> <file> sha=<before16> old=<json> new=<json> exit=<rc>
      verdict=<RED|GREEN|INCONCLUSIVE> evidence=<json> restored=<ok|FAIL> cmd=<json>
  A mutation is "proven" only when verdict=RED and restored=ok.
Read-only on everything but <file>, which it always restores (try/finally).
"""
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent          # results dir (the log lives here)


def sha(path: pathlib.Path) -> str:
    # sha256 of the file's exact bytes — the restore check compares these.
    return hashlib.sha256(path.read_bytes()).hexdigest()


def junit_evidence(spec: str, started: float):
    # `<dir>[:<TestClass>]` — the class narrows the XML to the focused test's
    # own report, so a concurrent lane's run in the same build dir is ignored.
    xml_dir, _, cls = spec.partition(":")
    # Only XML written by THIS run counts — stale reports are not evidence.
    fresh = [p for p in pathlib.Path(xml_dir).glob(f"TEST-*{cls}.xml") if p.stat().st_mtime >= started]
    if not fresh:
        return "INCONCLUSIVE", "no fresh JUnit XML (build failed before the tests ran)"
    for p in sorted(fresh):
        text = p.read_text(encoding="utf-8", errors="replace")
        # A <failure>/<error> element is an executed, failed assertion.
        m = re.search(r'<(failure|error) message="([^"]*)"', text)
        if m:
            return "RED", f"{p.stem}: {m.group(2)[:240]}"
    return "GREEN", f"{len(fresh)} fresh XML, 0 failures"


def xcode_evidence(out: str):
    # XCTest's own summary line — present only when the tests executed.
    counts = [int(n) for n in re.findall(r"Executed \d+ tests?, with (\d+) failures?", out)]
    if not counts:
        return "INCONCLUSIVE", "no 'Executed N tests' line (build or runner failure)"
    if max(counts) == 0:
        return "GREEN", "Executed, 0 failures"
    line = next((l for l in out.splitlines() if "error: -[" in l), "failure count > 0")
    return "RED", line.strip()[-240:]


def vitest_evidence(out: str):
    # vitest's summary: "Tests  2 failed | 67 passed (69)".
    m = re.search(r"Tests\s+(\d+) failed", out)
    if not m:
        return ("GREEN", "0 failed") if re.search(r"Tests\s+\d+ passed", out) else \
            ("INCONCLUSIVE", "no vitest Tests summary")
    line = next((l for l in out.splitlines() if "AssertionError" in l or "expected" in l), "")
    return "RED", f"{m.group(1)} failed: {line.strip()[:200]}"


def main() -> int:
    # Split the argv at the `--` separator: mutation spec | test command.
    sep = sys.argv.index("--")
    head, cmd = sys.argv[1:sep], sys.argv[sep + 1:]
    ev = head[head.index("--evidence") + 1]
    mid, file_arg, old, new = [a for i, a in enumerate(head)
                               if a != "--evidence" and (i == 0 or head[i - 1] != "--evidence")]
    path = pathlib.Path(file_arg).resolve()
    original = path.read_bytes()                         # the byte-exact restore source
    before = sha(path)
    text = original.decode("utf-8")
    # A mutation must hit exactly one site, or it proves nothing about the pin.
    if text.count(old) != 1:
        print(f"{mid}: expected 1 occurrence, found {text.count(old)} — refusing", file=sys.stderr)
        return 2
    rc, out = None, ""
    started = time.time() - 1                            # 1 s slack for mtime granularity
    try:
        path.write_bytes(text.replace(old, new).encode("utf-8"))
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        rc, out = proc.returncode, proc.stdout.decode("utf-8", errors="replace")
    finally:
        path.write_bytes(original)                       # always restore, even on Ctrl-C
    restored = "ok" if sha(path) == before else "FAIL"
    if ev.startswith("junit:"):
        verdict, evidence = junit_evidence(ev.split(":", 1)[1], started)
    elif ev == "xcode":
        verdict, evidence = xcode_evidence(out)
    else:
        verdict, evidence = vitest_evidence(out)
    # A clean exit with no assertion failure is GREEN whatever the parser saw.
    if rc == 0 and verdict != "GREEN":
        verdict = "GREEN"
    line = (f"{mid} {file_arg} sha={before[:16]} old={json.dumps(old)} new={json.dumps(new)} exit={rc} "
            f"verdict={verdict} evidence={json.dumps(evidence)} restored={restored} cmd={json.dumps(' '.join(cmd))}")
    with open(HERE / "mutations.log", "a", encoding="utf-8") as log:
        log.write(line + "\n")
    print(line)
    return 0 if restored == "ok" else 3


if __name__ == "__main__":
    sys.exit(main())
