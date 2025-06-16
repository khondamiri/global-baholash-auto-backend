package com.globalbaholash.plugins

import com.globalbaholash.db.AssessmentRepositoryImpl
import com.globalbaholash.db.UserRepository
import com.globalbaholash.routing.assessmentRoutes
import com.globalbaholash.routing.authRoutes
import com.globalbaholash.services.AuthService
import com.globalbaholash.services.EmailService
import globalbaholash.routing.adminRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val userRepository = UserRepository()
    val authService = AuthService()
    val assessmentRepository = AssessmentRepositoryImpl(this)
    val emailService = EmailService(environment)

    routing {
        get("/") {
            call.respondText("Assessment App backend is up")
        }

        authRoutes(userRepository, authService, emailService)

        route("/api") {
            assessmentRoutes(assessmentRepository, userRepository)
            adminRoutes(userRepository, assessmentRepository)
        }
    }
}
