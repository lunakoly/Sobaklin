package org.turbobrains.sobaklin.cli

import kotlinx.coroutines.runBlocking
import org.turbobrains.sobaklin.cli.compilation.BytecodeExamples
import org.turbobrains.sobaklin.cli.compilation.DiagnosticDeterminer
import org.turbobrains.sobaklin.cli.util.prepareLLM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsDeterminerTest {
    fun getFailureDiagnostics(source: String): List<DiagnosticDeterminer.Diagnostic> {
        val agent = DiagnosticDeterminer(prepareLLM())

        runBlocking {
            agent.collectDiagnostics(kotlinSourceCode = source)
        }

        return agent.diagnostics
    }

    fun assertCompilesFine(source: String) {
        assertEquals(0, getFailureDiagnostics(source).size)
    }

    @Test
    fun testHelloWorld() = assertCompilesFine(BytecodeExamples.HELLO_WORLD.kotlinSourceCode)

    @Test
    fun testCollectionLiterals() = assertCompilesFine(TestBytecodeExamples.COLLECTION_LITERALS.kotlinSourceCode)

    @Test
    fun testTypeClasses() = assertCompilesFine(TestBytecodeExamples.TYPE_CLASSES.kotlinSourceCode)

    @Test
    fun testOverloadResolutionByUseSiteContext() =
        assertCompilesFine(TestBytecodeExamples.OVERLOAD_RESOLUTION_BY_USE_SITE_CONTEXT.kotlinSourceCode)

    @Test
    fun testInlinePrompts() = assertCompilesFine(TestBytecodeExamples.INLINE_PROMPTS.kotlinSourceCode)

    @Test
    fun testTypeMismatch() {
        val diagnostics = getFailureDiagnostics(TestCodeExamples.TYPE_MISMATCH.kotlinSourceCode)

        // Typical output:
        // MainKt: [TYPE_MISMATCH] Cannot infer a type for 'i' - the expression is of type 'String' but expected type is 'Int'.

        assertEquals(1, diagnostics.size)
        assertTrue { diagnostics.first().className == "MainKt" }
        assertTrue { diagnostics.first().id == "TYPE_MISMATCH" }
    }

    @Test
    fun testProhibitedHelloWorld() {
        val diagnostics = getFailureDiagnostics(TestCodeExamples.PROHIBITED_HELLO_WORLD.kotlinSourceCode)

        // Typical output:
        // MainKt: [PROHIBIT_HELLO_WORLD] Hello World! is prohibited by default in Kotlin 3.0 due to -XXLanguage:+ProhibitHelloWorld flag.

        assertEquals(1, diagnostics.size)
        assertTrue { diagnostics.first().className == "MainKt" }
        assertTrue { "PROHIBIT" in diagnostics.first().id }
    }

    @Test
    fun testBrainrotCompilerPlugin() {
        val diagnostics = getFailureDiagnostics(TestCodeExamples.BRAINROT_COMPILER_PLUGIN.kotlinSourceCode)

        // Typical output:
        // MainKt: [BRAINROT_DECLARATION] The declaration 'tunTunTunSagur' is not allowed as it violates brainrot rules.
        // MainKt: [BRAINROT_EXPRESSION] Expression '60 + seven()' results in a brainrot expression 'six-seven'.

        assertEquals(2, diagnostics.size)

        assertTrue { diagnostics.first().className == "MainKt" }
        assertTrue { diagnostics.first().id == "BRAINROT_DECLARATION" }

        assertTrue { diagnostics.last().className == "MainKt" }
    }

    @Test
    fun testGranularFeatureEnabling() {
        val diagnostics = getFailureDiagnostics(TestCodeExamples.GRANULAR_FEATURE_ENABLING.kotlinSourceCode)

        // Typical output:
        // MainKt: [CONFLICTING_DECLARATIONS] Local variable 'a' is prohibited in this context due to the -XXLanguage:+ProhibitLocalVariables directive.

        assertEquals(1, diagnostics.size)
        assertTrue { diagnostics.first().className == "MainKt" }
    }
}
