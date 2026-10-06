#!/usr/bin/env python3
"""wave-52 lane L7 — executed-mutation runner (proof that a pin CAN fail).

Usage: mutate.py <id> <file> <old> <new> <red-regex> -- <test command...>
  Replaces the single occurrence of <old> with <new> in <file> (refuses when
  the count is not exactly 1), runs the focused test command with its output
  captured to _mut-<id>.log, then restores the file BYTE-EXACT from an
  in-memory copy and checks sha256 before == after.
  RED requires POSITIVE evidence of an assertion failure: <red-regex> must
  match the captured output (or a JUnit XML under the command's report dir,
  via the WAVE52_XML env var) — a compile error or an infrastructure crash
  exits non-zero too, and is recorded as INCONCLUSIVE, never as proof.
  Appends one line to mutations.log beside this script.
Read-only on everything but <file>, which it always restores (try/finally).
"""
import glob
import hashlib
import os
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent          # results dir (logs live here)


def sha(path: pathlib.Path) -> str:
    # sha256 of the file's exact bytes — the restore check compares these.
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    # Split argv at `--`: mutation spec | test command.
    sep = sys.argv.index("--")
    mid, file_arg, old, new, red = sys.argv[1:sep]
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
    log_path = HERE / f"_mut-{mid}.log"
    rc = None
    try:
        path.write_bytes(text.replace(old, new).encode("utf-8"))
        # The focused test run; output kept as evidence.
        with open(log_path, "w", encoding="utf-8") as out:
            rc = subprocess.run(cmd, stdout=out, stderr=subprocess.STDOUT).returncode
    finally:
        path.write_bytes(original)                       # always restore, even on Ctrl-C
    after = sha(path)
    restored = "ok" if after == before else "FAIL"
    # Evidence: the captured output plus any JUnit XML the run redirected.
    evidence = log_path.read_text(encoding="utf-8", errors="replace")
    xml_dir = os.environ.get("WAVE52_XML")
    if xml_dir:
        for f in glob.glob(os.path.join(xml_dir, "*.xml")):
            evidence += pathlib.Path(f).read_text(encoding="utf-8", errors="replace")
    hit = re.search(red, evidence)
    verdict = "RED" if (rc not in (0, None) and hit) else ("GREEN" if rc == 0 else "INCONCLUSIVE")
    line = (f"{mid} {file_arg} sha={before[:16]} test-exit={rc} verdict={verdict} "
            f"evidence={'`' + hit.group(0)[:80] + '`' if hit else '-'} restored={restored}")
    with open(HERE / "mutations.log", "a", encoding="utf-8") as log:
        log.write(line + "\n")
    print(line)
    return 0 if restored == "ok" else 3


if __name__ == "__main__":
    sys.exit(main())
