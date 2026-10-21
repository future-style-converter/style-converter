#!/usr/bin/env python3
# scratchpad-scrub.py — wave 55 pre-landing records scrub (prelanding/citations.md item 4, censuses-and-deliveries.md
# "Records hygiene"; PLAN §8 step 18: "`grep` for a scratchpad path returns nothing (lane notes' `TREES:` lines excepted)").
#
# Usage (run from anywhere; python3 -I so nothing is imported from the cwd):
#   python3 -I scratchpad-scrub.py --repo <shared tree> --root <session scratchpad root> [--apply] --out <list.txt>
# Without --apply it is a dry run: it prints the same list and writes nothing.
#
# The root is an ARGUMENT, never a literal here, so this record carries no scratchpad path itself. The macOS alias of
# /private/tmp (/tmp) is matched too: the L4 xcodebuild logs spell the root as /tmp/… (same directory).
#
# Rules (the task's (a)/(b)):
#   - *.patch files are NEVER edited (their bytes are hashed in the notes' `patch:` lines and their '-' lines are the
#     tree's context); they are listed when they carry the root, for the exemption record.
#   - a line beginning `TREES:` is exempt by design (PLAN §0 "No scratchpad pointers", round-1 N6) and left as is.
#   - every other line: `<root>/wave55-<role>-<id>` → `<wt:<role>-<id>>`, then `<root>` → `<scratch>`.
#   - one targeted rule: wave55-instruments/u3/selftest.base-bytes.out.txt carries the self-test's `cut -c1-110`
#     truncation of its stage dir, `(records in <the root's first 15 chars>)` — a prefix of
#     <scratch>/l7/abst0/tmp/ab-arms55.XXXXXX (the self-test exports TMPDIR=$S/tmp/ and $S = <scratch>/l7/abst0)
#     → `(records in <scratch>…)`, "…" being the file's own truncation mark. The 15-char prefix is derived from --root,
#     so it is asserted to BE a prefix of the root, and is never spelled out in this script or its list.
#   - binary files (NUL byte or not UTF-8) are not edited; the run asserts none of them carries the root.
# Self-checks: line count unchanged; every untouched line byte-identical; every changed line rebuilds byte-exactly from
# the scrubbed line and the recorded originals (in memory only: the originals are scratch paths, never written).
import argparse, hashlib, os, re, sys

LANE_DIRS = ["wave55-hyphen-ios-linebox", "wave55-cbb-android", "wave55-positioned-parent-slot",
             "wave55-ua-heading-margin", "wave55-instruments"]          # L1 L2 L3 L4 L7 (L5, L6 owned elsewhere)
WL7A_DIR = "wave55-gate/wl7a"                                          # only its *.txt
U3_FILE = "wave55-instruments/u3/selftest.base-bytes.out.txt"

ap = argparse.ArgumentParser()
ap.add_argument("--repo", required=True)
ap.add_argument("--root", required=True)
ap.add_argument("--out", required=True)
ap.add_argument("--apply", action="store_true")
a = ap.parse_args()

res = os.path.join(a.repo, "tools/titan/results")
root = a.root.rstrip("/")
bare = root[len("/private"):] if root.startswith("/private/") else root      # /tmp/… form
ROOT_RE = "(?:/private)?" + re.escape(bare)
# a lane worktree component ends at a non-name char; a name followed by an extension (wave55-x.txt) is a FILE in the
# root, not a worktree, so it keeps <scratch>/… (the lookahead backtracks the optional group away)
RX = re.compile(ROOT_RE + r"(?:/wave55-([A-Za-z0-9_-]+)(?![A-Za-z0-9_.-]*\.[A-Za-z0-9]))?")
SC_RE = re.compile(ROOT_RE)
U3_OLD, U3_NEW = "(records in " + root[:15] + ")", "(records in <scratch>…)"   # root[:15]: the cut -c1-110 remnant

