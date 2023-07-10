package com.executor.interpreter

import printscript.language.interpreter.contextProvider.ContextProvider
import printscript.language.interpreter.memory.MapMemory
import printscript.language.interpreter.memory.Memory
import java.util.*

class TestContextProvider(
    private var inputs: Queue<String> = LinkedList(),
    private var outputs: List<String> = listOf(),
    private var memory: Memory = MapMemory(mutableMapOf()),
    ): ContextProvider {

    override fun getMemory(): Memory = memory

    override fun setMemory(memory: Memory) {
        this.memory = memory
    }

    override fun emit(text: String) {
        outputs = outputs.plus(text)
    }

    override fun read(default: String): String {
        return inputs.remove()
    }

    fun getOutputs(): List<String> {
        return outputs
    }
}