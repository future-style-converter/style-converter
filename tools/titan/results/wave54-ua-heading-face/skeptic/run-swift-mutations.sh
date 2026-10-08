#!/usr/bin/env bash
# Skeptic (wave 54 L5) — Catalyst baseline, then M3s / M5s replayed and S6 / S7 (mine).
set -u
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
D=tools/titan/results/wave54-ua-heading-face/skeptic
xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
  -only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests -only-testing:StyleConverterRuntimeTests/UAHeadingFoldGateTests > $D/catalyst-baseline.log 2>&1
echo "[baseline] rc=$? $(grep -E 'Executed [0-9]+ tests?' $D/catalyst-baseline.log | tail -1)"
M=$D/mutate.py
G=runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift
R=runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift
python3 $M M3s-old-hasChildren-gate swift $G '        guard component.children?.isEmpty == false else { return false }
' '        guard component.children?.isEmpty == false else { return false }
        if true { return true }
'
python3 $M M5s-ua-step-writes-color swift $R '            out = substituting(out, type: "FontWeight", with: entry)
' '            out = substituting(out, type: "FontWeight", with: entry)
            out = substituting(out, type: "Color", with: IRProperty(type: "Color", data: .object(["srgb": .object(["r": .double(1), "g": .double(0), "b": .double(0)])])))
'
python3 $M S6-gate-fold-on-empty-list swift $G 'containerProperties: mergedPreUA,' 'containerProperties: [],'
python3 $M S7-gate-ignores-runs swift $G 'return flow == nil' 'return flow == nil && false'
echo BATCH-DONE
