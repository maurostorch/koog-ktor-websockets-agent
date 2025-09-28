package com.example.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import com.example.ollamaModel
import kotlin.math.abs

fun calculatorAgent(): AIAgent<String, String> {
    val toolRegistry = ToolRegistry {
        tools(CalculatorTools())
    }
    val agentConfig = AIAgentConfig(
        prompt = prompt("calculator") {
            system("You are a calculator. Always use the provided tools for arithmetic.")
        },
        model = ollamaModel,
        maxAgentIterations = 50
    )
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        strategy = CalculatorStrategy.strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onToolCall { e ->
                println("Tool called: ${e.tool.name}, args=${e.toolArgs}")
            }
            onAgentRunError { e ->
                println("Agent error: ${e.throwable.message}")
            }
            onAgentFinished { e ->
                println("Final result: ${e.result}")
            }
        }
    }
    return agent
}

object CalculatorStrategy {
    private const val MAX_TOKENS_THRESHOLD = 1000

    val strategy = strategy<String, String>("test") {
        val callLLM by nodeLLMRequestMultiple()
        val executeTools by nodeExecuteMultipleTools(parallelTools = true)
        val sendToolResults by nodeLLMSendMultipleToolResults()
        val compressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>()

        edge(nodeStart forwardTo callLLM)

        // If the assistant produced a final answer, finish.
        edge((callLLM forwardTo nodeFinish) transformed { it.first() } onAssistantMessage { true })

        // Otherwise, run the tools LLM requested (possibly several in parallel).
        edge((callLLM forwardTo executeTools) onMultipleToolCalls { true })

        // If we’re getting large, compress past tool results before continuing.
        edge(
            (executeTools forwardTo compressHistory)
                    onCondition { llm.readSession { prompt.latestTokenUsage > MAX_TOKENS_THRESHOLD } }
        )
        edge(compressHistory forwardTo sendToolResults)

        // Normal path: send tool results back to the LLM.
        edge(
            (executeTools forwardTo sendToolResults)
                    onCondition { llm.readSession { prompt.latestTokenUsage <= MAX_TOKENS_THRESHOLD } }
        )

        // LLM might request more tools after seeing results.
        edge((sendToolResults forwardTo executeTools) onMultipleToolCalls { true })

        // Or it can produce the final answer.
        edge((sendToolResults forwardTo nodeFinish) transformed { it.first() } onAssistantMessage { true })
    }
}

// Format helper: integers render cleanly, decimals keep reasonable precision.
private fun Double.pretty(): String =
    if (abs(this % 1.0) < 1e-9) this.toLong().toString() else "%.10g".format(this)

@LLMDescription("Tools for basic calculator operations")
class CalculatorTools : ToolSet {

    @Tool
    @LLMDescription("Adds two numbers and returns the sum as text.")
    fun plus(
        @LLMDescription("First addend.") a: Double,
        @LLMDescription("Second addend.") b: Double
    ): String = (a + b).pretty()

    @Tool
    @LLMDescription("Subtracts the second number from the first and returns the difference as text.")
    fun minus(
        @LLMDescription("Minuend.") a: Double,
        @LLMDescription("Subtrahend.") b: Double
    ): String = (a - b).pretty()

    @Tool
    @LLMDescription("Multiplies two numbers and returns the product as text.")
    fun multiply(
        @LLMDescription("First factor.") a: Double,
        @LLMDescription("Second factor.") b: Double
    ): String = (a * b).pretty()

    @Tool
    @LLMDescription("Divides the first number by the second and returns the quotient as text. Returns an error message on division by zero.")
    fun divide(
        @LLMDescription("Dividend.") a: Double,
        @LLMDescription("Divisor (must not be zero).") b: Double
    ): String = if (abs(b) < 1e-12) {
        "ERROR: Division by zero"
    } else {
        (a / b).pretty()
    }
}