#!/usr/bin/env python3
# Wave 52 · lane L9 (inline-run-wall) — the blast-radius census, re-derived
# from the 1435 per-test IR docs of the run of record (PLAN.md §0 (ii)).
#
# Why a script and not a grep: each fix keys on a STRUCTURAL shape (a fold
# member's props, a clamp host's effective white-space, a run's hyphens mode)
# that only exists once slot.parent chains and meta.runs references are
# resolved. Every number in _note.md "Census" comes from this file's output
# (census.json beside it); a skeptic can re-run it read-only:
#   python3 tools/titan/results/wave52-inline-run-wall/census.py [RUN]
#
# Cell verdicts replay score-gate.mjs:50-51 exactly: a cell is SCORED when
# `diffs[<p>-ref].ssim` is a number and `scoreExcluded` is not set; it PASSES
# when `wptPass === true`.

import glob, json, os, sys

RUN = sys.argv[1] if len(sys.argv) > 1 else "wave51-fix"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
SECTIONS = os.path.join(ROOT, "tools", "titan", "runs", RUN, "sections")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "census.json")
SHY = "­"
PRESERVING = {"PRE", "PRE_WRAP", "PRE_LINE", "BREAK_SPACES"}   # css-text-3 §3
NOWRAP_LIKE = {"PRE", "NOWRAP"}          # no soft wrap: Visible on Compose (wave 39)


# Fix pass (skeptic M1) — the soft wrap opportunity classes OTHER than U+0020
# a drawn clamp's last line may hold. GreedyLineBreaker.clampHead (both twins)
# cuts at the first four and DECLINES on the last two; a host carrying none
# of them is cut exactly as the pre-fix U+0020-only loop cut it.
def opportunity_classes(text):
    out = set()
    for ch in text:
        c = ord(ch)
        if c in (0x09, 0x1680, 0x205F, 0x3000) or 0x2000 <= c <= 0x2006 or 0x2008 <= c <= 0x200A:
            out.add("spaceSeparator")          # UAX #14 BA/SP Zs (cut, hidden)
        elif c == 0x200B:
            out.add("zwsp")                    # ZW (cut)
        elif c in (0x2D, 0x2010, 0x2013, 0x2014):
            out.add("dash")                    # HY/BA/B2 break-after (cut)
        elif c == 0xAD:
            out.add("softHyphen")              # §5.3 manual (cut, hyphen painted)
        elif (0x2E80 <= c <= 0x9FFF and c != 0x3000) or 0xAC00 <= c <= 0xD7AF or 0xF900 <= c <= 0xFAFF \
                or 0xFF00 <= c <= 0xFFEF or 0x20000 <= c <= 0x3FFFF:
            out.add("ideographic")             # ID (decline)
        elif 0x0E00 <= c <= 0x0EFF or 0x1000 <= c <= 0x109F or 0x1780 <= c <= 0x17FF \
                or 0x1950 <= c <= 0x19DF or 0x1A20 <= c <= 0x1AAF or 0xAA60 <= c <= 0xAADF:
            out.add("saScript")                # SA (decline)
    return sorted(out)


def host_text(h, by_name):
    # The rendered paragraph: a fold host's meta.runs in order, else its text.
    runs = (h.get("meta") or {}).get("runs") or []
    if not runs:
        return h.get("text") or ""
    parts = []
    for r in runs:
        if "child" in r and r["child"] in by_name:
            parts.append(by_name[r["child"]].get("text") or "")
        elif isinstance(r.get("text"), str):
            parts.append(r["text"])
    return "".join(parts)


def cells_for(result):
    # score-gate.mjs:50-51 — scored = numeric ssim and not scoreExcluded.
    out = {}
    for plat in ("web", "ios", "android"):
        x = (result.get("browserRef") or {}).get("diffs", {}).get(f"{plat}-ref")
        if x and isinstance(x.get("ssim"), (int, float)) and not x.get("scoreExcluded"):
            out[plat] = {"pass": x.get("wptPass") is True, "ssim": x["ssim"]}
    return out


