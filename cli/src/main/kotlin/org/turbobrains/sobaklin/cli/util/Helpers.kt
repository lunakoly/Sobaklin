package org.turbobrains.sobaklin.cli.util

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

fun String.prependLinesWith(prefix: String): String =
    lines().joinToString("\n") { "$prefix$it" }

@Suppress("unused")
fun prepareQwen25() = LLModel(
    provider = LLMProvider.Ollama,
    id = "qwen2.5:7b",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Tools
    ),
    contextLength = 32_768,
)

@Suppress("unused")
fun prepareQwen3() = LLModel(
    provider = LLMProvider.Ollama,
    id = "qwen3-coder:30b",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Tools
    ),
    contextLength = 262_144,
)

fun prepareLLM() = prepareQwen3()
