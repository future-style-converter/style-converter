package app

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Retrospective finding A6#2 — the CLI must never exit 0 without writing an
 * artifact.
 *
 * Main.kt has said since the CLEANUP reboot that "invalid invocations must
 * never look like success to calling scripts", and enforced it on the OUTPUT
 * side (`--to compose` exits 1). The INPUT side kept the identical hole:
 * `--from compose` / `--from swiftui` reached placeholder readers that printed
 * "… not yet implemented" and `exitProcess(0)` — executed evidence: gradle
 * exit 0, no output directory created. Those stubs are deleted and `css` is
 * the only `--from` Main accepts; this suite pins the contract by running the
 * REAL entry point in a child JVM, because `main` terminates the process via
 * exitProcess and cannot be exercised in-process.
 *
 * Proven able to fail: re-adding `"compose"` to Main.kt's `allowedFrom` and
 * an `exitProcess(0)` branch to parsing.kt makes `--from compose is a usage
 * error` fail on the exit code (0 ≠ 2) — the exact defect, reproduced.
 */
class MainCliTest {

    /** Exit code + captured stderr of one CLI invocation against the compiled classes. */
    private data class Run(val exit: Int, val stderr: String, val stdout: String)

    private fun runCli(workDir: File, vararg args: String): Run {
        // The Gradle test worker's own JVM and classpath already contain the
        // compiled main classes plus kotlinx-serialization — reuse both so the
        // child process runs exactly the code under test, nothing rebuilt.
        val java = ProcessHandle.current().info().command()
            .orElse(File(System.getProperty("java.home"), "bin/java").path)
        val classpath = System.getProperty("java.class.path")
        val out = File(workDir, "stdout.txt")
        val err = File(workDir, "stderr.txt")
        val process = ProcessBuilder(listOf(java, "-cp", classpath, "app.MainKt") + args)
            .directory(workDir)
            .redirectOutput(out)   // files, not pipes: no deadlock on a chatty convert log
            .redirectError(err)
            .start()
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "CLI did not exit within 120s")
        return Run(process.exitValue(), err.readText(), out.readText())
    }

    /** A minimal but valid CSS envelope — one component, one declaration. */
    private fun fixture(workDir: File): File = File(workDir, "in.json").apply {
        writeText("""{"components":{"A":{"properties":{"color":"red"}}}}""")
    }

    private fun tempDir(): File = Files.createTempDirectory("main-cli-test").toFile()

    // ── the finding: unknown readers are usage errors, not silent successes ──

    @Test
    fun `--from compose is a usage error with no artifact`() {
        val dir = tempDir()
        val run = runCli(dir, "convert", "--from", "compose", "--to", "ir", "-i", fixture(dir).path, "-o", "out-compose")
        assertEquals(2, run.exit, "usageError exits 2; the old stub exited 0. stderr=${run.stderr}")
        assertTrue(run.stderr.contains("unknown --from 'compose'"), "stderr names the rejected reader: ${run.stderr}")
        assertFalse(File(dir, "out-compose").exists(), "no output directory may be created for a rejected reader")
    }

    @Test
    fun `--from swiftui is a usage error with no artifact`() {
        val dir = tempDir()
        val run = runCli(dir, "convert", "--from", "swiftui", "--to", "ir", "-i", fixture(dir).path, "-o", "out-swiftui")
        assertEquals(2, run.exit, "usageError exits 2; the old stub exited 0. stderr=${run.stderr}")
        assertTrue(run.stderr.contains("unknown --from 'swiftui'"), "stderr names the rejected reader: ${run.stderr}")
        assertFalse(File(dir, "out-swiftui").exists(), "no output directory may be created for a rejected reader")
    }

    @Test
    fun `--from is matched case-insensitively so COMPOSE is rejected too`() {
        // Main lowercases `--from` before the check; a spelling that used to
        // route to the stub must not slip through on case.
        val dir = tempDir()
        val run = runCli(dir, "convert", "--from", "COMPOSE", "--to", "ir", "-i", fixture(dir).path, "-o", "out-upper")
        assertEquals(2, run.exit, "stderr=${run.stderr}")
        assertFalse(File(dir, "out-upper").exists())
    }

    @Test
    fun `usage text advertises only the css reader`() {
        // The usage line used to read `--from css|compose|swiftui`, advertising
        // the two silent stubs; a rejected invocation prints usage, so read it there.
        val dir = tempDir()
        val run = runCli(dir, "convert", "--from", "compose", "--to", "ir", "-i", fixture(dir).path)
        assertTrue(run.stdout.contains("--from css --to ir"), "usage line: ${run.stdout}")
        assertFalse(run.stdout.contains("compose|swiftui"), "usage must not advertise deleted readers: ${run.stdout}")
    }

    // ── the twins that must keep working / keep failing loudly ──────────────

    @Test
    fun `--from css --to ir writes tmpOutput json and exits 0`() {
        val dir = tempDir()
        val run = runCli(dir, "convert", "--from", "css", "--to", "ir", "-i", fixture(dir).path, "-o", "out-css")
        assertEquals(0, run.exit, "stderr=${run.stderr}")
        val artifact = File(dir, "out-css/tmpOutput.json")
        assertTrue(artifact.exists(), "the IR artifact must be written")
        // The default wire is IR v2 (schema/spec/05-versioning.md).
        val root = Json.parseToJsonElement(artifact.readText()).jsonObject
        assertEquals("2", root["irVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun `--to compose still fails fast with exit 1 and no artifact`() {
        // The output-side contract the reboot established; pinned beside its
        // input-side twin so neither can regress alone.
        val dir = tempDir()
        val run = runCli(dir, "convert", "--from", "css", "--to", "compose", "-i", fixture(dir).path, "-o", "out-writer")
        assertEquals(1, run.exit, "stderr=${run.stderr}")
        assertTrue(run.stderr.contains("writer not implemented"), run.stderr)
        assertFalse(File(dir, "out-writer").exists())
    }
}
