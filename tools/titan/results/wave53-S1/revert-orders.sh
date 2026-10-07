#!/usr/bin/env bash
# S1: which revert orders of the landed unit commits apply cleanly (3-way git revert, in a scratch clone).
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
cd "$1" || exit 2
H=$(git -C "$R" rev-parse HEAD)
G=(git -c user.email=s1@x -c user.name=s1)
try(){ "${G[@]}" revert --abort >/dev/null 2>&1; git checkout -q --detach -f "$H"; git clean -qfd
  for c in "$@"; do
    if ! "${G[@]}" revert --no-edit "$c" >/dev/null 2>&1; then
      echo "  revert [$*] -> CONFLICT at $c in: $(git diff --name-only --diff-filter=U | xargs -n1 basename | tr '\n' ' ')"
      "${G[@]}" revert --abort >/dev/null 2>&1; return; fi
  done; echo "  revert [$*] -> clean"; }
echo "L3 (A must come out without its B units?):"
try add9de84
try 276757ea add9de84
try 276757ea 06c41979 add9de84
try 276757ea 06c41979 adfb3fe8 add9de84
try 06c41979; try 276757ea; try adfb3fe8
echo "every other unit alone on HEAD:"
for c in 54c8460f 9df67ae3 a8ffd1c6 205ab295 2c6ecea9 de54f458 6fcb3161; do try "$c"; done
echo "plan rule-6 orders (later commit first):"
try 2c6ecea9 205ab295
try a8ffd1c6 9df67ae3
