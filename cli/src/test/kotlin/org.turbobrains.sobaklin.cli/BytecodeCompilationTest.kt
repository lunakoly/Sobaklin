package org.turbobrains.sobaklin.cli

import org.turbobrains.sobaklin.cli.compilation.BytecodeExample
import org.turbobrains.sobaklin.cli.compilation.BytecodeExamples
import org.turbobrains.sobaklin.cli.compilation.compileInvokeAndVerify
import org.turbobrains.sobaklin.cli.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class BytecodeCompilationTest {
    fun compileAndRunBytecode(source: String): String {
        val fsManagement = FileSystemManagement(emptyList())
        val classFilePath = fsManagement.temporaryDirectory.resolve("MainKt.class")
        val generationResult = compileInvokeAndVerify(source, "MainKt")

        when (generationResult) {
            is Outcome.Error -> fail(generationResult.throwable.message)
            is Outcome.Success -> {}
        }

        classFilePath.writeBytes(generationResult.value)
        return packageAndRunMainKt(fsManagement)
    }

    fun testBytecodeExample(example: BytecodeExample) {
        assertEquals(
            example.expectedOutput,
            compileAndRunBytecode(example.bytecodeGenerator),
        )
    }

    @Test
    fun testHelloWorld() = testBytecodeExample(BytecodeExamples.HELLO_WORLD)

    @Test
    fun testPrimitiveArrays() = testBytecodeExample(BytecodeExamples.PRIMITIVE_ARRAYS)

    @Test
    fun testBoxedArrays() = testBytecodeExample(BytecodeExamples.BOXED_ARRAYS)

    @Test
    fun testLists() = testBytecodeExample(BytecodeExamples.LISTS)

    @Test
    fun testCollectionLiterals() = testBytecodeExample(TestBytecodeExamples.COLLECTION_LITERALS)

    @Test
    fun testTypeClasses() = testBytecodeExample(TestBytecodeExamples.TYPE_CLASSES)

    @Test
    fun testOverloadResolutionByUseSiteContext() =
        testBytecodeExample(TestBytecodeExamples.OVERLOAD_RESOLUTION_BY_USE_SITE_CONTEXT)

    @Test
    fun testInlinePrompts() = testBytecodeExample(TestBytecodeExamples.INLINE_PROMPTS)
}
