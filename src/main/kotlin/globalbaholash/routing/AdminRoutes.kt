package globalbaholash.routing

import com.globalbaholash.common.AssessmentProject
import com.globalbaholash.common.CreateAssessmentRequest
import com.globalbaholash.common.ProjectStatus
import com.globalbaholash.common.UpdateAssessorCreditRequest
import com.globalbaholash.common.UpdateAssessorStatusRequest
import com.globalbaholash.db.UserRepository
import com.globalbaholash.db.UsersTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.locks.reentrantLock

fun Route.adminRoutes(userRepository: UserRepository) {
    authenticate("auth-jwt") {
        route("/admin") {

            fun ApplicationCall.isAdmin(): Boolean {
                val principal = principal<JWTPrincipal>()
                return principal?.payload?.getClaim("role")?.asString() == "ADMIN"
            }

            // admin check
            get("/check") {
                val principal = call.principal<JWTPrincipal>()
                val userRole = principal?.payload?.getClaim("role")?.asString()

                if (userRole == "ADMIN") {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Accessed"))
                } else {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
            }

            route("/assessors") {

                // list assessors
                get {
                    if (!call.isAdmin()) {
                        return@get call.respond(HttpStatusCode.Forbidden,
                            mapOf("error" to "Access denied"))
                    }

                    try {
                        val assessors = userRepository.getAllAssessors()
                        call.respond(assessors)
                    } catch (e: Exception) {
                        application.log.error("*** ADMIN: failed to get assessors ***")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Could not retrieve assessors list")
                        )
                    }
                }

                // get assessor by ID
                get("/{assessorId}") {
                    if (!call.isAdmin()) {
                        return@get call.respond(HttpStatusCode.Forbidden,
                            mapOf("error" to "Access denied"))
                    }

                    val assessorId = call.parameters["assessorId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest,
                            mapOf("error" to "Missing assessor ID")
                        )

                    try {
                        val assessor = userRepository.findUserById(assessorId)
                        if (assessor != null && assessor.role == "ASSESSOR") {
                            call.respond(assessor)
                        } else if (assessor != null && assessor.role != "ASSESSOR") {
                            call.respond(
                                HttpStatusCode.Forbidden,
                                mapOf("error" to "Specified User is not ASSESSOR")
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "User not found")
                            )
                        }
                    } catch (e: Exception) {
                        application.log.error("*** ADMIN: failed to get assessor: $assessorId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to retrieve assessor details")
                        )
                    }
                }

                // set assessor credits
                put("/{assessorId}/credits") {
                    if (!call.isAdmin()) {
                        return@put call.respond(HttpStatusCode.Forbidden,
                            mapOf("error" to "Access denied"))
                    }

                    val assessorId = call.parameters["assessorId"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest,
                            mapOf("error" to "Missing assessor ID")
                        )

                    try {
                        val request = call.receive<UpdateAssessorCreditRequest>()

                        if (request.credits < 0) {
                            return@put call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Credits cannot be negative")
                            )
                        }

                        val updatedAssessor = userRepository.setUserCredits(assessorId, request.credits)
                        if (updatedAssessor != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "$assessorId's credits updated to ${request.credits}")
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "Assessor not found or failed to update")
                            )
                        }
                    } catch (e: ContentTransformationException) {
                        application.log.error("*** ADMIN: bad request data for credits for $assessorId: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Bad request data for credits")
                        )
                    } catch (e: Exception) {
                        application.log.error("*** ADMIN: failed to get credits for $assessorId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to retrieve assessor details")
                        )
                    }
                }

                // set assessor status
                put("/{assessorId}/status") {
                    if (!call.isAdmin()) {
                        return@put call.respond(HttpStatusCode.Forbidden,
                            mapOf("error" to "Access denied"))
                    }

                    val assessorId = call.parameters["assessorId"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest,
                            mapOf("error" to "Missing assessor ID")
                        )

                    try {
                        val request = call.receive<UpdateAssessorStatusRequest>()

                        val success = userRepository.setUserActiveStatus(assessorId, request.isActive)

                        if (success) {
                            val updatedAssessor = userRepository.findUserById(assessorId)
                            call.respond(HttpStatusCode.OK, updatedAssessor.toString())
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "Assessor not found or failed to update status")
                            )
                        }


                    } catch (e: ContentTransformationException) {
                        application.log.error("*** ADMIN: bad request data for credits for $assessorId: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Bad request data for credits")
                        )
                    } catch (e: Exception) {
                        application.log.error("*** ADMIN: Failed to update status for $assessorId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to update status")
                        )
                    }
                }
            }
        }
    }
}

/*TEMPLATES
if (!call.isAdmin()) {
    return@put call.respond(HttpStatusCode.Forbidden,
        mapOf("error" to "Access denied"))
}

val assessorId = call.parameters["assessorId"]
    ?: return@put call.respond(HttpStatusCode.BadRequest,
        mapOf("error" to "Missing assessor ID")
    )

try {
    if () {
        call.respond(
            HttpStatusCode.OK,
            mapOf("message" to "")
        )
    } else {
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to "Assessor not found or failed to update")
        )
    }
} catch (e: ContentTransformationException) {
    application.log.error("*** ADMIN: bad request data for credits for $assessorId: ${e.message}", e)
    call.respond(
        HttpStatusCode.BadRequest,
        mapOf("error" to "Bad request data for credits")
    )
} catch (e: Exception) {
    application.log.error("***  ${e.localizedMessage}", e)
    call.respond(
        HttpStatusCode.InternalServerError,
        mapOf("error" to "Failed to ")
    )
}*/