def kw(data):
    # The keyword wire: a bare string, or a {"type": …} sealed envelope.
    if isinstance(data, str):
        return data.upper().replace("-", "_")
    if isinstance(data, dict) and isinstance(data.get("type"), str):
        return data["type"].upper().replace("-", "_")
    return None


def prop(comp, t):
    # The component's OWN declaration of type t (last wins), or None.
    hit = [p["data"] for p in comp.get("properties", []) if p["type"] == t]
    return hit[-1] if hit else None


def inherited(comp, t, by_id):
    # Walk slot.parent for an inherited property (white-space and hyphens
    # are inherited, css-text-3 §3 / §5.3); own declaration first.
    seen = 0
    while comp is not None and seen < 64:
        v = prop(comp, t)
        if v is not None:
            return v
        parent = (comp.get("slot") or {}).get("parent")
        comp = by_id.get(parent)
        seen += 1
    return None


# Manifest results keyed by (section, per-test-ir stem).
results = {}
for mf in sorted(glob.glob(os.path.join(SECTIONS, "*", "manifest.json"))):
    sec = os.path.basename(os.path.dirname(mf))
    for key, r in json.load(open(mf))["wpt"]["results"].items():
        stem = "wpt__" + key[4:-5].replace("/", "__")
        results[(sec, stem)] = (key, r)

f1, f2, f3, f5, fold_clamp = [], [], [], [], []
docs = sorted(glob.glob(os.path.join(SECTIONS, "*", "per-test-ir", "*.json")))
for path in docs:
    sec = os.path.basename(os.path.dirname(os.path.dirname(path)))
    stem = os.path.basename(path)[:-5]
    key, r = results[(sec, stem)]
    comps = json.load(open(path))["components"]
    by_id = {c["id"]: c for c in comps}
    by_name = {c["name"]: c for c in comps}
    kids = {}
    for c in comps:
        kids.setdefault((c.get("slot") or {}).get("parent"), []).append(c)
    cells = cells_for(r)
    # ── fold members: every meta.runs child reference ─────────────────
    for host in comps:
        for run in (host.get("meta") or {}).get("runs", []) or []:
            m = by_name.get(run.get("child")) if "child" in run else None
            if m is None:
                continue
            types = [p["type"] for p in m.get("properties", [])]
            # F1 — a HangingPunctuation member (css-text-3 §8.3).
            if "HangingPunctuation" in types:
                f1.append({"test": key, "host": host["name"], "member": m["name"],
                           "text": m.get("text"), "props": types, "cells": cells})
            # F2 — glyph-less: whitespace-only text, no nested subtree.
            text = m.get("text") or ""
            nested = bool(kids.get(m["id"])) or bool((m.get("meta") or {}).get("runs"))
            if text and not text.strip() and not nested:
                ws = kw(prop(m, "WhiteSpace"))
                arms_only = set(types) <= {"WhiteSpace", "BackgroundColor"} and (ws is None or ws in PRESERVING)
                f2.append({"test": key, "host": host["name"], "member": m["name"], "props": types,
                           "whiteSpace": ws, "textLength": len(text),
                           "admittedByTheGlyphlessArmsAlone": arms_only, "cells": cells})
    # ── F3/F4 — LineClamp hosts (+ the bare MaxLines longhand) ─────────
    clamp_hosts = [c for c in comps if prop(c, "LineClamp") is not None or prop(c, "MaxLines") is not None]
    if clamp_hosts:
        rows = []
        for h in clamp_hosts:
            lc = prop(h, "LineClamp")
            fixed = isinstance(lc, dict) and lc.get("type") == "lines"
            ell = (lc or {}).get("ellipsis") if isinstance(lc, dict) else None
            suppressed = isinstance(ell, dict) and (ell.get("type") == "no-ellipsis"
                                                     or (ell.get("type") == "string" and not ell.get("value")))
            ws = kw(inherited(h, "WhiteSpace", by_id))
            sub = [h] + [c for c in comps if (c.get("slot") or {}).get("parent") == h["id"]]
            if not fixed:
                klass = "maxLinesOnly-noMarker"            # css-overflow-4 §5.1
            elif suppressed:
                klass = "markerSuppressed"                 # -023 / -024
            elif ws in NOWRAP_LIKE:
                klass = "preOrNowrap"                      # Visible, unchanged
            else:
                klass = "markerDrawnSoftWrapped"           # F3 (Compose) / F4 (iOS)
            rows.append({"host": h["name"], "count": (lc or {}).get("count") if fixed else None,
                         "class": klass, "whiteSpace": ws, "foldHost": bool((h.get("meta") or {}).get("runs")),
                         "softHyphen": any(SHY in (c.get("text") or "") for c in sub),
                         "fontFamily": prop(h, "FontFamily"), "width": prop(h, "Width"),
                         "nonSpaceOpportunities": opportunity_classes(host_text(h, by_name))})
        f3.append({"test": key, "hosts": rows, "cells": cells})
        if any(x["foldHost"] and x["class"] == "markerDrawnSoftWrapped" for x in rows):
            fold_clamp.append({"test": key, "cells": cells})
    # ── F5 — U+00AD carriers and the hyphens mode governing them ──────
    carriers = [c for c in comps if SHY in (c.get("text") or "")]
    if carriers:
        modes = sorted({kw(inherited(c, "Hyphens", by_id)) or "MANUAL(initial)" for c in carriers})
        f5.append({"test": key, "softHyphens": sum((c.get("text") or "").count(SHY) for c in carriers),
                   "hyphensModes": modes, "cells": cells})


