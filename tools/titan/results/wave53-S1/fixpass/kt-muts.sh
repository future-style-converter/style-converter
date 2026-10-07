#!/usr/bin/env bash
SP=/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/fix
X=$SP/ktF; FAP=runtimes/compose/src/main/java/com/styleconverter/runtime/layout/FloatAvoidPlan.kt
RBP=runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt
m(){ local id=$1; shift; echo -n "$id "; python3 $SP/ktmut.py "$X" "$@"; }
m FA-S1 :runtime:testDebugUnitTest '*FloatAvoidPlanTest*' $FAP 'val w = shape.containerWidthPx?.let { it * scale } ?: incomingWidth' 'val w = shape.containerWidthPx ?: incomingWidth'
m FA-S4 :runtime:testDebugUnitTest '*FloatAvoidPlanTest*' $FAP 'val used = shape.bfcInlineSizePx * scale' 'val used = shape.bfcInlineSizePx'
m FA-G6 :runtime:testDebugUnitTest '*FloatAvoidPlanTest*' $FAP '        if (floats.any { keyword(pairs(it), "Position") !in FLOAT_POSITIONS }) return null\n' ''
m XC1 :app:testDebugUnitTest '*ComposedCanvasRootBackground*' $RBP 'overpaintFrame = !plan.uniform)' 'overpaintFrame = false)'
m XC2 :app:testDebugUnitTest '*ComposedCanvasRootBackground*' $RBP 'CanvasPaint(layerConfigs(props, plan, frame).asReversed()' 'CanvasPaint(layerConfigs(props, plan, 0f).asReversed()'
m XC3 :app:testDebugUnitTest '*ComposedCanvasRootBackground*' $RBP 'CanvasPaint(layerConfigs(props, plan, frame).asReversed()' 'CanvasPaint(layerConfigs(props, plan, frame)'
m XC9 :app:testDebugUnitTest '*ComposedCanvasRootBackground*' $RBP '        if (!paint.overpaintFrame) return m ' '        return m '
