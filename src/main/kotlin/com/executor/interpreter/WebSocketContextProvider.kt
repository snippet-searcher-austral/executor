package com.executor.interpreter

import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import printscript.language.interpreter.contextProvider.ContextProvider
import printscript.language.interpreter.memory.MapMemory
import printscript.language.interpreter.memory.Memory
import java.util.concurrent.*

class WebSocketContextProvider(
    private var memory: Memory = MapMemory(mutableMapOf()),
    private var session: WebSocketSession? = null,
    private var inputFuture : CompletableFuture<String> = CompletableFuture(),

) : ContextProvider {

    override fun emit(text: String) {
        session?.sendMessage(TextMessage(text))
    }

    override fun read(default: String): String {
        val input = inputFuture.join()
        inputFuture = CompletableFuture()
        return input;
    }



    override fun getMemory(): Memory = memory

    override fun setMemory(memory: Memory) {
        this.memory = memory
    }

    @Synchronized
    fun handleWebSocketMessage(message: TextMessage) {
        inputFuture.complete(message.payload)
    }
}
