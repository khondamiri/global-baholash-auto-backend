package com.db

import com.common.AssessmentFieldDefinition
import com.common.AssessmentFieldValue
import com.common.AssessmentProject
import com.common.AssessmentType
import com.common.FieldDataType
import com.common.ProjectStatus
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.util.logging.Logger

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.let

interface AssessmentRepository {

    // assessment types and fields

    suspend fun getAssessmentTypes(): List<AssessmentType>

    suspend fun getAssessmentTypeById(typeId: String): AssessmentType?

    suspend fun getFieldDefinitionsForType(typeId: String): List<AssessmentFieldDefinition>

    // CRUD functions

    suspend fun createAssessmentProject(
        displayName: String,
        assessmentTypeId: String,
        assessorId: String,
        /*creationTime: Long,*/
        fieldValues: List<AssessmentFieldValue>?
    ): AssessmentProject?

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
}

class AssessmentRepositoryImpl() : AssessmentRepository {

    // ==================================
    //             METHODS
    // ==================================

    // assessment types and fields

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

    // CRUD functions

    override suspend fun createAssessmentProject(
        displayName: String,
        assessmentTypeId: String,
        assessorId: String,
        /*creationTimeInput: Long,*/
        fieldValues: List<AssessmentFieldValue>?
    ): AssessmentProject? = DatabaseFactory.dbQuery {
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
            id = projectId, displayName = displayName, assessmentTypeId = assessmentTypeId,
            assessorId = assessorId, status = ProjectStatus.ACTIVE,
            creationTimestamp = /*creationTimeInput*/ System.currentTimeMillis() , lastModifiedTimestamp = System.currentTimeMillis(),
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

//    override suspend fun updateAssessmentProject(
//        projectId: String,
//        assessorId: String,
//        newDisplayName: String?,
//        newStatus: ProjectStatus?,
//        newFieldValues: List<AssessmentFieldValue>?
//    ): Boolean = DatabaseFactory.dbQuery {
//        val projectRow = AssessmentProjectsTable.selectAll().where {
//            (AssessmentProjectsTable.id eq projectId) and (AssessmentProjectsTable.assessorId eq assessorId)
//        }.singleOrNull()
//
//        if (projectRow == null) return@dbQuery false
//        if (newDisplayName == null && newStatus == null && newFieldValues == null) return@dbQuery false
//
//        var updated = false
//
//        // updating metadata
//
//        if (newDisplayName != null || newStatus != null) {
//            val updateCount = AssessmentProjectsTable.update( { (AssessmentProjectsTable.id eq projectId) })
//            { stmt ->
//                var metadataUpdated = false
//                newDisplayName?.let {
//                    if (it != projectRow[AssessmentProjectsTable.displayName])
//                        stmt[displayName] = it
//                        metadataUpdated = true
//                }
//                newStatus?.let {
//                    if (it.name != projectRow[AssessmentProjectsTable.status])
//                        stmt[status] = it.name
//                        metadataUpdated = true
//                }
//
//                if (metadataUpdated)
//                    stmt[lastModificationTimestamp] = System.currentTimeMillis()
//                    updated = true
//            }
//        }
//
//        // updating fields
//
//        newFieldValues?.let { valuesToUpdate ->
//            AssessmentFieldValuesTable.deleteWhere { AssessmentFieldValuesTable.projectId eq projectId }
//
//            if (valuesToUpdate.isNotEmpty()) {
//                valuesToUpdate.forEach { fieldValue ->
//                    val valueId = UUID.randomUUID().toString()
//                    AssessmentFieldValuesTable.insert {
//                        it[id] = valueId
//                        it[AssessmentFieldValuesTable.projectId] = projectId
//                        it[fieldDefinitionId] = fieldValue.fieldDefinitionId
//                        it[singleValue] = fieldValue.value
//                        it[multipleValues] = fieldValue.values?.let { list -> Json.encodeToString(list) }
//                    }
//                }
//
//                if (!updated) {
//                    AssessmentProjectsTable.update( { (AssessmentProjectsTable.id eq projectId) } ) {
//                        it[lastModificationTimestamp] = System.currentTimeMillis()
//                    }
//                }
//                updated = true
//            } else {
//                if (!updated) {
//                    AssessmentProjectsTable.update( { (AssessmentProjectsTable.id eq projectId) } ) {
//                        it[lastModificationTimestamp] = System.currentTimeMillis()
//                    }
//                }
//                updated = true
//            }
//        }
//
//        if (newDisplayName == null && newStatus == null && newFieldValues == null) {
//            return@dbQuery false // no update
//        }
//
//        return@dbQuery updated
//    }

    override suspend fun deleteAssessmentProject(projectId: String, assessorId: String): Boolean = DatabaseFactory.dbQuery {
        val deletedRows = AssessmentProjectsTable.deleteWhere {
            (AssessmentProjectsTable.id eq projectId) and (AssessmentProjectsTable.assessorId eq assessorId)
        }
        deletedRows > 0
    }

    // ==================================
    //         HELPER FUNCTIONS
    // ==================================

    private fun mapToAssessmentType(row: ResultRow, definitions: List<AssessmentFieldDefinition>? = null) = AssessmentType(
        id = row[AssessmentTypesTable.id],
        name = row[AssessmentTypesTable.name],
        description = row[AssessmentTypesTable.description],
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
