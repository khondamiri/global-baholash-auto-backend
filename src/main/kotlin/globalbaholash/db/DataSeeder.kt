package com.globalbaholash.db

import com.globalbaholash.common.FieldDataType
import com.globalbaholash.services.AuthService
import io.ktor.util.logging.Logger
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import kotlin.collections.mapOf

suspend fun stdTypeSeeder(log: Logger) {
    DatabaseFactory.dbQuery {
        val stdTypeName = "Standard Avtomobil Avariyasi"

        if (AssessmentTypesTable.selectAll().where { AssessmentTypesTable.name eq stdTypeName }.empty()) {
            log.info("====== Seeding standard car collision assessment type... ======")

            val stdTypeId = "std-car-collision-v1"

            AssessmentTypesTable.insert {
                it[id] = stdTypeId
                it[name] = stdTypeName
                it[description] = "Avariya natijasida yetkazilgan zararni hisoblash"
            }
            log.info("====== Inserted Assessment Type: $stdTypeName (ID: $stdTypeId) ======")

            // OPTIONS
            val providedDocType = listOf("Passport", "Haydovchi guvohnomasi")
            val carBodyType = listOf("Sedan", "Avtobus")
            val fuelType = listOf("Benzin", "Metan", "Propan", "Salyarka")
            val transmissionType = listOf("Avtomatik", "Mexanik")
            val carTechnicalCondition = listOf("Soz", "Nosoz")

            // Json OPTIONS
            val providedDocTypeJson = Json.encodeToString(providedDocType)
            val carBodyTypeJson = Json.encodeToString(carBodyType)
            val fuelTypeJson = Json.encodeToString(fuelType)
            val transmissionTypeJson = Json.encodeToString(transmissionType)
            val carTechnicalConditionJson = Json.encodeToString(carTechnicalCondition)

            val fieldsToSeed = listOf(

                // ==============================
                //             MAIN
                // ==============================

                mapOf(
                    "key" to "{{RESIDENTIAL_ADDRESS}}", "label" to "Yashash manzil",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 0,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{ORDERING_PARTY}}", "label" to "Buyurtmachi",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 1,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{CAR_OWNER}}", "label" to "Avtomobil egasi",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 2,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{PROVIDED_DOC_TYPE}}", "label" to "Hujjat turi",
                    "type" to FieldDataType.ENUM_SINGLE.name, "section" to "Asosiy", "req" to true, "ord" to 3,
                    "opts" to providedDocTypeJson
                ),
                mapOf(
                    "key" to "{{PROVIDED_DOC_SERIAL_NUMBER}}", "label" to "Hujjat seria raqami",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 4,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{PROVIDED_DOC_GIVEN_DATE}}", "label" to "Hujjat berilgan sana",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 5,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{PROVIDED_DOC_GIVEN_BY}}", "label" to "Hujjat kim tomonidan berilgan",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 6,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{CAR_MODEL}}", "label" to "Avtomobil modeli",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 7,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{CAR_REGISTRATION_PLATE}}", "label" to "Avtomobil davlat raqami",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 8,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{CAR_MANUFACTURING_YEAR}}", "label" to "Avtomobil ishlab chiqarilgan yil",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 9,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DATE_REPORT_PREPARATION}}", "label" to "Hisobot tayyorlangan sana",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 10,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DATE_INSPECTION_ACT}}", "label" to "Avtomobil ko'rik sanasi",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 11,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{INSPECTION_PLACE}}", "label" to "Ko'rik jarima maydoni",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 12,
                    "opts" to null
                ),

                // ==============================
                //          TECHNICAL
                // ==============================

                mapOf(
                    "key" to "{{CAR_BODY_TYPE}}", "label" to "Kuzov turi",
                    "type" to FieldDataType.ENUM_SINGLE.name, "section" to "Texnik", "req" to true, "ord" to 13,
                    "opts" to carBodyTypeJson
                ),
                mapOf(
                    "key" to "{{CAR_BODY_NUMBER}}", "label" to "Kuzov raqami",
                    "type" to FieldDataType.TEXT.name, "section" to "Texnik", "req" to false, "ord" to 14,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{ENGINE_NUMBER}}", "label" to "Dvigatel raqami",
                    "type" to FieldDataType.TEXT.name, "section" to "Texnik", "req" to false, "ord" to 15,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{CHASSIS_NUMBER}}", "label" to "Shassi raqami",
                    "type" to FieldDataType.TEXT.name, "section" to "Texnik", "req" to false, "ord" to 16,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{FUEL_TYPE}}", "label" to "Yoqilg'i turi",
                    "type" to FieldDataType.ENUM_SINGLE.name, "section" to "Texnik", "req" to true, "ord" to 17,
                    "opts" to fuelTypeJson
                ),
                mapOf(
                    "key" to "{{DISTANCE_TRAVELLED}}", "label" to "Bo'sib o'tgan yo'l",
                    "type" to FieldDataType.NUMBER.name, "section" to "Texnik", "req" to true, "ord" to 18,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{CAR_COLOR}}", "label" to "Rangi",
                    "type" to FieldDataType.TEXT.name, "section" to "Texnik", "req" to true, "ord" to 19,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{TRANSMISSION_TYPE}}", "label" to "Uzatma qutisi turi",
                    "type" to FieldDataType.ENUM_SINGLE.name, "section" to "Texnik", "req" to true, "ord" to 20,
                    "opts" to transmissionTypeJson
                ),
                mapOf(
                    "key" to "{{ENGINE_POWER}}", "label" to "Dvigatel quvvati",
                    "type" to FieldDataType.NUMBER.name, "section" to "Texnik", "req" to true, "ord" to 21,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{VEHICLE_REGISTRATION_CERTIFICATE_SERIAL_NUMBER}}", "label" to "Texnik passport raqami",
                    "type" to FieldDataType.TEXT.name, "section" to "Texnik", "req" to true, "ord" to 22,
                    "opts" to null
                ),

                // ==============================
                //            DAMAGE
                // ==============================

                mapOf(
                    "key" to "{{DAMAGE_CAR_BODY}}", "label" to "Kuzovga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 23,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_ENGINE}}", "label" to "Dvigatelga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 24,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_TRANSMISSION}}", "label" to "Uzatma qutisiga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 25,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_STEERING_SYS}}", "label" to "Rul boshqarish qismiga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 26,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_REAR_AXLE}}", "label" to "Orqa ko'prikka yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 27,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_INTERIOR}}", "label" to "Salonga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 28,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_WHEELS}}", "label" to "G'ildiraklarga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 29,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_COOLING_RADIATOR}}", "label" to "Suv sovutish radiatoriga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 30,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_SEATS}}", "label" to "O'rindiqlarga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 31,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_RUNNING_GEAR}}", "label" to "Yurish qismiga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 32,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_ELECTRICAL_SYS}}", "label" to "Elektr qismiga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 33,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_WINDOWS}}", "label" to "Oynalarga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 34,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_BATTERY}}", "label" to "Akkumulyatorga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 35,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_SHOCK_ABSORBERS}}", "label" to "Amortizatorlarga yetkazilgan zarar",
                    "type" to FieldDataType.TEXT_AREA.name, "section" to "Zarar", "req" to false, "ord" to 36,
                    "opts" to null
                ),
                mapOf(
                    "key" to "{{DAMAGE_TECHNICAL CONDITION}}", "label" to "Avtomobil texnik holati",
                    "type" to FieldDataType.ENUM_SINGLE.name, "section" to "Zarar", "req" to true, "ord" to 37,
                    "opts" to carTechnicalConditionJson
                )
            )

            fieldsToSeed.forEach { fieldData ->
                AssessmentFieldDefinitionsTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[assessmentTypeId] = stdTypeId as String
                    it[fieldKey] = fieldData["key"] as String
                    it[label] = fieldData["label"] as String
                    it[fieldType] = fieldData["type"] as String
                    it[isRequired] = fieldData["req"] as Boolean
                    it[order] = fieldData["ord"] as Int
                    it[section] = fieldData["section"] as String

                    (fieldData["opts"] as String?)?.let { optJson ->
                        it[options] = optJson
                    }
                }
                log.info("Inserted FieldDefinitions: ${fieldData["label"]}.")
            }
            log.info("====== Finished inserting $stdTypeName ======")
        } else {
            log.info("====== $stdTypeName already exists ======")
        }
    }
}

