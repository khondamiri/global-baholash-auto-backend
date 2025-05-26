package com.plugins

import com.db.UserRepository
import com.routing.authRoutes
import com.services.AuthService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val userRepository = UserRepository()
    val authService = AuthService()

    routing {
        get("/") {
            call.respondText("Assessment App backend is up")
        }

        authRoutes(userRepository, authService)
    }
}
