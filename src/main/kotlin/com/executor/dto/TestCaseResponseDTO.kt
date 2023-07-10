package com.executor.dto

data class TestCaseResponseDTO(
    val content: String,
    val inputs: List<String>,
    val outputs: List<String>
)