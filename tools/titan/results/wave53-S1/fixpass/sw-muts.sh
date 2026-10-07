#!/usr/bin/env bash
SP=/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/fix
X=$SP/swF; FAP=runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/FloatAvoidPlan.swift
UNI=runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundUniformity.swift
m(){ local id=$1; shift; echo -n "$id "; python3 $SP/swmut.py "$X" "$@"; }
m SW-S1 FloatAvoidPlanTests $FAP 'let w = shape.containerWidthPx.map { $0 * scale } ?? incomingWidth' 'let w = shape.containerWidthPx ?? incomingWidth'
m SW-S4 FloatAvoidPlanTests $FAP 'let used = shape.bfcInlineSizePx * scale ' 'let used = shape.bfcInlineSizePx '
m SW-G6 FloatAvoidPlanTests $FAP '\n            && [nil, "STATIC", "RELATIVE"].contains(keyword($0.properties, "Position"))' ''
m SW-SA5 WPTCaptureModeTests $UNI 'return be32(16) == 1 && be32(20) == 1 ' 'return true '
m SW-SU WPTCaptureModeTests $UNI '        layers.allSatisfy(layerIsUniform) && repeats.allSatisfy(entryRepeatsBoth)' '        true'