def tally(rows, plat):
    # (scored, passing) on one platform over a list of {cells} rows.
    s = [x["cells"][plat] for x in rows if plat in x["cells"]]
    return {"scored": len(s), "passing": sum(1 for c in s if c["pass"])}


by_class = {}
for t in f3:
    k = "|".join(sorted({h["class"] for h in t["hosts"]}))
    by_class.setdefault(k, []).append(t)
summary = {
    "docs": len(docs),
    "F1_hangingPunctuationMembers": len(f1),
    "F2_glyphlessMembers": len(f2),
    "F2_admittedByTheGlyphlessArmsAlone": sum(1 for x in f2 if x["admittedByTheGlyphlessArmsAlone"]),
    "F3F4_clampTests": len(f3),
    "F3F4_byHostClass": {k: {"tests": len(v), "android": tally(v, "android"), "ios": tally(v, "ios"),
                             "testsList": [x["test"] for x in v]} for k, v in sorted(by_class.items())},
    "F4_foldHostsUnderADrawnClamp": [x["test"] for x in fold_clamp],
    # Fix pass (skeptic M1): drawn soft-wrapped clamp hosts whose paragraph
    # holds a non-U+0020 opportunity — the only hosts the clampHead walk can
    # treat differently from the pre-fix U+0020-only loop.
    "M1_drawnClampHostsWithNonSpaceOpportunities": [
        {"test": t["test"], "host": h["host"].split("__")[-1], "classes": h["nonSpaceOpportunities"],
         "cells": {p: [c["pass"], round(c["ssim"], 4)] for p, c in t["cells"].items()}}
        for t in f3 for h in t["hosts"]
        if h["class"] == "markerDrawnSoftWrapped" and h["nonSpaceOpportunities"]],
    "M1_drawnClampHostsDeclined": [
        t["test"] for t in f3 for h in t["hosts"]
        if h["class"] == "markerDrawnSoftWrapped"
        and set(h["nonSpaceOpportunities"]) & {"ideographic", "saScript"}],
    "F5_softHyphenTests": len(f5),
    "F5_android": tally(f5, "android"),
    "F5_byMode": {m: [x["test"] for x in f5 if m in x["hyphensModes"]]
                  for m in sorted({m for x in f5 for m in x["hyphensModes"]})},
}
json.dump({"run": RUN, "summary": summary, "F1": f1, "F2": f2, "F3F4": f3, "F5": f5},
          open(OUT, "w"), indent=1, ensure_ascii=False)
print(json.dumps(summary, indent=1, ensure_ascii=False))
