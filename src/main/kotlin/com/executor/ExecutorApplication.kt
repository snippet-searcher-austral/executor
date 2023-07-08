package com.executor

import com.executor.interpreter.WebSocketContextProvider
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import printscript.language.interpreter.interpreter.InterpreterImpl
import printscript.language.interpreter.interpreter.InterpreterWithIterator
import printscript.language.lexer.LexerFactory
import printscript.language.lexer.TokenListIterator
import printscript.language.parser.ASTIterator
import printscript.language.parser.ParserFactory
import org.springframework.web.socket.handler.TextWebSocketHandler
import printscript.language.interpreter.memory.MapMemory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SpringBootApplication
class ExecutorApplication

fun main(args: Array<String>) {
    runApplication<ExecutorApplication>(*args)
}

@Configuration
@EnableWebSocket
class WSConfig(@Autowired val executorHandler: ExecutorHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(executorHandler, "/execute")
    }
}

@Component
class ExecutorHandler: TextWebSocketHandler() {
    private var contextProvider: WebSocketContextProvider = WebSocketContextProvider()
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    @Value("\${snippet-manager.url}")
    private lateinit var snippetManagerUrl: String

    private fun execute(code: String) {
        val astIterator = getAstIterator(code, "1.1")
        val interpreter = InterpreterWithIterator(InterpreterImpl(contextProvider), astIterator)
        try {
            while (interpreter.hasNextInterpretation()) {
                interpreter.interpretNextAST()
            }
        }
        catch (e: Exception) {
            contextProvider.emit(e.message.toString())
        }

    }

    private fun getAstIterator(code: String, version: String): ASTIterator {
        val inputStream = code.byteInputStream(Charsets.UTF_8)
        val lexer = LexerFactory().createLexer(version, inputStream)
        val tokenListIterator = TokenListIterator(lexer)
        val parser = ParserFactory().createParser(version)
        return ASTIterator(parser, tokenListIterator)
    }

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
            code = getSnippet(snippetId, accessToken)
        }
        catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet provided not found")
        }
        executorService.execute {
            execute(code)
            // disconnect after execution
            session.close()
        }
    }

    // TODO: Authenticate request to snippet manager and check if user has permission in manager.
    private fun getSnippet(snippetId: String, accessToken: String): String {
        val url = URL(snippetManagerUrl + snippetId)
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.requestMethod = "GET"
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val jsonResponse = JSONObject(response)
        return jsonResponse.getString("content");
    }
}