def sha(b): return hashlib.sha256(b).hexdigest()

files = []
for d in LANE_DIRS:
    for dp, dn, fn in os.walk(os.path.join(res, d)):
        dn.sort()
        for f in sorted(fn):
            files.append(os.path.relpath(os.path.join(dp, f), res))
for f in sorted(os.listdir(os.path.join(res, WL7A_DIR))):
    if f.endswith(".txt"):
        files.append(os.path.join(WL7A_DIR, f))
files.sort()

out = []
def emit(s): out.append(s)
emit("# scratchpad-scrub.txt — wave 55 pre-landing records scrub (" + ("APPLIED" if a.apply else "DRY RUN") + ")")
emit("# script: tools/titan/results/wave55-gate/prelanding/scratchpad-scrub.py sha256 "
     + sha(open(__file__, "rb").read()))
emit("# command: python3 -I scratchpad-scrub.py --repo <shared tree> --root <scratch>" + (" --apply" if a.apply else "")
     + " --out <this file>   (<scratch> = the session scratchpad root, matched with and without its /private prefix)")
emit("# scope: tools/titan/results/{" + ",".join(LANE_DIRS) + "}/** + " + WL7A_DIR + "/*.txt — "
     + str(len(files)) + " files (L5 wave55-counter-suffix-rows/ and L6 wave55-android-paint-order/ are NOT in scope)")
emit("# rules: <root>/wave55-<role>-<id> → <wt:<role>-<id>>; any other <root> → <scratch>; in " + U3_FILE
     + " the stage dir truncated by the self-test's cut -c1-110 to the root's first 15 chars → '(records in <scratch>…)';"
     " lines beginning 'TREES:' exempt;"
     " *.patch never edited; binaries not edited")
emit("# line format: <path>:<line>  subs=<n>  <placeholders written on that line>")
emit("")

tot_files = tot_lines = tot_subs = 0
patches_with_root, trees_lines, binaries, errors = [], [], [], []
wt_names = {}
per_file = []
for rel in files:
    p = os.path.join(res, rel)
    raw = open(p, "rb").read()
    if rel.endswith(".patch"):
        n = len(SC_RE.findall(raw.decode("utf-8", "surrogateescape")))
        if n or (rel == "wave55-instruments/unit-U3.patch"):
            trunc = raw.decode("utf-8", "surrogateescape").count(U3_OLD)
            patches_with_root.append((rel, n, trunc, sha(raw)))
        continue
    try:
        if b"\x00" in raw: raise UnicodeDecodeError("nul", b"", 0, 1, "NUL byte")
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        binaries.append(rel)
        if re.search(ROOT_RE.encode(), raw): errors.append(f"binary {rel} carries the root")
        continue
    lines = text.split("\n")            # keeps the trailing "" when the file ends in \n; joins back byte-exactly
    new_lines, changed = [], []
    for i, ln in enumerate(lines, 1):
        if ln.startswith("TREES:"):
            if SC_RE.search(ln): trees_lines.append(f"{rel}:{i}")
            new_lines.append(ln); continue
        # one left-to-right pass: each match is either a lane worktree path or the bare root; spans are recorded in
        # the NEW line so the rebuild check can put back exactly the bytes it took out (pre-existing <wt>/<scratch>
        # placeholders written by hand are never touched and never counted)
        pieces, spans, pos, npos = [], [], 0, 0
        for m in RX.finditer(ln):
            tok = f"<wt:{m.group(1)}>" if m.group(1) else "<scratch>"
            if m.group(1): wt_names[m.group(1)] = wt_names.get(m.group(1), 0) + 1
            pieces.append(ln[pos:m.start()]); npos += m.start() - pos
            spans.append((npos, npos + len(tok), m.group(0), tok)); pieces.append(tok); npos += len(tok)
            pos = m.end()
        pieces.append(ln[pos:]); nl = "".join(pieces)
        if rel == U3_FILE and U3_OLD in nl:
            j = nl.index(U3_OLD)
            if nl.count(U3_OLD) != 1: errors.append(f"U3 rule matched more than once {rel}:{i}")
            spans.append((j, j + len(U3_NEW), U3_OLD, U3_NEW)); spans.sort()
            nl = nl[:j] + U3_NEW + nl[j + len(U3_OLD):]
        if nl != ln:
            # rebuild: replace each recorded span (right to left) by its original; must equal the old line byte-exactly
            rb = nl
            for a0, a1, orig, tok in sorted(spans, reverse=True):
                if rb[a0:a1] != tok: errors.append(f"span mismatch {rel}:{i}")
                rb = rb[:a0] + orig + rb[a1:]
            if rb != ln: errors.append(f"rebuild mismatch {rel}:{i}")
            if SC_RE.search(nl): errors.append(f"root survives {rel}:{i}")
            kinds = {}
            for _, _, _, t in spans: kinds[t] = kinds.get(t, 0) + 1
            changed.append((i, len(spans), kinds))
        new_lines.append(nl)
    new = "\n".join(new_lines).encode("utf-8")
    if len(new_lines) != len(lines): errors.append(f"line count changed {rel}")
    for i, (o, n) in enumerate(zip(lines, new_lines), 1):
        if o != n and i not in {c[0] for c in changed}: errors.append(f"unlisted change {rel}:{i}")
    if changed:
        tot_files += 1; tot_lines += len(changed); s = sum(c[1] for c in changed); tot_subs += s
        per_file.append((rel, len(changed), s, sha(raw), sha(new), changed))
        if a.apply:
            with open(p + ".scrub-tmp", "wb") as fh: fh.write(new)
            os.replace(p + ".scrub-tmp", p)

