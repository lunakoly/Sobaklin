package org.turbobrains.sobaklin.cli

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool

class FinishTool {
    var finished = false

    @Tool
    @LLMDescription("Call this tool when you've finished.")
    fun finish(): String {
        finished = true
        return "Finished."
    }
}
