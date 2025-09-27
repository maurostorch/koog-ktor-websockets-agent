package com.example

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds

val ollamaModel = LLModel(
    provider = LLMProvider.Ollama,
    id = "gemma3:4b",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Basic
    ),
    contextLength = 1024 * 5
)

fun Application.configureFrameworks() {
    install(Koog) {
        llm {
//            openAI(apiKey = "your-openai-api-key")
//            anthropic(apiKey = "your-anthropic-api-key")
            ollama { baseUrl = "http://localhost:11434" }
//            google(apiKey = "your-google-api-key")
//            openRouter(apiKey = "your-openrouter-api-key")
//            deepSeek(apiKey = "your-deepseek-api-key")
        }
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/agent/base") {
            for (frame in incoming) {
                println("Received frame: $frame")
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    AIAgent(
                        executor = SingleLLMPromptExecutor(OllamaClient()),
                        llmModel = ollamaModel,
                        systemPrompt = """
                        You are a highly intelligent question answering bot.
                        You will be provided a question and you will answer it as best as possible. 
                        If you don't know the answer, just say that you don't know. 
                        Do not try to make up an answer.
                    """.trimIndent(),
                        strategy = singleRunStrategy()
                    ) {
                        eventHandler { feedback ->
                            feedback.message?.let { outgoing.send(Frame.Text(it)) }
                            feedback.state.takeIf { s -> s == State.COMPLETED }?.let {
                                outgoing.send(Frame.Text("__END__"))
//                                close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                            }
                        }
                    }.run(message.trim())
                }
            }

        }
        route("/ai") {
            post("/chat") {
                val userInput = call.receive<String>()
                val output = aiAgent(userInput, model = OpenAIModels.Chat.GPT4_1)
                call.respondText(output)
            }
        }
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
}

fun AIAgent.FeatureContext.eventHandler(handler: suspend (Feedback) -> Unit) {
    install(EventHandler) {
        onBeforeLLMCall {
            // list of random messages to indicate processing
            val processingMessages = listOf(
                "Processing...",
                "Thinking...",
                "Analyzing...",
                "Working on it...",
                "Let me see...",
                "Just a moment...",
            )
            handler(Feedback(State.PROCESSING, processingMessages.random()))
        }
        onAgentFinished {
            println("Agent finished")
            handler(Feedback(State.COMPLETED, it.result.toString()))
        }
    }
}

enum class State {
    INITIAL, PROCESSING, COMPLETED, ERROR
}
data class Feedback(val state: State, val message: String?)
