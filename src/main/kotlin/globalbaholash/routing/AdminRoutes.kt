package globalbaholash.routing

import com.globalbaholash.common.AssessmentProject
import com.globalbaholash.common.AssessmentTypeUpsertRequest
import com.globalbaholash.common.AssignTypeToAssessorRequest
import com.globalbaholash.common.CreateAssessmentRequest
import com.globalbaholash.common.ProjectStatus
import com.globalbaholash.common.UpdateAssessorCreditRequest
import com.globalbaholash.common.UpdateAssessorStatusRequest
import com.globalbaholash.common.UpdateTypeNameDescRequest
import com.globalbaholash.db.AssessmentRepository
import com.globalbaholash.db.AssessmentTypesTable
import com.globalbaholash.db.UserRepository
import com.globalbaholash.db.UsersTable
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.request
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

fun Route.adminRoutes(userRepository: UserRepository, assessmentRepository: AssessmentRepository) {
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
            } // [*]

            // assessment types manipulation
            route("/assessment-types") {

                // create assessment type
                post {
                    if (!call.isAdmin())
                        return@post call.respond(HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )

                    try {
                        val request = call.receive<AssessmentTypeUpsertRequest>()
                        val newType = assessmentRepository.createAssessmentType(
                            request.name,
                            request.description,
                            request.fieldDefinitions
                        )

                        if(newType != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "Assessment type ${request.name} created")
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "This assessment type might already exist or creation failed")
                            )
                        }
                    } catch (e: Exception) {
                        application.log.error("ADMIN: Failed to create assessment type: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid data for assessment type")
                        )
                    }
                } // [*]

                // list all assessment types
                get {
                    if (!call.isAdmin()) return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )

                    try {
                        val types = assessmentRepository.getAssessmentTypes()
                        call.respond(types)
                    } catch (e: Exception) {
                        application.log.error("Admin: Failed to list assessment types: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Could not retrieve assessment types"
                        )
                    }
                } // [*]

                // get specific assessment type
                get("/{typeId}") {
                    if (!call.isAdmin()) return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )
                    val typeId = call.parameters["typeId"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing type ID")
                    )

                    try {
                        val type = assessmentRepository.getAssessmentTypeById(typeId)
                        if (type != null) call.respond(type) else call.respond(HttpStatusCode.NotFound)
                    } catch (e: Exception) {
                        application.log.error("Admin: Failed to get assessment type $typeId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Could not retrieve assessment type")
                        )
                    }
                } // [*]

                // update assessment type
                put("/{typeId}") {
                    if (!call.isAdmin()) return@put call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )
                    val typeId = call.parameters["typeId"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing type I")
                    )

                    try {
                        val request = call.receive<UpdateTypeNameDescRequest>()
                        val updatedType = assessmentRepository.updateAssessmentType(typeId, request.name, request.description)

                        if (updatedType != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "Type ${request.name} updated")
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "Type not found or update failed")
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "ADMIN: Failed to update assessment type $typeId")
                        )
                        application.log.error("Invalid data for update ${e.localizedMessage}", e)
                    }
                } // [*]

                // delete assessment type
                delete("/{typeId}") {
                    if (!call.isAdmin()) return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )

                    val typeId = call.parameters["typeId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing type ID")
                    )

                    try {
                        if (assessmentRepository.deleteAssessmentType(typeId)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "Could not delete assessment type: " +
                                        "it might be in use by assessment projects or not found"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        application.log.error("ADMIN: could not delete assessment type: $typeId")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Could not delete assessment type")
                        )
                    }
                } // [*]
            }

            // assessors manipulation
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
                } // [*]

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
                        } else if (assessor != null) {
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
                } // [*]

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
                } // [*]

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
                } // [*]

                // delete assessor account
                delete("/{assessorId}/delete") {
                    if (!call.isAdmin()) {
                        return@delete call.respond(HttpStatusCode.Forbidden,
                            mapOf("error" to "Access denied"))
                    }

                    val assessorId = call.parameters["assessorId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest,
                            mapOf("error" to "Missing assessor ID")
                        )

                    try {
                        if (userRepository.deleteAssessorById(assessorId)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "User not found or an assessor")
                            )
                        }
                    } catch (e: Exception) {
                        application.log.error("Admin: Failed to delete assessor $assessorId: ${e.localizedMessage}", e)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Could not delete assessor account."))
                    }
                } // [*]
            }

            // assessment type assignment
            route("/assessors/{assessorId}/assessment-types") {

                // assign type to assessor
                post {
                    if (!call.isAdmin()) return@post call.respond(HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )
                    val assessorId = call.parameters["assessorId"] ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Missing assessor ID")
                    )

                    try {
                        val request = call.receive<AssignTypeToAssessorRequest>()

                        if (assessmentRepository.assignTypeToAssessor(assessorId, request.assessmentTypeId)) {
                            call.respond(
                                HttpStatusCode.Created,
                                mapOf("message" to "Type successfully assigned to assessor")
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "Failed to assign type to assessor")
                            )
                        }
                    } catch (e: Exception) {
                        application.log.error("Failed to assign type to assessor $assessorId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid data for assignment: ${e.message}")
                        )
                    }
                } // [*]

                // list assigned types
                get {
                    if (!call.isAdmin()) return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )

                    val assessorId = call.parameters["assessorId"] ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Missing assessorId")
                    )

                    try {
                        val assignedTypes = assessmentRepository.getAssignedTypesForAssessor(assessorId)
                        call.respond(assignedTypes)
                    } catch (e: Exception) {
                        application.log.error("ADMIN: Failed to assigned types for assessor $assessorId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Could not retrieve assigned types")
                        )
                    }
                } // [*]

                // delete assigned type
                delete("/typeId") {
                    if (!call.isAdmin()) return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )

                    val assessorId = call.parameters["assessorId"] ?: return@delete call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Missing assessorId")
                    )

                    val typeId = call.parameters["typeId"] ?: return@delete call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Missing assessorId")
                    )

                    try {
                        if (assessmentRepository.removeTypeFromAssessor(assessorId, typeId)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "Assignment not found or failed to remove")
                            )
                        }
                    } catch (e: Exception) {
                        application.log.error("Admin: Failed to remove type $typeId from assessor $assessorId: ${e.localizedMessage}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Could not remove type assignment."
                        )
                    }
                } // [*]
            }
        }
    }
}