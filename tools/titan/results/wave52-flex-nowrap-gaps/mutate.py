#!/usr/bin/env python3
"""wave-52 lane L10 — executed-mutation runner (proof that a pin CAN fail).

Usage: mutate.py <id> <file> <old> <new> <expect-test> -- <test command...>
  Replaces the single occurrence of <old> with <new> in <file> (refuses when
  the count is not exactly 1), runs the focused test command, then restores
  the file BYTE-EXACT from an in-memory copy and checks sha256 before == after.
  <expect-test> is the test-method name that must be reported FAILED in the
  output (xcodebuild "error: -[… <name>]" / Gradle "<name> FAILED").
  Appends one line to mutations.log beside this script:
    <id> <file> sha=<before> exit=<rc> verdict=<RED|GREEN|NOPROOF> restored=<ok|FAIL>
  RED only when the named test is reported failed by an ASSERTION — a compile
  error or a crashed runner is NOPROOF (same rule as lane L2's runner).
Read-only on everything but <file>, which it always restores (try/finally).
"""
import hashlib
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent          # results dir (log lives here)


def sha(path: pathlib.Path) -> str:
    # sha256 of the file's exact bytes — the restore check compares these.
    return hashlib.sha256(path.read_bytes()).hexdigest()


def failed_by_assertion(out: str, test: str) -> bool:
    # xcodebuild: "<file>:<line>: error: -[Suite testName] : XCTAssert… failed".
    if re.search(r"error: -\[\S+ " + re.escape(test) + r"\] : ", out):
        return True
    # Gradle/JUnit console: "<Class> > <test name> FAILED" + an AssertionError.
    # (assertArrayEquals throws ArrayComparisonFailure, an AssertionError
    # subclass whose console name lacks the word — accept both spellings.)
    return bool(re.search(re.escape(test) + r".*FAILED", out)) and (
        "AssertionError" in out or "ComparisonFailure" in out)


def main() -> int:
    # Split the argv at the `--` separator: mutation spec | test command.
    sep = sys.argv.index("--")
    mid, file_arg, old, new, expect = sys.argv[1:sep]
    cmd = sys.argv[sep + 1:]
    path = pathlib.Path(file_arg).resolve()
    original = path.read_bytes()                         # the byte-exact restore source
    before = sha(path)
    text = original.decode("utf-8")
    # A mutation must hit exactly one site, or it proves nothing about the pin.
    count = text.count(old)
    if count != 1:
        print(f"{mid}: expected 1 occurrence, found {count} — refusing", file=sys.stderr)
        return 2
    rc, out = None, ""
    try:
        path.write_bytes(text.replace(old, new).encode("utf-8"))
        # The focused test run; output is kept to read the verdict from it.
        proc = subprocess.run(cmd, capture_output=True, text=True)
        rc, out = proc.returncode, proc.stdout + proc.stderr
    finally:
        path.write_bytes(original)                       # always restore, even on Ctrl-C
    after = sha(path)
    restored = "ok" if after == before else "FAIL"
    if rc == 0:
        verdict = "GREEN"                                # the pin did not notice
    else:
        verdict = "RED" if failed_by_assertion(out, expect) else "NOPROOF"
    (HERE / "logs").mkdir(exist_ok=True)
    (HERE / "logs" / f"{mid}.log").write_text(out[-20000:], encoding="utf-8")
    line = (f"{mid} {file_arg} sha={before[:16]} exit={rc} expect={expect} "
            f"verdict={verdict} restored={restored}")
    with open(HERE / "mutations.log", "a", encoding="utf-8") as log:
        log.write(line + "\n")
    print(line)
    return 0 if restored == "ok" else 3


if __name__ == "__main__":
    sys.exit(main())
