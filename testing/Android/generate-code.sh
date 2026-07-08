#!/bin/bash
set -e

INPUT_JSON="app/src/main/assets/composeOutput.json"
OUTPUT_KT="app/src/main/java/com/styleconverter/test/generated/GeneratedComponents.kt"

echo "[CodeGen] Generating Kotlin code from $INPUT_JSON..."

# Create output directory
mkdir -p "$(dirname "$OUTPUT_KT")"

# Use jq to parse JSON and generate Kotlin code
cat > "$OUTPUT_KT" << 'HEADER'
package com.styleconverter.test.generated

// AUTO-GENERATED FILE - DO NOT EDIT
// Generated from composeOutput.json

HEADER

# Extract and write imports (excluding RectangleShape which is not used)
jq -r '.imports[]' "$INPUT_JSON" | grep -v "RectangleShape" | while read import; do
    echo "import $import" >> "$OUTPUT_KT"
done

# Add additional required imports
cat >> "$OUTPUT_KT" << 'IMPORTS'
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Text

IMPORTS

# Extract and write component code
jq -r '.components[].composableCode' "$INPUT_JSON" >> "$OUTPUT_KT"

echo "" >> "$OUTPUT_KT"
echo "[CodeGen] Successfully generated $OUTPUT_KT"
