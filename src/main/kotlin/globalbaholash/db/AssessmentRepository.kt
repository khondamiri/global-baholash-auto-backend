package com.globalbaholash.db

import com.globalbaholash.common.AssessmentFieldDefinition
import com.globalbaholash.common.AssessmentFieldValue
import com.globalbaholash.common.AssessmentProject
import com.globalbaholash.common.AssessmentType
import com.globalbaholash.common.CreateFieldDefinitionRequest
import com.globalbaholash.common.FieldDataType
import com.globalbaholash.common.ProjectStatus

import io.ktor.server.application.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.postgresql.util.PSQLException
import java.util.UUID
import kotlin.let

interface AssessmentRepository {

    // =================================================================================

    // ============================== CRUD FUNCTIONALITY ===============================

    suspend fun getAssessmentTypes(): List<AssessmentType>

    suspend fun getAssessmentTypeById(typeId: String): AssessmentType?

    suspend fun getFieldDefinitionsForType(typeId: String): List<AssessmentFieldDefinition>

    suspend fun createAssessmentProject(
        displayName: String,
        assessmentTypeId: String,
        assessorId: String,
        /*creationTime: Long,*/
        fieldValues: List<AssessmentFieldValue>?
    ): AssessmentProject?

    suspend fun addDocumentRecord(
        projectId: String,
        documentType: String,
        originalFileName: String,
        storedFilePath: String
    ): Boolean

    suspend fun getAssessmentProjectById(projectId: String, assessorId: String): AssessmentProject?

    suspend fun getAssessmentProjectsForAssessor(assessorId: String): List<AssessmentProject>

    suspend fun updateAssessmentProject(
        projectId: String,
        assessorId: String,
        newDisplayName: String?,
        newStatus: ProjectStatus?,
        newFieldValues: List<AssessmentFieldValue>?
    ): Boolean

    suspend fun deleteAssessmentProject(projectId: String, assessorId: String): Boolean

    // =================================================================================

    // ============================== ADMIN FUNCTIONALITY ==============================

    suspend fun createAssessmentTypeWithTemplates(
        name: String,
        description: String?,
        fieldDefinitionsRequest: List<CreateFieldDefinitionRequest>,
        templateFileNames: List<String>
    ): AssessmentType?

    suspend fun updateAssessmentType(
        typeId: String,
        newName: String?,
        newDescription: String?,
    ): AssessmentType?

    suspend fun deleteAssessmentType(typeId: String): Boolean

    suspend fun assignTypeToAssessor(assessorId: String, typeId: String): Boolean
    suspend fun removeTypeFromAssessor(assessorId: String, typeId: String): Boolean
    suspend fun getAssignedTypesForAssessor(assessorId: String): List<AssessmentType>
    suspend fun isTypeAssignedToAssessor(assessorId: String, typeId: String): Boolean

    // =================================================================================

    // =========================== PUBLISHING FUNCTIONALITY ============================

    suspend fun getDocumentsForProject(projectId: String, documentType: String): List<AssessmentProjectDocument>
    suspend fun updateProjectPublishingInfo(projectId: String, publicAccessId: String, finalDocPath: String, qrCodeData: String): Boolean

    suspend fun findProjectByPublicAccessId(publicAccessId: String): AssessmentProject?
}

class AssessmentRepositoryImpl(private val application: Application) : AssessmentRepository {

    override suspend fun getAssessmentTypes(): List<AssessmentType> = DatabaseFactory.dbQuery {
        AssessmentTypesTable.selectAll().map { mapToAssessmentType(it) }
    }

    override suspend fun getFieldDefinitionsForType(typeId: String): List<AssessmentFieldDefinition> = DatabaseFactory.dbQuery {
        AssessmentFieldDefinitionsTable
            .selectAll().where { AssessmentFieldDefinitionsTable.assessmentTypeId eq typeId }
            .orderBy(AssessmentFieldDefinitionsTable.order)
            .map { mapToFieldDefinition(it) }
    }

