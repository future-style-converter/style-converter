#!/usr/bin/env python3
# Wave 52 · lane L9 (inline-run-wall) — the executed-mutation runner.
#
# Why this exists: PLAN.md §0 (i) — "each [pin] proven able to fail by the
# named mutation". Every mutation below is ONE exact substring replacement
# (asserted to occur exactly once, so a drifted source fails loudly instead
# of silently mutating nothing), followed by the focused test command, then
# a byte-exact restore verified by sha256 (before == after, or the run
# aborts). It runs against an ISOLATED copy of the tree (`--root`: HEAD +
# this lane's files + the lane's seam patch applied), never the shared
# worktree, so no other lane's in-flight edit can make a pin fail for the
# wrong reason and no seam file in the shared tree is ever touched.
#
# Usage: mutate.py --root <iso> --spec <spec.json> --out <result.json>
#   spec: {"cmd": [...], "cwd": "<rel to root>", "mutations": [
#           {"id","file","old","new","expectFail":[substrings]}]}
#   The command's output is scanned for each expected failing test name;
#   a mutation is PROVEN when the command exits non-zero AND every expected
#   name appears in a failure line.

import argparse, hashlib, json, os, subprocess, sys


def sha(path):
    # sha256 of the file bytes — the restore check compares these.
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--spec", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    spec = json.load(open(a.spec))
    results = []
    for m in spec["mutations"]:
        path = os.path.join(a.root, m["file"])
        before = sha(path)
        src = open(path, encoding="utf-8").read()
        # Exactly one occurrence, or the spec has drifted from the source.
        n = src.count(m["old"])
        if n != 1:
            sys.exit(f"{m['id']}: expected exactly 1 occurrence in {m['file']}, found {n}")
        open(path, "w", encoding="utf-8").write(src.replace(m["old"], m["new"], 1))
        try:
            p = subprocess.run(spec["cmd"], cwd=os.path.join(a.root, spec["cwd"]),
                               capture_output=True, text=True)
            out = p.stdout + p.stderr
        finally:
            # Restore byte-exact, whatever the command did.
            open(path, "w", encoding="utf-8").write(src)
        after = sha(path)
        if after != before:
            sys.exit(f"{m['id']}: restore mismatch {before} != {after}")
        # Failure lines: Gradle "> name FAILED" / XCTest "' failed (".
        fail_lines = [l for l in out.splitlines()
                      if l.rstrip().endswith("FAILED") or "' failed (" in l or ": error: -[" in l]
        seen = {e: any(e in l for l in fail_lines) for e in m["expectFail"]}
        proven = p.returncode != 0 and all(seen.values())
        results.append({"id": m["id"], "file": m["file"], "exit": p.returncode,
                        "sha256Before": before, "sha256After": after,
                        "expectFail": seen, "failLines": fail_lines[:40],
                        "proven": proven})
        print(f"{m['id']}: exit={p.returncode} proven={proven} restored={after == before}")
    json.dump({"cmd": spec["cmd"], "results": results}, open(a.out, "w"), indent=1)
    # Non-zero when any mutation went unproven — the pin did not bite.
    sys.exit(0 if all(r["proven"] for r in results) else 1)


if __name__ == "__main__":
    main()
