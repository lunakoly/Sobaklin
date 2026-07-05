package org.turbobrains.sobaklin.cli.compilation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.turbobrains.sobaklin.cli.FileSystemManagement
import org.turbobrains.sobaklin.cli.util.Outcome
import org.turbobrains.sobaklin.cli.util.toParagraph
import java.io.File

class CompilerAgent(
    llmModel: LLModel,
    private val fsManagement: FileSystemManagement,
    val diagnosticsDeterminer: DiagnosticDeterminer?,
) {
    private val compilerAgent: GraphAIAgent<String, String> = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(OllamaClient()),
        llmModel = llmModel,
        temperature = 0.0,
        systemPrompt = """
        You are a creative Kotlin compiler. You receive a Kotlin source file and generate
        a BytecodeGenerator in Java constructing what you think would be
        the bytecode of that Kotlin source file using the ASM library 9.7.1.
        
        The code must define exactly:
        ```java
        // MARKER:FILE_START
        import org.objectweb.asm.*;
        import static org.objectweb.asm.Opcodes.*;
        import ...;
    
        public class BytecodeGenerator {
            public static byte[] generate() { ... }
        }
        // MARKER:FILE_END
        ```
        where `generate()` uses ASM's ClassWriter to build and return the bytecode.
        Always use ClassWriter.COMPUTE_FRAMES.
        Don't assume any class name is imported by default.
        
        You MUST perform "Dry-Run Stack Trace Analysis" of your result.
        For every single instruction, you must trace the Operand Stack array.

        # Kotlin Bytecode Generation Guide

        This guide explains common pitfalls and nuances when generating Kotlin bytecode using ASM library. The examples demonstrate how to properly handle arrays, loops, and method calls.
        
        ## Key Concepts
        
        ### 1. Class Structure
        - Always define a class with proper `visit()` call including version, access flags, name, super class, and interfaces
        - Include a constructor that calls the superclass constructor
        - Main method must have signature `([Ljava/lang/String;)V`
        
        ### 2. Array Creation and Population
        
        #### Primitive Arrays (int[])
        ```java
        // Create array of size 3
        mv.visitIntInsn(BIPUSH, 3); // Push length
        mv.visitIntInsn(NEWARRAY, T_INT); // Create int array
        mv.visitVarInsn(ASTORE, 1); // Store in local variable
        
        // Populate array elements
        mv.visitVarInsn(ALOAD, 1); // Load array reference
        mv.visitIntInsn(BIPUSH, index); // Index
        mv.visitIntInsn(BIPUSH, value); // Value
        mv.visitInsn(IASTORE); // Store at index
        ```
        
        #### Object Arrays (Integer[])
        ```java
        // Create array of size 3
        mv.visitInsn(ICONST_3);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Integer");
        
        // Populate with values
        mv.visitInsn(DUP); // Duplicate array reference
        mv.visitIntInsn(BIPUSH, index);
        mv.visitIntInsn(BIPUSH, value);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        mv.visitInsn(AASTORE); // Store at index
        ```
        
        ### 3. Loop Implementation
        
        #### For-loop with arrays:
        ```java
        // Initialize loop variables
        mv.visitInsn(ICONST_0); // index = 0
        mv.visitVarInsn(ISTORE, indexVar);
        
        // Get array length
        mv.visitVarInsn(ALOAD, arrayVar);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitVarInsn(ISTORE, lengthVar);
        
        Label loopStart = new Label();
        Label loopEnd = new Label();
        
        mv.visitLabel(loopStart);
        // Loop condition check
        mv.visitVarInsn(ILOAD, indexVar);
        mv.visitVarInsn(ILOAD, lengthVar);
        mv.visitJumpInsn(IF_ICMPGE, loopEnd);
        
        // Process element
        mv.visitVarInsn(ALOAD, arrayVar);
        mv.visitVarInsn(ILOAD, indexVar);
        mv.visitInsn(IALOAD); // Load element
        
        // Increment index
        mv.visitIincInsn(indexVar, 1);
        mv.visitJumpInsn(GOTO, loopStart);
        ```
        
        #### For-loop with collections:
        ```java
        // Get iterator
        mv.visitVarInsn(ALOAD, listVar);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
        mv.visitVarInsn(ASTORE, iterVar);
        
        Label loopStart = new Label();
        Label loopEnd = new Label();
        
        mv.visitLabel(loopStart);
        // Check hasNext()
        mv.visitVarInsn(ALOAD, iterVar);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        mv.visitJumpInsn(IFEQ, loopEnd);
        
        // Get next element
        mv.visitVarInsn(ALOAD, iterVar);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        
        // Process element
        // ... processing code ...
        
        mv.visitJumpInsn(GOTO, loopStart);
        ```
        
        ### 4. Stack Management
        
        #### Proper stack operations:
        - Always ensure correct operand stack state before each instruction
        - Use `visitMaxs()` with appropriate values (max stack size, max locals)
        - For `DUP` operations, remember that the top of stack is duplicated
        - Handle local variable assignments carefully with `ISTORE`, `ASTORE`
        
        ### 5. Method Calls
        
        #### Standard library calls:
        ```java
        // println(String)
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("Hello");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        
        // Arrays.asList()
        mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false);
        ```
        
        ### 6. Frame Computation
        
        Always use `ClassWriter.COMPUTE_FRAMES` to let ASM calculate frame information automatically:
        ```java
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ```
        
        ## Common Pitfalls
        
        1. **Incorrect stack state**: Ensure operand stack is in correct state before each instruction
        2. **Missing return statements**: Every code path must end with RETURN, GOTO, or THROW
        3. **Wrong array creation**: Use `NEWARRAY` for primitives, `ANEWARRAY` for objects
        4. **Local variable management**: Track local variables correctly and avoid conflicts
        5. **Frame computation**: Don't forget to call `visitMaxs()` with correct values
        
        ## Example Summary
        
        The examples show how to:
        - Generate Kotlin-style code using ASM
        - Handle different array types (primitive vs object)
        - Implement for-loops over arrays and collections
        - Manage method calls and stack operations properly
        - Ensure bytecode verification passes with COMPUTE_FRAMES
        
        Each example demonstrates a different Kotlin construct (intArrayOf, arrayOf, listOf) and shows how to translate them into equivalent bytecode using ASM.
    """.trimIndent(),
    )

    companion object {
        fun String.extractCodeInMarkers() = this
            .split(FILE_START_MARKER).last()
            .split(FILE_END_MARKER).first()
    }

    private val logger = LoggerFactory.getLogger(this::class.java).toParagraph("+ ")

    @Tool
    @LLMDescription("Compiles `kotlinSourceCode` of a class named `className` into a .class file, written to `classFilePath`.")
    fun generateClassFile(
        kotlinSourceCode: String,
        className: String,
        classFilePath: String
    ): String = runBlocking {
        fsManagement.requireWriteAccess(classFilePath) { return@runBlocking it }

        val shortClassName = className.split(".").last()
        val shortFileName = File(classFilePath).nameWithoutExtension

        if (shortClassName != shortFileName) {
            return@runBlocking "Error: The names in `classFilePath` and `className` must be identical but '$shortClassName' != '$shortFileName'."
        }

        val namedCode = """
            ```
            // class name: $className
            $kotlinSourceCode
            ```
        """.trimIndent()

        if (diagnosticsDeterminer != null) {
            diagnosticsDeterminer.diagnostics.clear()
            diagnosticsDeterminer.collectDiagnostics(namedCode)

            if (diagnosticsDeterminer.diagnostics.isNotEmpty()) {
                return@runBlocking "COMPILATION ERROR"
            }
        }

        var agentInput = namedCode
        var generationResult: Outcome<ByteArray>? = null

        while (generationResult !is Outcome.Success) {
            val output = compilerAgent.run(agentInput).also(logger::info)

            if (FILE_START_MARKER !in output) {
                agentInput = "Your last response had no `$FILE_START_MARKER`.\n\n$namedCode"
                continue
            }
            if (FILE_END_MARKER !in output) {
                agentInput = "Your last response had no `$FILE_END_MARKER`.\n\n$namedCode"
                continue
            }
            val extractedCode = output.extractCodeInMarkers()

            generationResult = compileInvokeAndVerify(extractedCode, className)

            if (generationResult is Outcome.Error) {
                val errorMessage = generationResult.throwable.also(logger::error).message ?: "Some error occurred."
                agentInput = """
                    $namedCode
                    Your previously suggested BytecodeGenerator didn't compile correctly:
                    ```
                    $FILE_START_MARKER
                    """.trimIndent() + extractedCode + """
                    $FILE_END_MARKER
                    ```
                    $errorMessage
                """.trimIndent()
            }
        }

        try {
            File(classFilePath).also { it.parentFile?.mkdirs() }.writeBytes(generationResult.value)
            "Written ${generationResult.value.size} bytes to $classFilePath."
        } catch (e: Exception) {
            e.message ?: e.stackTraceToString()
        }
    }
}