    override suspend fun getAssessmentTypeById(typeId: String): AssessmentType? = DatabaseFactory.dbQuery {
        exposedLogger.error("Fetching type structure for typeId: $typeId")
        val typeRow = AssessmentTypesTable.selectAll().where{ AssessmentTypesTable.id eq typeId }.singleOrNull()
        typeRow?.let {
            exposedLogger.error("Fetching definitions for typeId: $typeId")
            val definitions = try {
                getFieldDefinitionsForType(typeId)
            } catch (e: Exception) {
                exposedLogger.error("Error getting definitions for typeId: $typeId")
            }
            exposedLogger.error("Got definitions for typeId: $typeId")
            mapToAssessmentType(it, definitions as List<AssessmentFieldDefinition>?)
        }
    }

    override suspend fun createAssessmentProject(
        displayName: String,
        assessmentTypeId: String,
        assessorId: String,
        /*creationTimeInput: Long,*/
        fieldValues: List<AssessmentFieldValue>?
    ): AssessmentProject? = DatabaseFactory.dbQuery {
        val assessorRow = UsersTable.selectAll().where { UsersTable.id eq assessorId }.singleOrNull()

        if (assessorRow == null) {
            application.log.error("Attempt to create assessment for non-existent user")
            return@dbQuery null
        }

        val currentCredits = assessorRow[UsersTable.credits]

        if (currentCredits <= 0) {
            application.log.error("User tried to create project with insufficient credits: $currentCredits")
            return@dbQuery null
        }

        UsersTable.update({ UsersTable.id eq assessorId }) {
            it[credits] = currentCredits - 1
        }

        val projectId = UUID.randomUUID().toString()

        // insert main project record
        AssessmentProjectsTable.insert {
            it[id] = projectId
            it[AssessmentProjectsTable.displayName] = displayName
            it[AssessmentProjectsTable.assessmentTypeId] = assessmentTypeId
            it[AssessmentProjectsTable.assessorId] = assessorId
            it[creationTimestamp] = /*creationTimeInput*/ System.currentTimeMillis()
            it[lastModificationTimestamp] = System.currentTimeMillis()
            it[status] = ProjectStatus.ACTIVE.name
        }

        // insert field values
        val processedFieldValues = mutableListOf<AssessmentFieldValue>()
        fieldValues?.forEach { fieldValueInput ->
            val valueId = UUID.randomUUID().toString()
            val insertResult = AssessmentFieldValuesTable.insert {
                it[id] = valueId
                it[AssessmentFieldValuesTable.projectId] = projectId
                it[fieldDefinitionId] = fieldValueInput.fieldDefinitionId
                it[singleValue] = fieldValueInput.value
                it[multipleValues] = fieldValueInput.values?.let { list -> Json.encodeToString(list) }
            }
            insertResult.resultedValues?.firstOrNull() ?: return@dbQuery null
            processedFieldValues.add(fieldValueInput)
        }

        // return project
        AssessmentProject(
            id = projectId,
            displayName = displayName,
            assessmentTypeId = assessmentTypeId,
            assessorId = assessorId,
            status = ProjectStatus.ACTIVE,
            creationTimestamp = /*creationTimeInput*/ System.currentTimeMillis() ,
            lastModifiedTimestamp = System.currentTimeMillis(),
            fieldValues = processedFieldValues
        )
    }

    override suspend fun getAssessmentProjectById(
        projectId: String,
        assessorId: String,
    ): AssessmentProject? = DatabaseFactory.dbQuery {

        val projectRow = AssessmentProjectsTable
            .selectAll().where{ (AssessmentProjectsTable.id eq projectId) and (AssessmentProjectsTable.assessorId eq assessorId) }
            .singleOrNull()

        projectRow?.let {
            val values = AssessmentFieldValuesTable
                .selectAll().where{ AssessmentFieldValuesTable.projectId eq projectId }
                .map { mapToFieldValue(it) }

            mapToAssessmentProject(it, values)
        }
    }

    override suspend fun getAssessmentProjectsForAssessor(assessorId: String): List<AssessmentProject> = DatabaseFactory.dbQuery {
        AssessmentProjectsTable
            .selectAll().where{ AssessmentProjectsTable.assessorId eq assessorId }
            .orderBy(AssessmentProjectsTable.lastModificationTimestamp, SortOrder.DESC)
            .map { projectRow ->
                val values = AssessmentFieldValuesTable
                    .selectAll().where{ AssessmentFieldValuesTable.projectId eq projectRow[AssessmentProjectsTable.id] }
                    .map { mapToFieldValue(it) }

                mapToAssessmentProject(projectRow, values)
            }
    }

