package com.globalbaholash.routing

import com.globalbaholash.common.AssessmentProject
import com.globalbaholash.common.CreateAssessmentRequest
import com.globalbaholash.common.ProjectStatus
import com.globalbaholash.db.AssessmentRepository
import com.globalbaholash.db.UserRepository
import globalbaholashauto.services.ReportService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.io.File
import java.nio.file.Paths

fun Route.assessmentRoutes(assessmentRepository: AssessmentRepository, userRepository: UserRepository, reportService: ReportService) {

    authenticate("auth-jwt") {
        route("/assessment-types") {

            // get assessment types
            get {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "User unauthorized")
                try {
                    val assignedTypes = assessmentRepository.getAssignedTypesForAssessor(assessorId)
                    call.respond(assignedTypes)
                } catch (e: Exception) {
                    application.log.error("Failed to get assessment types: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not retrieve assessment types."))
                }
            } // [*]

            //  get assessment types
            get("/{typeId}/structure") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "User unauthorized")

                val typeId = call.parameters["typeId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing assessment type ID."))

                try {
                    val isAssigned = assessmentRepository.isTypeAssignedToAssessor(assessorId, typeId)
                    if (!isAssigned) return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied")
                    )

                    val assessmentTypeWithFields = assessmentRepository.getAssessmentTypeById(typeId)
                    if (assessmentTypeWithFields != null){
                        call.respond(assessmentTypeWithFields)
                    } else {
                        application.log.error("Assessor $assessorId: Assigned type $typeId structure not found unexpectedly.")
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Assessment type structure not found.")
                        )
                    }
                } catch (e: Exception) {
                    application.log.error("Failed to get assessment structure for $typeId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not retrieve assessment structure."))
                }
            } // [*]
        }

        route("/assessments") {

            // ASSESSMENT WORKFLOW

            // create assessment
            post {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                try {
                    val request = call.receive<CreateAssessmentRequest>()

                    val isTypeAssigned = assessmentRepository.isTypeAssignedToAssessor(assessorId, request.assessmentTypeId)
                    if (!isTypeAssigned) {
                        application.log.warn("Assessor $assessorId attempted to create project with unassigned type ${request.assessmentTypeId}")
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "You are not authorized to use this assessment type.")
                        )
                    }

                    val user = userRepository.findUserById(assessorId)
                    if (user == null || user.credits <= 0 ) {
                        application.log.error("Insufficient credits: ${user?.credits} for ${user?.username}")
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Insufficient credits to create new assessment")
                        )
                    }

                    val newProject = assessmentRepository.createAssessmentProject(
                        request.displayName,
                        request.assessmentTypeId,
                        assessorId,
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
            } // [*]

            // get assessment project for assessor
            get{
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                try {
                    val projects = assessmentRepository.getAssessmentProjectsForAssessor(assessorId)
                    call.respond(projects)
                } catch (e: Exception) {
                    application.log.error("Failed to get assessment for assessor $assessorId: ${e.localizedMessage}.", e)
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not retrieve assessments."))
                }
            } // [*]

            // get specific project by its ID
            get("/{projectId}") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized,
                        mapOf("error" to "User not authenticated or userId missing in token."))

                val projectId = call.parameters["projectId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing project ID"))

                try {
                    val project = assessmentRepository.getAssessmentProjectById(projectId, assessorId)
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
            } // [*]

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

                    val success = assessmentRepository.updateAssessmentProject(
                        projectIdFromPath,
                        assessorId,
                        updatedProjectData.displayName,
                        updatedProjectData.status,
                        updatedProjectData.fieldValues
                    )

                    if (success) {
                        val projectAfterUpdate = assessmentRepository.getAssessmentProjectById(projectIdFromPath, assessorId)
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
            } // [*]

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
                    if (assessmentRepository.deleteAssessmentProject(projectId, assessorId)) {
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
            } // [*]

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
                    val success = assessmentRepository.updateAssessmentProject(
                        projectId, assessorId,
                        null,
                        ProjectStatus.FINISHED,
                        null
                    )

                    if (success) {
                        val projectAfterUpdate = assessmentRepository.getAssessmentProjectById(projectId, assessorId)
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
            } // [*]

            // DOCUMENT WORKFLOW

            // generate initial docs
            post("/{projectId}/generate-initial-documents") {
                val assessorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
                val projectID = call.parameters["projectId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val generatedDocs = reportService.generateInitialDocuments(projectID, assessorId)
                if (generatedDocs != null) {
                    call.respond(HttpStatusCode.OK, generatedDocs)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            } // [*]

            // download specific generated files
            get("/{projectId}/documents/{documentType}/{fileName...}") {
                val assessorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
                val projectID = call.parameters["projectId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val documentType = call.parameters["documentType"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val fileName = call.parameters.getAll("fileName")?.joinToString("/") ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (assessmentRepository.getAssessmentProjectById(projectID, assessorId) == null) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val file = File("storage/projects/$projectID/$documentType/$fileName")
                if (file.exists()) call.respondFile(file) else call.respond(HttpStatusCode.NotFound)
            } // [*]

            // upload modified docs
            post("/{projectId}/upload-modified") {
                val assessorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
                val projectID = call.parameters["projectId"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (assessmentRepository.getAssessmentProjectById(projectID, assessorId) == null) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }

                val multipartData = call.receiveMultipart()
                val uploadedFilesInfo = mutableListOf<String>()
                multipartData.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val originalFileName = part.originalFileName ?: "unknown"
                        val storageDir = Paths.get("storage", "projects", projectID, "modified").toFile()
                        storageDir.mkdirs()
                        val file = File(storageDir, originalFileName)
                        part.streamProvider().use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }

                        val storedRelativePath = Paths.get(projectID, "modified", originalFileName).toString()
                        assessmentRepository.addDocumentRecord(projectID, "MODIFIED", originalFileName, storedRelativePath)
                        uploadedFilesInfo.add(originalFileName)
                    }
                    part.dispose
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "Files uploaded", "uploaded files" to uploadedFilesInfo))
            } // [*]

            // PUBLISHING ENDPOINTS

            // publish final report
            post("/{projectId}/publish") {
                val principal = call.principal<JWTPrincipal>()
                val assessorId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val projectId = call.parameters["projectId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing project ID")

                try {
                    val appConfig = application.environment.config
                    val publicBaseUrl = appConfig.propertyOrNull("app.paths.publicBaseUrl")?.getString()
                        ?: "http://localhost:8080/public/docs"
                    val publishInfo = reportService.publishAssessment(projectId, assessorId, publicBaseUrl)

                    if (publishInfo != null) {
                        call.respond(HttpStatusCode.OK, publishInfo)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to publish assessment.")
                        )
                    }
                } catch (e: Exception) {
                    application.log.error("Publishing endpoint failed for project $projectId: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred during publishing.")
                }
            }
        }
    }
}