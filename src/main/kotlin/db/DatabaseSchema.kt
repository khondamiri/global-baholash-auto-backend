package com.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object UsersTable: Table("users") {
    val id = varchar("id", 36)
    //       ^                 ^            ^
    //       defines a column| column name| input max len

    val username = varchar("username", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50)
    override val primaryKey = PrimaryKey(id)
}

object AssessmentTypesTable: Table("assessment_types") {
    val id = varchar("id", 36)
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").nullable()
    override val primaryKey = PrimaryKey(id)
}

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

    override val primaryKey = PrimaryKey(id)
    init {
        index(true, assessmentTypeId, fieldKey)
    }
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