    override suspend fun updateAssessmentProject(
        projectId: String,
        assessorId: String,
        newDisplayName: String?,
        newStatus: ProjectStatus?,
        newFieldValues: List<AssessmentFieldValue>? // Nullable: if null, don't touch field values. If empty list, clear them.
    ): Boolean = DatabaseFactory.dbQuery {

        // 1. Verify the project exists and belongs to the assessor
        val projectRow = AssessmentProjectsTable.selectAll().where {
            (AssessmentProjectsTable.id eq projectId) and (AssessmentProjectsTable.assessorId eq assessorId)
        }.singleOrNull()

        if (projectRow == null) {
            return@dbQuery false // Project not found or not owned
        }

        // If no update parameters were provided at all.
        if (newDisplayName == null && newStatus == null && newFieldValues == null) {
            return@dbQuery false // No update requested
        }

        var hasAnythingMeaningfulChanged = false // Overall flag if any DB modification happened

        // 2. Update project metadata if new values are provided AND different
        var metadataUpdated = false // Flag specific to metadata changes
        if (newDisplayName != null && newDisplayName != projectRow[AssessmentProjectsTable.displayName] ||
            newStatus != null && newStatus.name != projectRow[AssessmentProjectsTable.status]) {

            AssessmentProjectsTable.update({ AssessmentProjectsTable.id eq projectId }) { stmt ->
                newDisplayName?.let { if (it != projectRow[AssessmentProjectsTable.displayName]) stmt[displayName] = it }
                newStatus?.let { if (it.name != projectRow[AssessmentProjectsTable.status]) stmt[status] = it.name }
                stmt[lastModificationTimestamp] = System.currentTimeMillis() // Update timestamp here
            }
            metadataUpdated = true
            hasAnythingMeaningfulChanged = true
        }

        // 3. Update field values if newFieldValues is NOT NULL
        var fieldValuesUpdated = false // Flag specific to field value changes
        if (newFieldValues != null) { // Only proceed if newFieldValues was actually passed
            // Fetch current values to see if a real change will occur (more robust)
            // For this iteration, we'll simplify and assume if newFieldValues is passed, it's an intended change.
            // The deleteWhere + insertAll effectively handles "replace" or "clear".

            // Delete all existing values for this project.
            // This operation itself counts as a change if there were values to delete.
            val deletedCount = AssessmentFieldValuesTable.deleteWhere { AssessmentFieldValuesTable.projectId eq projectId }

            if (newFieldValues.isNotEmpty()) { // If new values are provided, insert them
                newFieldValues.forEach { fieldValue ->
                    val valueId = UUID.randomUUID().toString()
                    AssessmentFieldValuesTable.insert {
                        it[id] = valueId
                        it[AssessmentFieldValuesTable.projectId] = projectId
                        it[fieldDefinitionId] = fieldValue.fieldDefinitionId
                        it[singleValue] = fieldValue.value
                        it[multipleValues] = fieldValue.values?.let { list -> Json.encodeToString(list) }
                    }
                }
                fieldValuesUpdated = true // Values were inserted
            } else { // newFieldValues is an empty list, meaning "clear all values"
                if (deletedCount > 0) { // If we actually deleted something
                    fieldValuesUpdated = true
                }
                // If deletedCount was 0 and newFieldValues is empty, no actual change to field values happened.
            }

            if (fieldValuesUpdated) {
                hasAnythingMeaningfulChanged = true
            }
        }

        // 4. If only field values were actually changed (and metadata wasn't, hence timestamp not updated by metadata block),
        //    update the project's lastModifiedTimestamp.
        if (fieldValuesUpdated && !metadataUpdated) {
            AssessmentProjectsTable.update({ AssessmentProjectsTable.id eq projectId }) {
                it[lastModificationTimestamp] = System.currentTimeMillis()
            }
            // No need to set hasAnythingMeaningfulChanged again, it's already true if fieldValuesUpdated is true.
        }

        return@dbQuery hasAnythingMeaningfulChanged // Return true if any meaningful change was persisted
    }

