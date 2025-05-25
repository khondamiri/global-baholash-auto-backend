package com

import com.db.DatabaseFactory
import com.plugins.configureRouting
import com.plugins.configureSecurity
import com.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureSerialization()
    configureRouting()
    DatabaseFactory.init(environment.config)
}
