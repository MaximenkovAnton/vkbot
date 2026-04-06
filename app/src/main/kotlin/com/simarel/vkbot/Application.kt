package com.simarel.vkbot

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.annotations.QuarkusMain

/**
 * Main entry point for the VK Bot application.
 */
@QuarkusMain
object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        Quarkus.run(*args)
    }
}