    override suspend fun deleteAssessmentProject(projectId: String, assessorId: String): Boolean = DatabaseFactory.dbQuery {
        val deletedRows = AssessmentProjectsTable.deleteWhere {
            (AssessmentProjectsTable.id eq projectId) and (AssessmentProjectsTable.assessorId eq assessorId)
        }
        deletedRows > 0
    }

    // =================================================================================

    // ============================== ADMIN FUNCTIONALITY ==============================

    override suspend fun createAssessmentTypeWithTemplates(
        name: String,
        description: String?,
        fieldDefinitionsRequest: List<CreateFieldDefinitionRequest>,
        templateFileNames: List<String>
    ): AssessmentType? = DatabaseFactory.dbQuery {
        if (!AssessmentTypesTable.selectAll().where { AssessmentTypesTable.name eq name }.empty()) {
            exposedLogger.warn("Attempt to create assessment type with existing name: $name")
            return@dbQuery null
        }

        val newTypeId = "type-" + name.lowercase().replace(" ", "-") + "-" + UUID.randomUUID().toString().take(8)

        AssessmentTypesTable.insert {
            it[id] = newTypeId
            it[AssessmentTypesTable.name] = name
            it[AssessmentTypesTable.description] = description
            it[AssessmentTypesTable.templateFileNames] = Json.encodeToString(templateFileNames)
        }

        val createdFieldDefinitions = mutableListOf<AssessmentFieldDefinition>()
        fieldDefinitionsRequest.forEach { fieldReq ->
            val fieldDefId = UUID.randomUUID().toString()
            AssessmentFieldDefinitionsTable.insert {
                it[id] = fieldDefId
                it[assessmentTypeId] = newTypeId
                it[fieldKey] = fieldReq.fieldKey
                it[label] = fieldReq.label
                it[fieldType] = fieldReq.fieldType.name
                it[options] = fieldReq.options?.let { opts -> Json.encodeToString(opts) }
                it[order] = fieldReq.order
                it[isRequired] = fieldReq.isRequired
                it[section] = fieldReq.section
                it[defaultTextIfEmpty] = fieldReq.defaultTextIfEmpty
            }

            createdFieldDefinitions.add(
                AssessmentFieldDefinition(
                    fieldDefId, newTypeId, fieldReq.fieldKey, fieldReq.label,
                    fieldReq.fieldType, fieldReq.options, fieldReq.isRequired,
                    fieldReq.order, fieldReq.section, fieldReq.defaultTextIfEmpty
                )
            )
        }

        AssessmentType(newTypeId, name, description, templateFileNames,createdFieldDefinitions)
    }

    override suspend fun addDocumentRecord(
        projectId: String,
        documentType: String,
        originalFileName: String,
        storedFilePath: String
    ): Boolean = DatabaseFactory.dbQuery {
        val docId = UUID.randomUUID().toString()

        AssessmentProjectDocumentsTable.insert {
            it[id] = docId
            it[AssessmentProjectDocumentsTable.projectId] = projectId
            it[AssessmentProjectDocumentsTable.documentType] = documentType
            it[AssessmentProjectDocumentsTable.originalFileName] = originalFileName
            it[AssessmentProjectDocumentsTable.storedFilePath] = storedFilePath
            it[AssessmentProjectDocumentsTable.uploadTimestamp] = System.currentTimeMillis()
        }.resultedValues?.isNotEmpty() ?: false
    }

    override suspend fun updateAssessmentType(
        typeId: String,
        newName: String?,
        newDescription: String?,
    ): AssessmentType? = DatabaseFactory.dbQuery {
        newName?.let { nn ->
            if (AssessmentTypesTable.selectAll().where { (AssessmentTypesTable.name eq nn) and (AssessmentTypesTable.id neq typeId) }.any()) {
                exposedLogger.warn("Attempt to update assessment type $typeId to existing name: $nn")
                return@dbQuery null
            }

            val updatedRows = AssessmentTypesTable.update({ AssessmentTypesTable.id eq typeId }) {
                newName?.let { nn -> it[name] = nn }
                newDescription?.let { nd -> it[description] = nd }
            }

            if (updatedRows > 0) {
                getAssessmentTypeById(typeId)
            } else {
                null
            }
        }
    }

