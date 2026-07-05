package org.turbobrains.sobaklin.cli

import kotlinx.coroutines.runBlocking
import org.turbobrains.sobaklin.cli.compilation.BytecodeExample
import org.turbobrains.sobaklin.cli.compilation.BytecodeExamples
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainAgentTest {
    fun runSingleFileJar(source: String): String {
        val sourcesDirectory = createTempDirectory().toFile()
        val mainFilePath = sourcesDirectory.resolve("Main.kt")
        val jarPath = sourcesDirectory.resolve("main.jar")

        mainFilePath.writeText(source)
        val outputStream = ByteArrayOutputStream()

        runBlocking {
            start(
                args = arrayOf(mainFilePath.absolutePath, "-include_runtime", "-d", jarPath.absolutePath),
                output = PrintStream(outputStream),
            )
        }

        val output = outputStream.toString()

        return when {
            output.isEmpty() -> runAndGetOutput(jarPath.absolutePath)
            else -> output
        }
    }

    // Drop the class name helper to make sure the agent properly passes the name.
    private val String.withoutClassNameDirective: String
        get() = replace("""^// class name: .*\n""".toRegex(RegexOption.MULTILINE), "")

    fun testBytecodeExample(bytecodeExample: BytecodeExample) {
        val source = bytecodeExample.kotlinSourceCode.withoutClassNameDirective
        assertEquals(bytecodeExample.expectedOutput, runSingleFileJar(source))
    }

    @Test
    fun testHelloWorld() = testBytecodeExample(BytecodeExamples.HELLO_WORLD)

    @Test
    fun testTypeClasses() = testBytecodeExample(TestBytecodeExamples.TYPE_CLASSES)

    private val diagnosticPattern = """(.*): \[(.*)] (.*)""".toRegex()

    @Test
    fun testProhibitHelloWorld() {
        val source = TestCodeExamples.PROHIBITED_HELLO_WORLD.kotlinSourceCode.withoutClassNameDirective
        val output = runSingleFileJar(source)
        val matches = diagnosticPattern.findAll(output).toList()

        assertTrue { matches.isNotEmpty() }
        assertTrue { "PROHIBIT" in matches.first().groupValues[2] }
    }
}