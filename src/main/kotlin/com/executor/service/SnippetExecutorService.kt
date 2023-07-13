package com.executor.service

import com.executor.dto.Mismatch
import com.executor.dto.Result
import com.executor.dto.TestCaseResponseDTO
import com.executor.dto.TestCaseResultDTO
import com.executor.interpreter.TestContextProvider
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import printscript.language.interpreter.contextProvider.ContextProvider
import printscript.language.interpreter.interpreter.InterpreterImpl
import printscript.language.interpreter.interpreter.InterpreterWithIterator
import printscript.language.lexer.LexerFactory
import printscript.language.lexer.TokenListIterator
import printscript.language.parser.ASTIterator
import printscript.language.parser.ParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

@Service
class SnippetExecutorService {

    private var snippetManagerUrl = System.getenv("SNIPPET_MANAGER_URL") ?: "Not found :'("

    fun executeTest(id: UUID, accessToken: String): TestCaseResultDTO {
        val testCase = getTestCase(id.toString(), accessToken)
        val inputs = LinkedList(testCase.inputs)
        val contextProvider = TestContextProvider(inputs)

        try {
            execute(testCase.content, contextProvider)
        }
        catch (e: Exception) {
            return TestCaseResultDTO(
                result = Result.FAILURE,
                outputMismatch = listOf(),
                errorMessage = e.message
            )
        }

        val outputs = contextProvider.getOutputs()

        val outputMismatch = testCase.outputs.zip(outputs).mapIndexed { index, pair ->
            if (pair.first != pair.second) {
                Mismatch(pair.first, pair.second)
            } else {
                null
            }
        }.filterNotNull()

        return TestCaseResultDTO(
            result = if (outputMismatch.isEmpty()) Result.SUCCESS else Result.FAILURE,
            outputMismatch = outputMismatch,
            errorMessage = null
        )
    }

    fun getSnippet(snippetId: String, accessToken: String): String {
        val url = URL(snippetManagerUrl + snippetId)
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.requestMethod = "GET"
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val jsonResponse = JSONObject(response)
        return jsonResponse.getString("content");
    }

    fun getTestCase(snippetId: String, accessToken: String): TestCaseResponseDTO {
        val url = URL(snippetManagerUrl + "test/" +  snippetId)
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.requestMethod = "GET"
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val jsonResponse = JSONObject(response)
        val snippet = jsonResponse.getJSONObject("snippet")
        return TestCaseResponseDTO(
            content = snippet.getString("content"),
            inputs = jsonResponse.getJSONArray("inputs").toList().map { it.toString() },
            outputs = jsonResponse.getJSONArray("outputs").toList().map { it.toString() }
        )
    }

    private fun getAstIterator(code: String): ASTIterator {
        val inputStream = code.byteInputStream(Charsets.UTF_8)
        val lexer = LexerFactory().createLexer("1.1", inputStream)
        val tokenListIterator = TokenListIterator(lexer)
        val parser = ParserFactory().createParser("1.1")
        return ASTIterator(parser, tokenListIterator)
    }

    fun execute(code: String, contextProvider: ContextProvider) {
        val astIterator = getAstIterator(code)
        val interpreter = InterpreterWithIterator(InterpreterImpl(contextProvider), astIterator)
        while (interpreter.hasNextInterpretation()) {
            interpreter.interpretNextAST()
        }
    }

}