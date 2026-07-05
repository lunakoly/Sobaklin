package org.turbobrains.sobaklin.cli

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import org.turbobrains.sobaklin.cli.util.toParagraph
import java.io.File

class KotlincOutputDeterminer(llmModel: LLModel) {
    val outputPaths = mutableListOf<String>()

    @Tool
    @LLMDescription("Call this to mark a file or a directory as the output path.")
    fun submitOutputPath(path: String): String {
        outputPaths.add(path)
        return "Submitted."
    }

    @Tool
    @LLMDescription("Returns the current working directory.")
    fun currentWorkingDirectory(): String = System.getProperty("user.dir")

    val agent = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(OllamaClient()),
        llmModel = llmModel,
        temperature = 0.0,
        systemPrompt = """
            You are an expert on `kotlinc` input-output.
            Given the CLI arguments, you must determine what the output path
            is supposed to be and pass it to `submitOutputPath`
        """.trimIndent(),
        toolRegistry = ToolRegistry {
            tool(::submitOutputPath)
            tool(::currentWorkingDirectory)
        },
    )

    val logger = LoggerFactory.getLogger(KotlincOutputDeterminer::class.java).toParagraph("$ ")

    suspend fun determineOutputs(args: Array<String>): List<File> {
        agent.run("Input arguments: " + args.contentToString()).also(logger::info)
        return outputPaths.map { File(it).normalize() }
    }
}

