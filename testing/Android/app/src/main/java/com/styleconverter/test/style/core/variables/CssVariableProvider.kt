package com.styleconverter.test.style.core.variables

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.styleconverter.test.style.core.ir.IRComponent
import com.styleconverter.test.style.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Provides CSS variables to descendant components.
 *
 * CSS variables are scoped - variables defined in a parent are available
 * to all descendants, and children can override parent values.
 *
 * ## Usage
 * ```kotlin
 * CssVariableProvider(
 *     variables = mapOf(
 *         "--primary" to "#3498db",
 *         "--spacing" to "16px"
 *     )
 * ) {
 *     // Child components can use var(--primary), etc.
 * }
 * ```
 */
@Composable
fun CssVariableProvider(
    variables: Map<String, String>,
    content: @Composable () -> Unit
) {
    val parentScope = LocalCssVariables.current
    val mergedScope = remember(parentScope, variables) {
        parentScope.merge(CssVariableScope(variables))
    }

    CompositionLocalProvider(LocalCssVariables provides mergedScope) {
        content()
    }
}

/**
 * Provides CSS variables from an IRComponent's properties.
 */
@Composable
fun CssVariableProvider(
    component: IRComponent,
    content: @Composable () -> Unit
) {
    val variables = remember(component.properties) {
        extractVariables(component.properties)
    }

    if (variables.isNotEmpty()) {
        CssVariableProvider(variables = variables, content = content)
    } else {
        content()
    }
}

/**
 * Provides CSS variables with scope merging.
 */
@Composable
fun CssVariableProvider(
    scope: CssVariableScope,
    content: @Composable () -> Unit
) {
    CssVariableProvider(variables = scope.variables, content = content)
}

/**
 * Provides theme-aware CSS variables.
 *
 * Automatically switches between light and dark theme variables
 * based on system theme.
 */
@Composable
fun ThemeVariableProvider(
    lightVariables: CssVariableScope = ThemeVariables.materialLight,
    darkVariables: CssVariableScope = ThemeVariables.materialDark,
    additionalVariables: CssVariableScope = CssVariableScope.EMPTY,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val themeVariables = if (isDark) darkVariables else lightVariables

    val combinedScope = remember(themeVariables, additionalVariables) {
        themeVariables.merge(additionalVariables)
    }

    CssVariableProvider(scope = combinedScope, content = content)
}

/**
 * Extract CSS variable definitions from IR properties.
 *
 * Looks for properties that define custom properties (--name: value).
 */
private fun extractVariables(properties: List<IRProperty>): Map<String, String> {
    val variables = mutableMapOf<String, String>()

    properties.forEach { prop ->
        // Check for custom property type
        if (prop.type == "CustomProperty" || prop.type.startsWith("--")) {
            prop.data?.let { data ->
                extractVariableFromData(prop.type, data)?.let { (name, value) ->
                    variables[name] = value
                }
            }
        }
    }

    return variables
}

/**
 * Extract variable name and value from property data.
 */
private fun extractVariableFromData(type: String, data: JsonElement): Pair<String, String>? {
    return try {
        when {
            // Type is the variable name itself
            type.startsWith("--") -> {
                val value = when {
                    data is JsonObject -> {
                        data["value"]?.jsonPrimitive?.content
                            ?: data["original"]?.jsonPrimitive?.content
                            ?: return null
                    }
                    else -> data.jsonPrimitive.content
                }
                type to value
            }
            // CustomProperty type with name and value in data
            type == "CustomProperty" && data is JsonObject -> {
                val obj = data.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return null
                val value = obj["value"]?.jsonPrimitive?.content ?: return null
                name to value
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Composable that provides both theme and component-level variables.
 */
@Composable
fun FullVariableProvider(
    component: IRComponent? = null,
    useTheme: Boolean = true,
    customVariables: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit
) {
    val wrappedContent: @Composable () -> Unit = {
        if (component != null) {
            CssVariableProvider(component = component, content = content)
        } else if (customVariables.isNotEmpty()) {
            CssVariableProvider(variables = customVariables, content = content)
        } else {
            content()
        }
    }

    if (useTheme) {
        ThemeVariableProvider(content = wrappedContent)
    } else {
        wrappedContent()
    }
}
