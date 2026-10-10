#!/usr/bin/env bash
# Skeptic (wave 54 L5) — the Kotlin mutation batch: the lane's M1–M5 replayed, plus K6–K12 (mine).
set -u
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
M=tools/titan/results/wave54-ua-heading-face/skeptic/mutate.py
R=runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt
G=runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt
python3 $M M1-drop-own-size-guard kt $R 'if (multiplier != null && own.none { it.type in SIZE_DECLARING }) {' 'if (multiplier != null) {'
python3 $M M2-base-16 kt $R '            ?: MonospaceUAFontSize.resolveSp(merged)?.toDouble()
' ''
python3 $M M3-old-hasChildren-gate kt $G '        if (children.isNullOrEmpty()) return false
' '        if (children.isNullOrEmpty()) return false
        if (true) return true
'
python3 $M M4-weight-as-double kt $R 'JsonPrimitive(BOLD)' 'JsonPrimitive(BOLD.toDouble())'
python3 $M M5-ua-step-writes-color kt $R '            out = substituting(out, WEIGHT_TYPE, JsonObject(mapOf("weight" to JsonPrimitive(BOLD))))
' '            out = substituting(out, WEIGHT_TYPE, JsonObject(mapOf("weight" to JsonPrimitive(BOLD))))
            out = substituting(out, "Color", JsonObject(mapOf("srgb" to JsonObject(mapOf("r" to JsonPrimitive(1), "g" to JsonPrimitive(0), "b" to JsonPrimitive(0))))))
'
python3 $M K6-sup-half-gated kt $R 'val applies = !heading || !standsDown' 'val applies = !standsDown'
python3 $M K7-relative-mirror-off kt $G '== "RELATIVE") return true' '== "XRELATIVE") return true'
python3 $M K8-drop-own-weight-guard kt $R 'if (bold && own.none { it.type in WEIGHT_DECLARING }) {' 'if (bold) {'
python3 $M K9-append-not-substitute kt $R 'p.type != type -> p' 'p.type != type || true -> p'
python3 $M K10-h3-multiplier-1_2 kt $R '"h3" -> 1.17' '"h3" -> 1.2'
python3 $M K11-no-inherited-em-rung kt $R 'DynamicValueResolver.fontSizePxOf(merged)?.toDouble()' '(null as Double?)'
python3 $M K12-weight-half-ungated kt $R 'val bold = heading && applies' 'val bold = heading'
echo BATCH-DONE
