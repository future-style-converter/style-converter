#!/usr/bin/env python3
"""Pointer audit: every backticked path-like token in the 7 lane notes (+ skeptic.md) must resolve to a file or dir,
either repo-relative, lane-dir-relative, or plan-dir-relative; reports MISSING, and EXISTS-BUT-NOT-COMMITTED (gitignored)."""
import os, re, subprocess, sys, glob
T = sys.argv[1]; R = f"{T}/tools/titan/results"
lanes = ["rtl-marker-bake","table-body-cell","hyphenate-character","oof-layout","ua-heading-face","web-tail","label-chrome"]
tracked = set(subprocess.run(["git","-C",T,"ls-files"],capture_output=True,text=True).stdout.split("\n"))
tracked_dirs = set()
for p in tracked:
    parts = p.split("/")
    for i in range(1,len(parts)): tracked_dirs.add("/".join(parts[:i]))
EXT = r"(?:mjs|js|ts|tsx|kt|swift|py|sh|json|txt|md|patch|png|log|mts|staged|tmpl|sha256|jsonl|html)"
tok = re.compile(r"`([^`\s]+)`")
def candidates(t, lane):
    t = t.rstrip(".,;:)").split(":")[0]           # drop :line anchors
    t = t.replace("…", "")                         # elided paths are skipped below
    yield t
    yield f"tools/titan/results/wave54-{lane}/{t}"
    yield f"tools/titan/results/wave54-plan/{t}"
    yield f"tools/titan/results/{t}"
    yield f"tools/titan/{t}"
    yield f"tools/visual/{t}"
missing=[]; ignored=[]; ok=0
for lane in lanes:
    for note in ["_note.md","skeptic.md"]:
        f = f"{R}/wave54-{lane}/{note}"
        if not os.path.exists(f): continue
        txt = open(f).read()
        for m in tok.finditer(txt):
            t = m.group(1)
            if "…" in t or "<" in t or "*" in t or "{" in t or "$" in t: continue   # templated / elided
            if not re.search(r"\."+EXT+r"(?::\d.*)?$|/$", t.rstrip(".,;:)")): continue
            if t.startswith("http") or "=" in t: continue
            found=None
            for c in candidates(t, lane):
                c = c.rstrip("/")
                if os.path.exists(f"{T}/{c}"): found=c; break
            if not found:
                # basename search within the lane dir (notes often cite bare names that live in subdirs)
                hits = glob.glob(f"{R}/wave54-{lane}/**/{os.path.basename(t.rstrip('/'))}", recursive=True)
                if hits: found = os.path.relpath(hits[0], T)
            if not found: missing.append((lane,note,t)); continue
            if os.path.isfile(f"{T}/{found}") and found not in tracked: ignored.append((lane,note,t,found))
            elif os.path.isdir(f"{T}/{found}") and found not in tracked_dirs: ignored.append((lane,note,t,found+"/ (dir)"))
            else: ok+=1
print(f"resolved+committed {ok} · missing {len(missing)} · exists-but-not-committed {len(ignored)}")
for x in missing: print("MISSING   ", *x)
for x in ignored: print("UNCOMMITTED", *x)