emit(f"## summary: {tot_files} files rewritten, {tot_lines} lines, {tot_subs} substitutions; "
     f"{len(trees_lines)} TREES: lines left as is; {len(patches_with_root)} patch file(s) not edited; "
     f"{len(binaries)} binary/empty file(s) not edited; self-check errors {len(errors)}")
emit("## worktree placeholders written: " + ", ".join(f"<wt:{k}> ×{v}" for k, v in sorted(wt_names.items())))
emit("")
emit("## files (path  lines  subs  sha256 pre → post)")
for rel, nl_, s, h0, h1, _ in per_file:
    emit(f"{rel}  lines={nl_}  subs={s}  {h0} → {h1}")
emit("")
emit("## TREES: lines left as is (exempt by design: PLAN §0 'No scratchpad pointers', round-1 N6)")
for t in trees_lines: emit(t)
emit("")
emit("## *.patch files carrying the root — NOT edited (rule (a); exemption: prelanding/scratchpad-exemptions.md)")
for rel, n, trunc, h in patches_with_root:
    emit(f"{rel}  full-root occurrences={n}  15-char truncated-root occurrences={trunc}  sha256 {h}")
emit("")
emit("## binary / empty files not edited (none carries the root: asserted)")
for b in binaries: emit(b)
emit("")
emit("## self-check errors")
for e in errors: emit(e)
if not errors: emit("none — line counts unchanged, untouched lines byte-identical, every changed line rebuilds byte-exactly")
emit("")
emit("## every changed line")
for rel, _, _, _, _, changed in per_file:
    for i, n, kinds in changed:
        emit(f"{rel}:{i}  subs={n}  " + " ".join(f"{k}×{v}" for k, v in sorted(kinds.items())))

txt = "\n".join(out) + "\n"
if SC_RE.search(txt) or U3_OLD in txt: sys.exit("REFUSED: the list itself would carry the root (or its remnant)")
if a.apply or a.out != "-":
    with open(a.out, "w") as fh: fh.write(txt)
print("\n".join(out[:12 + len(per_file) + 4]))
sys.exit(1 if errors else 0)
