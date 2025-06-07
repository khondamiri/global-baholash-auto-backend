package com.globalbaholash.routing

import com.globalbaholash.common.AssessmentProject
import com.globalbaholash.common.CreateAssessmentRequest
import com.globalbaholash.common.ProjectStatus
import com.globalbaholash.db.AssessmentRepository
import io.ktor.http.HttpStatusCode
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

fun Route.assessmentRoutes(repository: AssessmentRepository) {
    route("/assessment-types") {

        // get assessments WORKING
        get {
            try {
                val types = repository.getAssessmentTypes()
                call.respond(types)
            } catch (e: Exception) {
                application.log.error("Failed to get assessment types: ${e.localizedMessage}.", e)
                call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to "Could not retrieve assessment types."))
            }
        }

        //  get assessment types WORKING
        get("/{typeId}/structure") {
            val typeId = call.parameters["typeId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing assessment type ID."))

            try {
                val assessmentTypeWithFields = repository.getAssessmentTypeById(typeId)
                if (assessmentTypeWithFields != null){
                    call.respond(assessmentTypeWithFields)
                } else {
                    call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Assessment type not found"))
                }
            } catch (e: Exception) {
                application.log.error("Failed to get assessment structure for $typeId: ${e.localizedMessage}.", e)
                call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to "Could not retrieve assessment structure."))
            }
        }
    }

    authenticate("auth-jwt") {
        route("/assessments") {

            // create assessment WORKING
            post {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                try {
                    val request = call.receive<CreateAssessmentRequest>()

                    val newProject = repository.createAssessmentProject(
                        request.displayName,
                        request.assessmentTypeId, assessorId,
                        /*request.creationTimestamp,*/
                        request.fieldValues
                    )

                    if (newProject != null) {
                        call.respond(HttpStatusCode.Created, newProject)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to create assessment project."))
                    }

                } catch (e: ContentTransformationException) {
                    application.log.warn("Create assessment: Bad data request for assessor $assessorId: ${e.message}.")
                    call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid assessment data format: ${e.message}."))
                } catch (e: Exception) {
                    application.log.error("Failed to create assessment for assessor $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "An unexpected error occurred while creating the assessment."))
                }
            }

            // get assessment project for assessor WORKING
            get {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                try {
                    val projects = repository.getAssessmentProjectsForAssessor(assessorId)
                    call.respond(projects)
                } catch (e: Exception) {
                    application.log.error("Failed to get assessment for assessor $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not retrieve assessments."))
                }
            }

            // get specific project by its ID WORKING
            get("/{projectId}") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                val projectId = call.parameters["projectId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing project ID"))

                try {
                    val project = repository.getAssessmentProjectById(projectId, assessorId)
                    if (project != null) {
                        call.respond(project)
                    } else {
                        call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "Assessment project not found or not owned by user."))
                    }
                } catch (e: Exception) {
                    application.log.error("Failed to get assessment $projectId for $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not retrieve assessment project"))
                }
            }

            // update existing project WORKING
            put("/{projectId}") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                val projectIdFromPath = call.parameters["projectId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing project ID"))

                try {
                    val updatedProjectData = call.receive<AssessmentProject>()

                    if (updatedProjectData.id != projectIdFromPath || updatedProjectData.assessorId != assessorId) {
                        return@put call.respond(HttpStatusCode.Forbidden,
                            mapOf("error" to "Project ID mismatch or ownership conflict"))
                    }

                    val success = repository.updateAssessmentProject(
                        projectIdFromPath,
                        assessorId,
                        updatedProjectData.displayName,
                        updatedProjectData.status,
                        updatedProjectData.fieldValues
                    )

                    if (success) {
                        val projectAfterUpdate = repository.getAssessmentProjectById(projectIdFromPath, assessorId)
                        call.respond(HttpStatusCode.OK, projectAfterUpdate ?: mapOf("message" to "Update successful"))
                    } else {
                        call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "Assessment project not found or not updated"))
                    }
                } catch (e: ContentTransformationException) {
                    application.log.warn("Update assessment $projectIdFromPath: Bad request data for $assessorId: ${e.message}.")
                    call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid data format for update."))
                } catch (e: Exception) {
                    application.log.error("Failed to update assessment $projectIdFromPath for assessor $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "An unexpected error occurred while updating the assessment."))
                }
            }

            // delete project WORKING
            delete("/{projectId}") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                val projectId = call.parameters["projectId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing project ID."))

                try {
                    if (repository.deleteAssessmentProject(projectId, assessorId)) {
                        call.respond(HttpStatusCode.NoContent) // successful delete
                    } else {
                        call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "Assessment project not found or not owned by user."))
                    }
                } catch (e: Exception) {
                    application.log.error("Failed to delete assessment $projectId for assessor $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not delete assessment project."))
                }
            }

            // mark project finished
            post("/{projectId}/finish") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                val projectId = call.parameters["projectId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing project ID."))

                try {
                    val success = repository.updateAssessmentProject(
                        projectId, assessorId,
                        null,
                        ProjectStatus.FINISHED,
                        null
                    )

                    if (success) {
                        val projectAfterUpdate = repository.getAssessmentProjectById(projectId, assessorId)
                        call.respond(HttpStatusCode.OK,
                            projectAfterUpdate ?: mapOf("error" to "Assessment fetching failed."))
                    } else {
                        call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "Assessment project not found or failed to finish."))
                    }
                } catch (e: Exception) {
                    application.log.error("Failed to finish assessment $projectId for assessor $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not mark project as finished"))
                }
            }
        }
    }
}