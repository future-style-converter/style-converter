#!/usr/bin/env bash
# replay a lane seam patch onto the unit commit's parent blob and compare to the landed blob
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
S="${1:-$(mktemp -d)}"   # scratch work dir (never the shared tree)
check(){ local sha=$1 f=$2 patch=$3; local d=$S/$sha; rm -rf "$d"; mkdir -p "$d"; cd "$d" || return; git init -q; mkdir -p "$(dirname "$f")"; git -C $R show "$sha^:$f" > "$f"; git add -A; git -c user.email=x -c user.name=x commit -qm base;
 if git apply --include="$f" "$R/$patch" 2>"$d/err"; then r=applied; else r="APPLY-FAIL $(head -3 "$d/err")"; fi
 local a b; a=$(shasum -a 256 "$f" | cut -c1-16); b=$(git -C $R show "$sha:$f" | shasum -a 256 | cut -c1-16)
 echo "$sha $f <- $patch : $r; patched=$a commit=$b $( [ "$a" = "$b" ] && echo MATCH || echo DIFFER)"; cd - >/dev/null; }
check 9df67ae3 tools/titan/extract-fixture.mjs tools/titan/results/wave53-lists-bakes/seam-1.patch
check 205ab295 runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift tools/titan/results/wave53-soft-hyphen/seam-1.patch
check 2c6ecea9 runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt tools/titan/results/wave53-soft-hyphen/seam-2.patch
check de54f458 runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt tools/titan/results/wave53-float-avoid/seam-1.patch
check 6fcb3161 runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift tools/titan/results/wave53-float-avoid/seam-2.patch
