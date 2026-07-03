package org.turbobrains.sobaklin.cli

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.rag.base.files.JVMFileSystemProvider

var finalResults: String = ""

@Tool
@LLMDescription("Presents the requested results to the user")
fun presentResults(results: String): String {
    finalResults = results
    return "Results presented."
}

fun String.prependLinesWith(prefix: String): String =
    lines().joinToString("\n") { "$prefix$it" }

suspend fun main(args: Array<String>) {
    val agent = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(OllamaClient()),
        llmModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwen2.5:7b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 32_768,
        ),
        temperature = 0.1,
        systemPrompt = """
            Imagine you are `kotlinc`, the Kotlin compiler CLI tool, and you are to be run with its CLI arguments.

            If there are any input source files, print only the contents of those files
            concatenating them together with prefixes like `// FILE: SomeFile1.kt\n`.
            Do not hallucinate any hypothetical input.

            Present your results using the dedicated tool, `__present_results__`.
        """.trimIndent(),
        toolRegistry = ToolRegistry {
            tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            tool(::presentResults)
        },
    )

    val output = agent.run("'Execute' these arguments: " + args.contentToString())
    println(output.prependLinesWith("> "))

    println(finalResults.trim().prependLinesWith("|| "))
}
