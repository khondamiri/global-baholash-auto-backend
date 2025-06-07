package com.globalbaholash.db

import com.globalbaholash.common.User
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.*
import java.util.UUID
import javax.xml.crypto.Data
import kotlin.math.log

data class UserWithPasswordHash(val user: User, val passwordHash: String)

class UserRepository () {
    suspend fun createUser(username: String, email: String, passwordHash: String, role: String): User? {
        val userId = UUID.randomUUID().toString()

        return DatabaseFactory.dbQuery {
            val existingUserByUsername = UsersTable.selectAll().where { UsersTable.username eq username }.singleOrNull()
            if (existingUserByUsername != null) {
                return@dbQuery null
            }

            val existingUserByEmail = UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
            if (existingUserByEmail != null) {
                return@dbQuery null
            }

            UsersTable.insert {
                it[UsersTable.id] = userId
                it[UsersTable.username] = username
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = role
            }

            UsersTable.selectAll().where { UsersTable.id eq userId }.map { mapRowToUser(it) }.singleOrNull()
        }
    }

    suspend fun findUserByUsername(username: String): UserWithPasswordHash? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.username eq username }
                .map { mapRowToUserWithPasswordHash(it) }
                .singleOrNull()
        }
    }

    suspend fun findUserByEmail(email: String): UserWithPasswordHash? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.email eq email }
                .map { mapRowToUserWithPasswordHash(it) }
                .singleOrNull()

        }
    }

    suspend fun findUserByEmailToResetPassword(email: String): User? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.email eq email }
                .map { mapRowToUser(it) }
                .singleOrNull()
        }
    }

    suspend fun findUserById(id: String): User? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.id eq id }
                .map { mapRowToUser(it) }
                .singleOrNull()
        }
    }



    suspend fun storeEmailVerificationToken(userId: String, token: String): Boolean {
        return DatabaseFactory.dbQuery {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[verificationToken] = token
            } > 0
        }
    }

    suspend fun findUserByEmailVerificationToken(token: String): User? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.verificationToken eq token }
                .map { mapRowToUser(it) }
                .singleOrNull()
        }
    }

    suspend fun storePasswordResetToken(userId: String, token: String, expiryTimestamp: Long): Boolean {
        return DatabaseFactory.dbQuery {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[passwordResetToken] = token
                it[passwordResetTokenExpiry] = expiryTimestamp
            }
        } > 0
    }

    suspend fun findUserByPasswordResetToken(token: String): User? {
        return DatabaseFactory.dbQuery {
            val now = System.currentTimeMillis()
            UsersTable.selectAll().where{
                (UsersTable.passwordResetToken eq token) and (UsersTable.passwordResetTokenExpiry greaterEq now)
            }
                .map { mapRowToUser(it) }
                .singleOrNull()
        }
    }

    suspend fun updatePassword(userId: String, newPasswordHash: String): Boolean {
        return DatabaseFactory.dbQuery {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[passwordHash] = newPasswordHash
                it[passwordResetToken] = null
                it[passwordResetTokenExpiry] = null
            } > 0
        }
    }

    suspend fun verifyUserEmail(userId: String): Boolean {
        return DatabaseFactory.dbQuery {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[isEmailVerified] = true
                it[verificationToken] = null
            } > 0
        }
    }

    // ADMIN CONTROLS

    suspend fun getAllAssessors(): List<User> {
        return DatabaseFactory.dbQuery {
            UsersTable
                .selectAll().where { UsersTable.role eq "ASSESSOR" }
                .orderBy(UsersTable.username, SortOrder.ASC)
                .map { mapRowToUser(it) }
        }
    }

    suspend fun setUserCredits(userId: String, newCreditCount: Int): User? {
        if (newCreditCount < 0) {
            return null
        }

        return DatabaseFactory.dbQuery {
            val updatedRow = UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.credits] = newCreditCount
            }
            if (updatedRow > 0) {
                UsersTable.selectAll().where { UsersTable.id eq userId }
                    .map { mapRowToUser(it) }.singleOrNull()
            } else {
                null
            }
        }
    }

    suspend fun setUserActiveStatus(userId: String, isActive: Boolean): Boolean {
        return DatabaseFactory.dbQuery {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.isActive] = isActive
            } > 0
        }
    }

    /* DEPRECATED

suspend fun updateUserEmailVerificationStatus(userId: String, isVerified: Boolean): Boolean {
    return DatabaseFactory.dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[isEmailVerified] = isVerified
            if (isVerified) {
                it[verificationToken] = null
            }
        } > 0
    }
}

suspend fun updateUserCredits(userId: String, creditsChange: Int, isAbsolute: Boolean = false): User? {
    return DatabaseFactory.dbQuery {
        val currentUser = UsersTable.selectAll().where { UsersTable.id eq userId }
            .map { mapRowToUser(it) }
            .singleOrNull()

        currentUser?.let { user ->
            val newCredits = if (isAbsolute) creditsChange else user.credits + creditsChange
            if (newCredits < 0) {
                return@dbQuery null
            }
            val updatedRows = UsersTable.update({ UsersTable.id eq userId }) {
                it[credits] = newCredits
            }
            if (updatedRows > 0) {
                user.copy(credits = newCredits)
            } else {
                null
            }
        }
    }
}

suspend fun updateUserActiveStatus(userId: String, newIsActive: Boolean): Boolean {
    return DatabaseFactory.dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[isActive] = newIsActive
        } > 0
    }
}
*/

} // END OF USER REPOSITORY

// HELPER FUNCTIONS

private fun mapRowToUser(row: ResultRow): User {
    return User(
        row[UsersTable.id],
        row[UsersTable.username],
        row[UsersTable.role],
        row[UsersTable.email],
        row[UsersTable.credits],
        row[UsersTable.isActive],
        row[UsersTable.isEmailVerified],
    )
}

private fun mapRowToUserWithPasswordHash(row: ResultRow): UserWithPasswordHash {
    return UserWithPasswordHash(
        mapRowToUser(row),
        row[UsersTable.passwordHash]
    )
}