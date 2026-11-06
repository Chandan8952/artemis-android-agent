package com.artemis.agent.model

/**
 * State of the Artemis Autonomous Agent during task execution.
 */
sealed class AgentState {
    object Idle : AgentState()
    
    data class Running(
        val step: Int,
        val maxSteps: Int = 25,
        val goal: String,
        val thought: String = "Observing screen...",
        val lastAction: String = "None",
        val isShizukuActive: Boolean = false
    ) : AgentState()

    data class Paused(val step: Int, val goal: String) : AgentState()
    
    data class Error(val message: String, val step: Int) : AgentState()
    
    data class Completed(
        val summary: String,
        val totalSteps: Int,
        val success: Boolean
    ) : AgentState()
}
