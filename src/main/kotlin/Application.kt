package com

import com.db.DatabaseFactory
import com.db.initAdmin
import com.db.stdTypeSeeder
import com.db.testTypeSeeder
import com.plugins.configureRouting
import com.plugins.configureSecurity
import com.plugins.configureSerialization
import io.ktor.server.application.*
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(environment.config)

    launch {
        try {
            stdTypeSeeder(log)
            testTypeSeeder(log)
        } catch (e: Exception) {
            log.error("Data seeding failed during startup.", e)
        }

        try {
            initAdmin(log)
        } catch (e: Exception) {
            log.error("Admin initialization failed during startup:", e)
        }
    }

    configureSerialization()
    configureSecurity()
    configureRouting()
}