    override suspend fun deleteAssessmentType(typeId: String): Boolean = DatabaseFactory.dbQuery {
        try {
            AssessmentTypesTable.deleteWhere { AssessmentTypesTable.id eq typeId } > 0
        } catch (e: PSQLException) {
            if (e.message?.contains("violates foreign key constraint") == true &&
                e.message?.contains("assessment_projects_assessment_type_id_fkey") == true) {
                exposedLogger.warn("Attempt to delete assessment type $typeId still in use by projects")
                false
            } else {
                throw e
            }
        }
    }

    override suspend fun assignTypeToAssessor(assessorId: String, typeId: String): Boolean = DatabaseFactory.dbQuery {
        val assessorExists = UsersTable.selectAll().where { UsersTable.id eq assessorId }.count() > 0
        val typeExists = AssessmentTypesTable.selectAll().where { AssessmentTypesTable.id eq typeId }.count() > 0

        if (!assessorExists || !typeExists) {
            exposedLogger.warn("AssignType: Assessor $assessorId or Type $typeId not found.")
            return@dbQuery false
        }

        if (AssessmentTypesTable.selectAll().where { (AssessorTypeAssignmentsTable.assessorId eq assessorId) and
                    (AssessorTypeAssignmentsTable.assessmentTypeId eq typeId) }.empty()) {
            AssessorTypeAssignmentsTable.insert {
                it[AssessorTypeAssignmentsTable.assessmentTypeId] = typeId
                it[AssessorTypeAssignmentsTable.assessorId] = assessorId
            }
            true
        } else {
            false
        }
    }

    override suspend fun removeTypeFromAssessor(assessorId: String, typeId: String): Boolean = DatabaseFactory.dbQuery {
        AssessorTypeAssignmentsTable.deleteWhere {
            (AssessorTypeAssignmentsTable.assessorId eq assessorId) and (AssessorTypeAssignmentsTable.assessmentTypeId eq typeId)
        } > 0
    }

    override suspend fun getAssignedTypesForAssessor(assessorId: String): List<AssessmentType> = DatabaseFactory.dbQuery {
        AssessmentTypesTable.join(
            AssessorTypeAssignmentsTable,
            JoinType.INNER,
            AssessmentTypesTable.id,
            AssessorTypeAssignmentsTable.assessmentTypeId
        )
            .selectAll().where { AssessorTypeAssignmentsTable.assessorId eq assessorId }
            .map { resultRow ->
                val typeId = resultRow[AssessmentTypesTable.id]
                val definitions = getFieldDefinitionsForType(typeId)
                mapToAssessmentType(resultRow, definitions)
            }
    }

    override suspend fun isTypeAssignedToAssessor(assessorId: String, typeId: String): Boolean = DatabaseFactory.dbQuery {
        !AssessorTypeAssignmentsTable.selectAll().where {
            (AssessorTypeAssignmentsTable.assessorId eq assessorId) and (AssessorTypeAssignmentsTable.assessmentTypeId eq typeId)
        }.empty()
    }

    // =================================================================================

    // =========================== PUBLISHING FUNCTIONALITY ============================

    override suspend fun getDocumentsForProject(projectId: String, documentType: String): List<AssessmentProjectDocument> =
        DatabaseFactory.dbQuery {
            AssessmentProjectDocumentsTable.selectAll().where { (AssessmentProjectDocumentsTable.projectId eq projectId) and
                    (AssessmentProjectDocumentsTable.documentType eq documentType) }
                .map { mapToDocument(it) }
        }

    override suspend fun updateProjectPublishingInfo(projectId: String, publicAccessId: String, finalDocPath: String, qrCodeData: String): Boolean =
        DatabaseFactory.dbQuery {
            AssessmentProjectsTable.update({ AssessmentProjectsTable.id eq projectId }) {
                it[AssessmentProjectsTable.publicAccessId] = AssessmentProjectsTable.publicAccessId
                it[documentStoragePath] = finalDocPath
                it[AssessmentProjectsTable.qrCodeData] = qrCodeData
            } > 0
        }

    private fun mapToDocument(row: ResultRow) = AssessmentProjectDocument(
        id = row[AssessmentProjectDocumentsTable.id],
        projectId = row[AssessmentProjectDocumentsTable.projectId],
        documentType = row[AssessmentProjectDocumentsTable.documentType],
        originalFileName = row[AssessmentProjectDocumentsTable.originalFileName],
        storedFilePath = row[AssessmentProjectDocumentsTable.storedFilePath]
    )

