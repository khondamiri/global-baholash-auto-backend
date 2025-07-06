package com.globalbaholash.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object UsersTable: Table("users") {
    val id = varchar("id", 36)
    val username = varchar("username", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50)
    val email = varchar("email", 255).uniqueIndex()
    val isEmailVerified = bool("is_email_verified").default(false)
    val credits = integer("credits").default(0).check { it greaterEq 0 }
    val passwordResetToken = varchar("password_reset_token", 255).nullable()
    val passwordResetTokenExpiry = long("password_reset_token_expiry").nullable()
    val verificationToken = varchar("verification_token", 255).nullable()
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object AssessmentProjectsTable: Table("assessment_projects") {
    val id = varchar("id", 36)
    val displayName = varchar("display_name", 255)
    val assessmentTypeId = varchar("assessment_type_id", 36)
        .references(AssessmentTypesTable.id, onDelete = ReferenceOption.SET_NULL)
    val assessorId = varchar("assessor_id", 36)
        .references(UsersTable.id, onDelete = ReferenceOption.SET_NULL)

    val status = varchar("status", 50)
    val creationTimestamp = long("creation_timestamp")
    val lastModificationTimestamp = long("last_modification_timestamp")

    val publicAccessId = varchar("public_access_id", 36).nullable().uniqueIndex()
    val documentStoragePath = text("document_storage_path").nullable()
    val qrCodeData = text("qr_code_data").nullable()

    override val primaryKey = PrimaryKey(id)
}

// FIELDS TABLES

object AssessmentFieldDefinitionsTable: Table("assessment_field_definitions") {
    val id = varchar("id", 36)
    val assessmentTypeId = varchar("assessment_type_id", 36)
        .references(AssessmentTypesTable.id, onDelete = ReferenceOption.CASCADE)

    val fieldKey = varchar("field_key", 100)
    val label = varchar("label", 255)
    val fieldType = varchar("fieldType", 50)
    val options = text("options").nullable()
    val isRequired = bool("is_required").default(false)
    val order = integer("display_order").default(0)
    val section = varchar("section", 100).nullable()
    val defaultTextIfEmpty = text("default_text_if_empty").nullable()

    override val primaryKey = PrimaryKey(id)
    init {
        index(true, assessmentTypeId, fieldKey)
    }
}

object AssessmentFieldValuesTable: Table("assessment_field_values") {
    val id = varchar("id", 36)
    val projectId = varchar("projectId", 36)
        .references(AssessmentProjectsTable.id, onDelete = ReferenceOption.CASCADE)
    val fieldDefinitionId = varchar("field_definition_id", 36)
        .references(AssessmentFieldDefinitionsTable.id, onDelete = ReferenceOption.RESTRICT)

    val singleValue = text("single_value").nullable()
    val multipleValues = text("multiple_values").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(true, projectId, fieldDefinitionId)
    }
}

// TYPE TABLES

object AssessmentTypesTable: Table("assessment_types") {
    val id = varchar("id", 36)
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").nullable()
    val templateFileNames = text("template_file_names").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AssessorTypeAssignmentsTable: Table("assessment_type_assignments") {
    val assessorId = varchar("assessor_id", 36)
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    val assessmentTypeId = varchar("assessment_type_id", 36)
        .references(AssessmentTypesTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(
        assessorId,
        assessmentTypeId,
        name = "PK_AssessorTypeAssignments"
    )
}

object AssessmentProjectDocumentsTable: Table("assessment_project_documents") {
    val id = varchar("id", 36)
    val projectId = varchar("project_id", 36)
        .references(AssessmentProjectsTable.id, onDelete = ReferenceOption.CASCADE)
    val documentType = varchar("document_type", 50)
    val originalFileName = varchar("original_file_name", 255)
    val storedFilePath = text("stored_file_path")
    val uploadTimestamp = long("upload_timestamp")
    val versionNumber = integer("version_number").default(1)

    override val primaryKey = PrimaryKey(id)
}