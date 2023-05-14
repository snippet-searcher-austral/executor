package com.executor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Main entry point for the application.
 * @property args The command line arguments.
 */
@SpringBootApplication
class ExecutorApplication

/**
 * Main entry point for the application.
 */
fun main(args: Array<String>) {
    runApplication<ExecutorApplication>(*args)
}
/**
 * Controller for the application.
 * @property name The name of the person to greet.
 */
@RestController
class MessageController {
    /**
     * Returns a greeting for the given name.
     */
    @GetMapping("/")
    fun index(@RequestParam("name") name: String): String = "Hello, $name!"
}