suspend fun testTypeSeeder(log: Logger) {
    DatabaseFactory.dbQuery {
        val testTypeName = "test-type"

        if (AssessmentTypesTable.selectAll().where{ AssessmentTypesTable.name eq testTypeName }.empty()) {
            val testTypeId = "test-type-v1"
            log.info("====== Seeding test assessment type... ======")

            AssessmentTypesTable.insert {
                it[id] = testTypeId
                it[name] = testTypeName
                it[description] = "test"
            }
            log.info("====== Inserted Assessment type: $testTypeName ($testTypeId) ======")

            val fields = listOf(
                mapOf(
                    "key" to "{{RESIDENTIAL_ADDRESS}}", "label" to "Yashash manzil",
                    "type" to FieldDataType.TEXT.name, "section" to "Asosiy", "req" to true, "ord" to 0,
                    "opts" to null
                )
            )

            fields.forEach { fieldData ->
                AssessmentFieldDefinitionsTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[assessmentTypeId] = testTypeId as String
                    it[fieldKey] = fieldData["key"] as String
                    it[label] = fieldData["label"] as String
                    it[fieldType] = fieldData["type"] as String
                    it[isRequired] = fieldData["req"] as Boolean
                    it[order] = fieldData["ord"] as Int
                    it[section] = fieldData["section"] as String

                    (fieldData["opts"] as String?)?.let { optJson ->
                        it[options] = optJson
                    }
                }
                log.info("Inserted FieldDefinitions: ${fieldData["label"]}")
            }
            log.info("====== Finished seeding $testTypeName ======")
        } else {
            log.info("====== ${testTypeName}TypeName already exists ======")
        }
    }
}

suspend fun initAdmin(log: Logger) {
    DatabaseFactory.dbQuery {
        val adminUsername = "admin_khondamiri"
        if (UsersTable.selectAll().where { UsersTable.username eq adminUsername }.empty()) {
            val adminEmail = "khondamir.ismailow@gmail.com"
            val adminPasswordHash = AuthService().hashPassword(",Jwdjpamtg1")

            UsersTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[username] = adminUsername
                it[email] = adminEmail
                it[passwordHash] = adminPasswordHash
                it[role] = "ADMIN"
                it[credits] = 999999
                it[isActive] = true
                it[isEmailVerified] = true
            }
            log.info("====== admin initialized: $adminUsername ======")
        } else {
            log.info("====== admin $adminUsername already initialized ======")
        }
    }
}