package com.executor

import com.executor.interpreter.WebSocketContextProvider
import org.json.JSONObject
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
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
class WSConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(ExecutorHandler(), "/execute")
    }
}


class ExecutorHandler: TextWebSocketHandler() {
    private var contextProvider: WebSocketContextProvider = WebSocketContextProvider()
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()


    private fun execute(code: String) {
        val astIterator = getAstIterator(code, "1.1")
        val interpreter = InterpreterWithIterator(InterpreterImpl(contextProvider), astIterator)
        try {
            while (interpreter.hasNextInterpretation()) {
                interpreter.interpretNextAST()
            }
        }
        catch (e: Exception) {
            println(e.message)
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
        println(message.payload)
        contextProvider.handleWebSocketMessage(message)
    }


    override fun afterConnectionEstablished(session: WebSocketSession) {
        contextProvider = WebSocketContextProvider(MapMemory(mutableMapOf()),session)
        val snippetId = session.uri?.query?.split("=")?.get(1)
        println(snippetId)
        if (snippetId == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Snippet Id Not Provided")
        val code: String;
        try {
            code = getSnippet(snippetId)
        }
        catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet provided not found")
        }
        println(code)
        executorService.execute {
            execute(code)
        }
    }

    // TODO: Authenticate request to snippet manager and check if user has permission in manager.
    private fun getSnippet(snippetId: String): String {
        val url = URL("https://snippet-searcher.southafricanorth.cloudapp.azure.com/snippet-manager/snippet/$snippetId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val jsonResponse = JSONObject(response)
        return jsonResponse.getString("content");
    }
}