#!/usr/bin/env python3
"""Skeptic (wave 54 L4) — independent GAP reach census. Lists every container
with a PAINTING rule family (style not none/hidden, width not 0) whose rule axis
has a zero/absent gap, with the facts that decide whether a ZERO-extent gap can
occur there (display, direction, wrap, justify/align-content, item margins,
item count). Grid / multicol are listed too: the natives' hook is flex-only
(rememberGapDecorationSink(isFlex) / CSSFlexLayout .gapDecorations), so a
non-flex row is out of reach by construction. Usage: gap_reach.py <run-dir>"""
import json, sys, glob, os
def last(props, *t):
    v = None
    for p in props:
        if p["type"] in t: v = p["data"]
    return v
def px(d):
    if isinstance(d, dict):
        if isinstance(d.get("px"), (int, float)): return d["px"]
        if d.get("type") == "percentage": return f"{d.get('value')}%"
        return "?"
    if isinstance(d, (int, float)): return d
    return d
def painting(props, fam):
    st = last(props, f"{fam}RuleStyle")
    w = last(props, f"{fam}RuleWidth")
    if st is None or (isinstance(st, str) and st.upper() in ("NONE", "HIDDEN")): return False
    if isinstance(st, list) and all(str(x).upper() in ("NONE", "HIDDEN") for x in st): return False
    return not (isinstance(w, dict) and w.get("px") == 0)
rows = []
for f in sorted(glob.glob(os.path.join(sys.argv[1], "sections/*/per-test-ir/*.json")), key=os.path.basename):
    comps = json.load(open(f))["components"]
    kids = {}
    for c in comps: kids.setdefault((c.get("slot") or {}).get("parent"), []).append(c)
    for c in comps:
        P = c.get("properties") or []
        fams = [fam for fam in ("Column", "Row") if painting(P, fam)]
        if not fams: continue
        cg, rg = last(P, "ColumnGap"), last(P, "RowGap")
        zero = [fam for fam, g in (("Column", cg), ("Row", rg)) if fam in fams and (g is None or px(g) == 0 or g == "normal" or (isinstance(g, str) and g.upper() == "NORMAL"))]
        ks = kids.get(c["id"], [])
        disp = last(P, "Display")
        mar = sorted({p["type"] + "=" + str(px(p["data"])) for k in ks for p in (k.get("properties") or []) if p["type"].startswith("Margin") and px(p["data"]) not in (0, None)})
        rows.append((os.path.basename(f)[:-5], c["id"][-12:], disp, last(P, "FlexDirection"), last(P, "FlexWrap"),
                     "rules=" + "+".join(fams), f"cg={px(cg)} rg={px(rg)}", "ZERO-ON:" + ("+".join(zero) or "-"),
                     f"jc={last(P,'JustifyContent')} ac={last(P,'AlignContent')}", f"items={len(ks)}", "margins=" + (",".join(mar)[:80] or "-")))
flex = [r for r in rows if str(r[2]).upper() in ("FLEX", "INLINE_FLEX")]
print(f"rule-painting containers {len(rows)} (flex {len(flex)}); with a zero/absent gap on a ruled axis:")
for r in rows:
    if r[7] != "ZERO-ON:-": print(" ", *r)
