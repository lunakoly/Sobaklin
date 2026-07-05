package org.turbobrains.sobaklin.cli

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.AIAgentTool
import ai.koog.agents.core.agent.createAgentTool
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken

fun prepareKotlincExpertTool(llmModel: LLModel): Tool<AIAgentTool.AgentToolInput<String>, AIAgentTool.AgentToolResult<String>> {
    val kotlincExpertService = AIAgentService(
        promptExecutor = MultiLLMPromptExecutor(OllamaClient()),
        llmModel = llmModel,
        temperature = 0.0,
        systemPrompt = """
            You are an expert on `kotlinc` input-output.
            Given the CLI arguments, you must describe what the final result
            should look like/be structured as/contain necessarily.
            Imagine if `kotlinc` itself "forgot" which files it needs to produce,
            and you must "remind it".

            If an executable JAR is desired, the resulting JAR must look like:
            -- resulting.jar
               \-- META-INF/MANIFEST.MF (with Main-Class pointing to one)
               \-- packages/with/classfiles
               \-- kotlin (the Kotlin runtime)
        """.trimIndent(),
    ) {
        install(ChatMemory)
    }

    return kotlincExpertService.createAgentTool(
        agentName = "kotlincResultsExpert",
        agentDescription = "Clarifies what exactly `kotlinc` is supposed to produce.",
        inputDescription = "CLI arguments for `kotlinc`.",
        inputType = typeToken<String>(),
    )
}
