#!/bin/bash
# Skeptic L6 sk6: run the own DOM census in each export tree over run $1.
SK=$(cd "$(dirname "$0")" && pwd); RUN=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs/$1; shift
for t in "$@"; do
  ( cd $SK/$t && SK6_TREE=$SK/$t SK6_RUN=$RUN SK6_OUT=$SK/census.$t.$(basename $RUN).json $SK/$t/node_modules/.bin/vitest run --config $SK/$t/sk6.config.mts > $SK/census.$t.$(basename $RUN).log 2>&1; echo "$t rc=$?" ) &
done
wait
