package com.globalbaholash.common

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val role: String,
    val email: String,
    val credits: Int,
    val isActive: Boolean,
    val isEmailVerified: Boolean
)

// REGISTRATION AND LOGIN

@Serializable
data class UserCredentialsRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class UserLoginCredentialsRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val user: User,
    val token: String
)

@Serializable
data class RegistrationResponse(
    val message: String,
    val user: User?
)

@Serializable
data class PasswordResetRequest(
    val email: String
)

@Serializable
data class NewPasswordRequest(
    val token: String,
    val newPassword: String
)

// ADMIN FUNCTIONALITY

@Serializable
data class UpdateAssessorCreditRequest(
    val credits: Int
)

@Serializable
data class UpdateAssessorStatusRequest(
    val isActive: Boolean
)

// --- Assessment types and field data ---

@Serializable
enum class FieldDataType {
    TEXT,
    TEXT_AREA,
    NUMBER,
    DATE,
    BOOLEAN,
    ENUM_SINGLE,
    ENUM_MULTI
}

@Serializable
data class AssessmentFieldDefinition(
    val id: String,
    val assessmentTypeId: String,
    val fieldKey: String,
    val label: String,
    val fieldType: FieldDataType,
    val options: List<String>? = null,
    val isRequired: Boolean = false,
    val order: Int = 0,
    val section: String? = null
)

@Serializable
data class AssessmentType(
    val id: String,
    val name: String,
    val description: String? = null,
    val fieldDefinitions: List<AssessmentFieldDefinition>? = null
)

// --- Assessment project & their actual field values ---

@Serializable
enum class ProjectStatus {
    ACTIVE,
    FINISHED
}

@Serializable
data class AssessmentFieldValue(
    val fieldDefinitionId: String, // the same as AssessmentFieldDefinition.id
    val value: String? = null,
    val values: List<String>? = null
) {
    constructor(fieldDefinitionId: String, singleValue: String?) :
            this(fieldDefinitionId, singleValue, null)
}

@Serializable
data class AssessmentProject(
    val id: String,
    val displayName: String,
    val assessmentTypeId: String,
    val assessorId: String,
    var status: ProjectStatus = ProjectStatus.ACTIVE,
    var creationTimestamp: Long = System.currentTimeMillis(),
    var lastModifiedTimestamp:  Long = System.currentTimeMillis(),
    val fieldValues: List<AssessmentFieldValue>,
    val publicAccessId: String? = null,
    val documentStoragePath: String? = null,
    val qrCodeData: String? = null
)

// --- Data transfer objects (DTO) for API requests/responses ---

@Serializable
data class CreateAssessmentRequest(
    val displayName: String,
    val assessmentTypeId: String,
    val fieldValues: List<AssessmentFieldValue>
//    val creationTimestamp: Long
)

@Serializable
data class PublicDocumentView(
    val projectName: String,
    val assessmentTypeName: String,
    val generatedDate: Long,
    val downloadLink: String
)