package com.globalbaholash.plugins

import com.globalbaholash.db.AssessmentRepositoryImpl
import com.globalbaholash.db.UserRepository
import com.globalbaholash.routing.assessmentRoutes
import com.globalbaholash.routing.authRoutes
import com.globalbaholash.services.AuthService
import com.globalbaholash.services.EmailService
import globalbaholashauto.routing.adminRoutes
import globalbaholashauto.services.ReportService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    val userRepository = UserRepository()
    val authService = AuthService()
    val assessmentRepository = AssessmentRepositoryImpl(this)
    val emailService = EmailService(environment)
    val reportService = ReportService(this, assessmentRepository, userRepository)

    routing {
        get("/") {
            call.respondText("Assessment App backend is up")
        }

        authRoutes(userRepository, authService, emailService)

        route("/api") {
            assessmentRoutes(assessmentRepository, userRepository, reportService)
            adminRoutes(userRepository, assessmentRepository)
        }

        // public access
        route("/public/docs") {
            get("/{publicAccessId}") {
                val publicAccessId = call.parameters["publicAccessId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing access ID.")

                try {
                    val project = assessmentRepository.findProjectByPublicAccessId(publicAccessId)

                    if (project != null && project.documentStoragePath != null) {
                        val finalPdfFile = File(project.documentStoragePath)

                        if (finalPdfFile.exists()) {
                            call.respond(finalPdfFile)
                        } else {
                            application.log.error("Public document not found on disk for access ID $publicAccessId at path ${project.documentStoragePath}")
                            call.respond(HttpStatusCode.NotFound, "The requested document could not be found.")
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "No document found for the provided link.")
                    }
                } catch (e: Exception) {
                    application.log.error("Error serving public document for access ID $publicAccessId: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, "An error occurred while retrieving the document.")
                }
            }
        }
    }
}
