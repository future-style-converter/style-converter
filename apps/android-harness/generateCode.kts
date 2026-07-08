#!/usr/bin/env kotlin

import java.io.File
import kotlinx.serialization.json.*

// Read the JSON file
val jsonFile = File("app/src/main/assets/composeOutput.json")
val jsonContent = jsonFile.readText()

// Parse JSON manually (simple approach)
val json = Json.parseToJsonElement(jsonContent).jsonObject
val components = json["components"]?.jsonArray ?: error("No components found")
val imports = json["imports"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

// Generate the Kotlin file
val outputFile = File("app/src/main/java/com/styleconverter/test/generated/GeneratedComponents.kt")
outputFile.parentFile.mkdirs()

val code = buildString {
    appendLine("package com.styleconverter.test.generated")
    appendLine()
    appendLine("// AUTO-GENERATED FILE - DO NOT EDIT")
    appendLine("// Generated from composeOutput.json")
    appendLine()

    // Add imports
    imports.forEach { import ->
        appendLine("import $import")
    }

    // Add additional required imports
    appendLine("import androidx.compose.runtime.*")
    appendLine("import androidx.compose.ui.input.pointer.*")
    appendLine("import androidx.compose.foundation.focusable")
    appendLine("import androidx.compose.ui.focus.onFocusChanged")
    appendLine("import androidx.compose.foundation.layout.BoxScope")
    appendLine("import androidx.compose.foundation.layout.RowScope")
    appendLine("import androidx.compose.foundation.layout.BoxWithConstraints")
    appendLine("import androidx.compose.ui.geometry.Offset")
    appendLine("import androidx.compose.ui.draw.drawBehind")
    appendLine("import androidx.compose.material3.Text")
    appendLine()

    // Add each component
    components.forEach { componentElement ->
        val component = componentElement.jsonObject
        val composableCode = component["composableCode"]?.jsonPrimitive?.content ?: ""
        appendLine(composableCode)
        appendLine()
    }
}

outputFile.writeText(code)
println("[CodeGenerator] Generated ${outputFile.absolutePath}")
