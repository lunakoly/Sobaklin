package org.turbobrains.sobaklin.cli

import org.turbobrains.sobaklin.cli.compilation.BytecodeExample
import org.turbobrains.sobaklin.cli.compilation.BytecodeExamples
import org.turbobrains.sobaklin.cli.compilation.CompilerAgent
import org.turbobrains.sobaklin.cli.util.prepareLLM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilationAgentTest {
    fun compileAndRun(source: String): String {
        val fsManagement = FileSystemManagement(emptyList())
        val agent = CompilerAgent(prepareLLM(), fsManagement, diagnosticsDeterminer = null)
        val classFilePath = fsManagement.temporaryDirectory.resolve("MainKt.class")

        assertTrue {
            agent.generateClassFile(
                kotlinSourceCode = source,
                className = "MainKt",
                classFilePath = classFilePath.absolutePath,
            ).startsWith("Written")
        }

        return packageAndRunMainKt(fsManagement)
    }

    fun testKotlinExample(example: BytecodeExample) {
        assertEquals(
            example.expectedOutput,
            compileAndRun(example.kotlinSourceCode),
        )
    }

    @Test
    fun testHelloWorld() = testKotlinExample(BytecodeExamples.HELLO_WORLD)

    @Test
    fun testCollectionLiterals() = testKotlinExample(TestBytecodeExamples.COLLECTION_LITERALS)

    @Test
    fun testTypeClasses() = testKotlinExample(TestBytecodeExamples.TYPE_CLASSES)

    @Test
    fun testOverloadResolutionByUseSiteContext() =
        testKotlinExample(TestBytecodeExamples.OVERLOAD_RESOLUTION_BY_USE_SITE_CONTEXT)

    @Test
    fun testInlinePrompts() = testKotlinExample(TestBytecodeExamples.INLINE_PROMPTS)
}
