package com.globalbaholash

import com.globalbaholash.db.DatabaseFactory
import com.globalbaholash.db.initAdmin
import com.globalbaholash.db.stdTypeSeeder
import com.globalbaholash.db.testTypeSeeder
import com.globalbaholash.plugins.configureRouting
import com.globalbaholash.plugins.configureSecurity
import com.globalbaholash.plugins.configureSerialization
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(environment.config)

    launch {
        try {
//            stdTypeSeeder(log)
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
