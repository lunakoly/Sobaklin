package org.turbobrains.sobaklin.cli

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.exception.AIAgentStuckInTheNodeException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import org.slf4j.LoggerFactory
import org.turbobrains.sobaklin.cli.compilation.CompilerAgent
import org.turbobrains.sobaklin.cli.compilation.DiagnosticDeterminer
import org.turbobrains.sobaklin.cli.util.Outcome
import org.turbobrains.sobaklin.cli.util.prepareLLM
import org.turbobrains.sobaklin.cli.util.toParagraph
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit

@Tool
@LLMDescription("Inserts the Kotlin runtime (`kotlin` folder) into the directory denoted by `path`.")
fun addKotlinRuntimeTo(path: String): String {
    File(path).mkdirs()
    val runtimePath = System.getProperty("sobaklin.kotlin.runtime")
        ?: return "Error: Kotlin runtime cannot be found, proceed without it."
    val process = ProcessBuilder("cp", "-r", runtimePath, path)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    process.waitFor(10, TimeUnit.MINUTES)
    return when {
        process.exitValue() == 0 -> "Finished: $path/kotlin. Output: \n" + process.inputStream.bufferedReader().readText()
        else -> "Error: exit code ${process.exitValue()}, ${process.errorStream.bufferedReader().readText()}"
    }
}

suspend fun main(args: Array<String>) {
    // Logging can be brought back anytime via:
    // `java -Dorg.slf4j.simpleLogger.defaultLogLevel=info -jar sobaklin.jar ...`
    KotlinLoggingConfiguration.logStartupMessage = false

    start(args, System.out)
}

suspend fun start(args: Array<String>, output: PrintStream) {
    val llmModel = prepareLLM()
    val fsManagement = FileSystemManagement(KotlincOutputDeterminer(llmModel).determineOutputs(args))
    val finishTool = FinishTool()
    val diagnosticsDeterminer = DiagnosticDeterminer(llmModel)
    val compilerAgent = CompilerAgent(llmModel, fsManagement, diagnosticsDeterminer)

    val fileSystemTools = ToolRegistry {
        tool(fsManagement::prepareTemporaryDirectory)
        tool(fsManagement::listDirectory)
        tool(fsManagement::createDirectory)
        tool(fsManagement::readFileAsText)
        tool(fsManagement::createTextFile)
        tool(fsManagement::move)
        tool(fsManagement::checkPath)
        tool(fsManagement::createJar)
    }
    val agentLifetimeTools = ToolRegistry {
        tool(finishTool::finish)
    }
    val kotlinGenerationTools = ToolRegistry {
        tool(prepareKotlincExpertTool(llmModel))
        tool(compilerAgent::generateClassFile)
        tool(::addKotlinRuntimeTo)
    }

    val mainAgent = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(OllamaClient()),
        llmModel = llmModel,
        temperature = 0.0,
        systemPrompt = """
            You are a drop-in replacement for `kotlinc`. Given the same CLI arguments, produce the same output.

            Produce the result prescribed by `kotlincResultsExpert`.
            When creating files, make sure you create them in the right places.
            Intermediate files may only go somewhere inside `prepareTemporaryDirectory`.

            Read sources with `readFileAsText`, and compile them to class files using `generateClassFile`.
            If the compilation results in "COMPILATION ERROR", stop further work.

            Before calling `createJar`, you must always generate the manifest manually.
            The manifest must always end with an empty line.

            Call the `finish` tool once you've finished.
        """.trimIndent(),
        toolRegistry = kotlinGenerationTools + agentLifetimeTools + fileSystemTools,
    ) {
        install(ChatMemory)
    }

    val logger = LoggerFactory.getLogger("MainAgent").toParagraph("> ")

    suspend fun launch(input: String): Outcome<String> =
        try {
            Outcome.Success(mainAgent.run(input, "main-session").also(logger::info))
        } catch (e: AIAgentStuckInTheNodeException) {
            Outcome.Error(e.also(logger::error))
        }

    var agentInput = """
        You input arguments: " + ${args.contentToString()}.
        Read the sources with `readFileAsText`.
        Call `finish` once you've finished all the work.
    """.trimIndent().trim()

    while (!finishTool.finished) {
        val result = launch(agentInput)

        if (diagnosticsDeterminer.diagnostics.isNotEmpty()) {
            output.println(diagnosticsDeterminer.diagnostics.joinToString("\n"))
        }

        val suffix = (result as? Outcome.Error)?.let { " The last call didn't finish gracefully: ${it.throwable.message}." } ?: ""
        agentInput = "Perform the task using the tools.$suffix"
    }
}
