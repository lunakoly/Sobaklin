package org.turbobrains.sobaklin.cli.compilation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.exception.AIAgentStuckInTheNodeException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import org.turbobrains.sobaklin.cli.util.Outcome
import org.turbobrains.sobaklin.cli.util.toParagraph

class DiagnosticDeterminer(llmModel: LLModel) {
    data class Diagnostic(
        val className: String,
        val id: String,
        val message: String,
    ) {
        override fun toString(): String = "$className: [$id] $message."
    }

    val diagnostics = mutableListOf<Diagnostic>()

    @Tool
    @LLMDescription("Never call this until the code itself expects a compilation error.")
    fun reportDiagnostic(
        @LLMDescription("The name of the class where the error occurred.")
        className: String,
        @LLMDescription("A string like UPPER_BOUND_VIOLATED or CONFLICTING_DECLARATIONS.")
        id: String,
        @LLMDescription("A user-friendly clarification.")
        message: String
    ): String {
        diagnostics += Diagnostic(className, id, message)
        return "Reported. Don't forget to reply with text when you're done."
    }

    private val diagnosticsAgent: GraphAIAgent<String, String> = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(OllamaClient()),
        llmModel = llmModel,
        temperature = 0.0,
        systemPrompt = """
        You are a creative Kotlin compiler. You receive a Kotlin source file
        and determine if there are supposed to be any compilation errors
        according to its own rules, not some standard Kotlin.

        If the code is likely fine, just reply with text.
        Otherwise, if the author likely expects the compilation to fail,
        report all failures with `reportDiagnostic` and then reply with text.
    """.trimIndent(),
        toolRegistry = ToolRegistry {
            tool(::reportDiagnostic)
        }
    )

    private val logger = LoggerFactory.getLogger(this::class.java).toParagraph("& ")

    suspend fun collectDiagnostics(kotlinSourceCode: String) {
        suspend fun launch(input: String): Outcome<String> =
            try {
                Outcome.Success(diagnosticsAgent.run(input).also(logger::info))
            } catch (e: AIAgentStuckInTheNodeException) {
                Outcome.Error(e.also(logger::error))
            }

        var agentInput = """
            First, analyze the following code.
            $kotlinSourceCode
            Then think if it violates any of its own rules.
        """.trimIndent()
        var result: Outcome<String>? = null
        diagnostics.clear()

        while (result !is Outcome.Success) {
            result = launch(agentInput)

            if (diagnostics.isNotEmpty()) {
                break
            }

            if (result is Outcome.Error) {
                val errorMessage = result.throwable.also(logger::error).message ?: "Some error occurred."
                agentInput = """
                    $kotlinSourceCode
                    Your previous reply resulted in an error:
                    $errorMessage
                """.trimIndent()
            }
        }
    }
}
