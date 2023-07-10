package com.executor

import com.executor.dto.TestCaseResultDTO
import com.executor.interpreter.WebSocketContextProvider
import com.executor.service.SnippetExecutorService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import printscript.language.interpreter.memory.MapMemory
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SpringBootApplication
class ExecutorApplication

fun main(args: Array<String>) {
    runApplication<ExecutorApplication>(*args)
}

@RestController
class AuthorizationController(private val executorService: SnippetExecutorService) {
    @PostMapping("/execute-test/{id}")
    fun executeTest(@PathVariable("id") id: UUID, @RequestHeader("Authorization") authorizationHeader: String): TestCaseResultDTO {
        return executorService.executeTest(id, authorizationHeader.substring(7))
    }
}

@Configuration
@EnableWebSocket
class WSConfig(@Autowired val executorHandler: ExecutorHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(executorHandler, "/execute")
    }
}

@Component
class ExecutorHandler(@Autowired val snippetExecutorService: SnippetExecutorService): TextWebSocketHandler() {
    private var contextProvider: WebSocketContextProvider = WebSocketContextProvider()
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        contextProvider.handleWebSocketMessage(message)
    }


    override fun afterConnectionEstablished(session: WebSocketSession) {
        contextProvider = WebSocketContextProvider(MapMemory(mutableMapOf()),session)
        val snippetId = session.uri?.query?.split("=")?.get(1)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Snippet Id Not Provided")
        val headers = session.handshakeHeaders
        val accessToken = headers.getFirst("Authorization")?.substring(7)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No Authorization Provided")
        val code: String;
        try {
            code = snippetExecutorService.getSnippet(snippetId, accessToken)
        }
        catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet provided not found")
        }
        executorService.execute {
            try {
                snippetExecutorService.execute(code, contextProvider)
            }
            catch (e: Exception) {
                contextProvider.emit(e.message.toString())
            }
            // disconnect after execution
            session.close()
        }
    }
}