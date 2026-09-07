package app

import app.parsing.parsing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import kotlin.system.exitProcess

/*
Purpose: Kotlin CLI entry for the CSS → IR conversion pipeline.
Usage: style-converter convert --from css --to ir -i <input> -o <outDir>
Pipeline: Parse input JSON → normalize to IR → write <outDir>/tmpOutput.json.

The platform code generators (compose/swiftui/css writers) were removed:
they were dead stubs (SwiftUI/CSS exited 0 without output; Compose emitted
non-compiling code covering ~20/550 properties). The three runtime renderers
under runtimes/ consume the IR artifact (tmpOutput.json) directly, so `ir`
is the only supported target until real writers exist.

The INPUT side had the same hole until the retrospective (finding A6#2):
`--from compose` / `--from swiftui` routed to placeholder readers that
printed "not yet implemented" and exited 0 without writing anything — a
silent success to every calling script. Both stubs are deleted; `css` is
the only reader, and anything else is a usage error (exit 2) below.
*/

/**
 * Parses command line arguments into a key-value map
 * Supports both long (--flag) and short (-f) option formats
 * Flags without values are set to "true", flags with values store the next argument
 * @param args List of command line arguments
 * @return Map of flag names to their values
 */
private fun parseArgs(args: List<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            // Long form flag (--flag or --flag value)
            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                map[a] = args[i + 1]; i += 2
            } else {
                map[a] = "true"; i += 1
            }
        } else if (a.startsWith("-")) {
            // Short form flag (-f or -f value)
            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                map[a] = args[i + 1]; i += 2
            } else {
                map[a] = "true"; i += 1
            }
        } else {
            // Skip positional arguments
            i += 1
        }
    }
    return map
}

// Shared pretty-printing Json instance — building a Json format per call is
// needlessly slow (flagged by the Kotlin serialization compiler inspection).
private val prettyJson = Json { prettyPrint = true }

private fun printUsage() {
    println("Usage: style-converter convert --from css --to ir -i <input> -o <outDir> [--emit-ir v1|v2]")
    println("  --emit-ir v2   (default) flat-list IR v2 wire: irVersion/minReaderVersion envelope,")
    println("                 flat components + slot refs, text/pseudos/meta field names")
    println("  --emit-ir v1   DEPRECATED legacy nested-children wire, byte-identical to the pre-v2")
    println("                 converter; kept for exactly one deprecation window, then removed")
}

/**
 * Prints usage and terminates with a non-zero exit code.
 * Invalid invocations must never look like success to calling scripts.
 */
private fun usageError(message: String): Nothing {
    System.err.println("[style-converter] error: $message")
    printUsage()
    exitProcess(2)
}

/**
 * Main entry point for the style-converter CLI application.
 * Pipeline: parse input JSON → normalize to IR → write IR artifact.
 *
 * Supported target: `ir` — writes <outDir>/tmpOutput.json (the IR artifact
 * consumed by the Android/iOS/Web runtime renderers under runtimes/).
 *
 * Targets `compose`, `swiftui`, `css` are recognised but not implemented:
 * they fail fast with a clear error and a non-zero exit code instead of
 * silently succeeding with no output (the old generators did exactly that).
 *
 * @param rawArgs Command line arguments array
 */
fun main(rawArgs: Array<String>) {
    val args = parseArgs(rawArgs.toList())
    val cmd = rawArgs.firstOrNull()

    if (cmd != "convert") {
        // Unknown or missing command: fail loudly, never exit 0 with nothing.
        usageError("unknown command '${cmd ?: ""}' — expected 'convert'")
    }

    // Extract required command line arguments; missing args are hard errors.
    val inputPath = args["--input"] ?: args["-i"] ?: usageError("missing --input/-i")
    val fromRaw = args["--from"]?.lowercase() ?: usageError("missing --from")
    val targetsRaw = args["--to"] ?: usageError("missing --to")
    val outDir = args["--outDir"] ?: args["-o"] ?: "out"

    // Parse target platforms (comma or space separated)
    val targets = targetsRaw.split(Regex("[\\s,]+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (targets.isEmpty()) usageError("--to requires at least one target")

    // Fail fast on unimplemented / unknown targets BEFORE doing any work,
    // so scripts wired to old targets break loudly instead of silently.
    val unimplemented = setOf("compose", "swiftui", "css")
    for (target in targets) {
        when (target) {
            "ir" -> Unit // supported
            in unimplemented -> {
                System.err.println("[style-converter] error: '--to $target' writer not implemented yet — use --to ir")
                exitProcess(1)
            }
            else -> usageError("unknown target '$target'")
        }
    }

    // Route to the correct parser based on --from. CSS is the ONLY reader:
    // the `compose`/`swiftui` entries this set used to carry pointed at stub
    // readers that exited 0 with no artifact (retrospective A6#2 — the
    // input-side twin of the silent-success generators removed above), so an
    // unknown source is a usage error that exits 2 BEFORE any file is read.
    val allowedFrom = setOf("css")
    if (fromRaw !in allowedFrom) usageError("unknown --from '$fromRaw' — only 'css' has a reader")

    // IR wire version selector. v2 (flat list + slot) is the DEFAULT as of
    // the v2 freeze; `--emit-ir v1` keeps the legacy nested wire available
    // for exactly one deprecation window (see schema/spec/05-versioning.md),
    // after which the flag and the nested serializer branches are deleted.
    val emitIr = (args["--emit-ir"] ?: "v2").lowercase()
    if (emitIr !in setOf("v1", "v2")) usageError("unknown --emit-ir '$emitIr' — expected v1 or v2")

    // Parse input JSON and convert to intermediate representation
    val json = Json { ignoreUnknownKeys = true }
    val root = json.parseToJsonElement(File(inputPath).readText()).jsonObject
    val ir = parsing(root, fromRaw)

    // Serialize the IRDocument to the selected wire version.
    val pretty = if (emitIr == "v1") {
        // DEPRECATED legacy path: nested children, underscore metadata
        // names, no version envelope. Must stay byte-identical to the
        // pre-v2 converter for the deprecation window — do not touch.
        System.err.println("[style-converter] warning: --emit-ir v1 is deprecated and will be removed after one release window — migrate readers to IR v2")
        prettyJson.encodeToString(ir)
    } else {
        // v2 default: pre-order flatten (stamps slot.parent, verifies id
        // uniqueness — duplicate ids are a convert error), then the
        // versioned flat envelope (IRWireV2).
        prettyJson.encodeToString(app.irmodels.IRWireV2.encodeDocument(app.parsing.IRFlattener.flatten(ir)))
    }

    // Ensure output directory exists and write IR to tmpOutput.json.
    // NOTE: the filename tmpOutput.json is load-bearing — the Android, iOS
    // and Web runtime loaders all read this exact name. Renaming it is a
    // separate coordinated change across all three platforms.
    File(outDir).mkdirs()
    val outFile = File(outDir, "tmpOutput.json")
    outFile.writeText(pretty)
    println("[style-converter] Wrote ${outFile.path}")
}
