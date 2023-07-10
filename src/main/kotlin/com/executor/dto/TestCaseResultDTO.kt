package com.executor.dto

// Result enum
enum class Result {
    SUCCESS,
    FAILURE
}

data class Mismatch(
    val expected: String,
    val actual: String
)

data class TestCaseResultDTO(
    val result: Result,
    val outputMismatch: List<Mismatch>?,
    val errorMessage: String?
)