    override suspend fun findProjectByPublicAccessId(publicAccessId: String): AssessmentProject? = DatabaseFactory.dbQuery {
        val projectRow = AssessmentProjectsTable
            .selectAll().where { AssessmentProjectsTable.publicAccessId eq publicAccessId }
            .singleOrNull()

        projectRow?.let {
            val projectId = it[AssessmentProjectsTable.id]
            val values = AssessmentFieldValuesTable
                .selectAll().where { AssessmentFieldValuesTable.projectId eq projectId }
                .map { mapToFieldValue(it) }

            mapToAssessmentProject(it, values)
        }
    }

    // =================================================================================

    // ==================================== HELPERS ====================================

    private fun mapToAssessmentType(row: ResultRow, definitions: List<AssessmentFieldDefinition>? = null) = AssessmentType(
        id = row[AssessmentTypesTable.id],
        name = row[AssessmentTypesTable.name],
        description = row[AssessmentTypesTable.description],
        templateFileNames = row[AssessmentTypesTable.templateFileNames]?.let { jsonString ->
            if (jsonString.isNotBlank()) {
                try {
                    Json.decodeFromString(ListSerializer(String.serializer()), jsonString)
                } catch (e: Exception) {
                    application.log.error("Failed to parse templateFileNames JSON: '$jsonString' for AssessmentType ID: ${row[AssessmentTypesTable.id]}", e)
                    null
                }
            } else {
                null
            }
        },
        fieldDefinitions = definitions
    )

    private fun mapToFieldDefinition(row: ResultRow) = AssessmentFieldDefinition(
        id = row[AssessmentFieldDefinitionsTable.id],
        assessmentTypeId = row[AssessmentFieldDefinitionsTable.assessmentTypeId],
        fieldKey = row[AssessmentFieldDefinitionsTable.fieldKey],
        label = row[AssessmentFieldDefinitionsTable.label],
        fieldType = FieldDataType.valueOf(row[AssessmentFieldDefinitionsTable.fieldType]),
        options = row[AssessmentFieldDefinitionsTable.options]?.let { jsonString ->
            try {
                Json.decodeFromString(ListSerializer(String.serializer()), jsonString)
            } catch (e: Exception) {
                println("ERROR parsing options JSON: '$jsonString' for field ${row[AssessmentFieldDefinitionsTable.fieldKey]} - ${e.message}")
                null
            }
        },
        isRequired = row[AssessmentFieldDefinitionsTable.isRequired],
        order = row[AssessmentFieldDefinitionsTable.order],
        section = row[AssessmentFieldDefinitionsTable.section],
        defaultTextIfEmpty = row[AssessmentFieldDefinitionsTable.defaultTextIfEmpty]
    )

    private fun mapToAssessmentProject(row: ResultRow, values: List<AssessmentFieldValue>) = AssessmentProject(
        id = row[AssessmentProjectsTable.id],
        displayName = row[AssessmentProjectsTable.displayName],
        assessmentTypeId = row[AssessmentProjectsTable.assessmentTypeId],
        assessorId = row[AssessmentProjectsTable.assessorId],
        status = ProjectStatus.valueOf(row[AssessmentProjectsTable.status]),
        creationTimestamp = row[AssessmentProjectsTable.creationTimestamp],
        lastModifiedTimestamp = row[AssessmentProjectsTable.lastModificationTimestamp],
        fieldValues = values,
        publicAccessId = row[AssessmentProjectsTable.publicAccessId],
        documentStoragePath = row[AssessmentProjectsTable.documentStoragePath],
        qrCodeData = row[AssessmentProjectsTable.qrCodeData]
    )

    private fun mapToFieldValue(row: ResultRow) = AssessmentFieldValue(
        fieldDefinitionId = row[AssessmentFieldValuesTable.fieldDefinitionId],
        value = row[AssessmentFieldValuesTable.singleValue],
        values = row[AssessmentFieldValuesTable.multipleValues]?.let { jsonString ->
            Json.decodeFromString(ListSerializer(String.serializer()), jsonString)
        }
    )
}

data class AssessmentProjectDocument(val id: String, val projectId: String, val documentType: String, val originalFileName: String, val storedFilePath: String)