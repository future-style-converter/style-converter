#!/usr/bin/env bash
SP=/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/fix
X=$SP/swF; FAP=runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/FloatAvoidPlan.swift
m(){ local id=$1; shift; echo -n "$id "; python3 $SP/swmut.py "$X" "$@"; }
m SW-G6b FloatAvoidPlanTests $FAP '&& [nil, "STATIC", "RELATIVE"].contains(keyword($0.properties, "Position"))' '&& true'
