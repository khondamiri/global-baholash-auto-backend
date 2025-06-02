package com.db

import com.common.User
import io.ktor.server.engine.applicationEnvironment
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.*
import java.util.UUID

class UserRepository {
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

            User(userId, username, email, role)
        }
    }

    suspend fun findUserByUsername(username: String): UserWithPasswordHash? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.username eq username }
                .map { row ->
                    UserWithPasswordHash(
                        User(
                            row[UsersTable.id],
                            row[UsersTable.username],
                            row[UsersTable.email],
                            row[UsersTable.role]
                        ),
                        row[UsersTable.passwordHash]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun findUserByEmail(email: String): UserWithPasswordHash? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.email eq email }
                .map { row ->
                    UserWithPasswordHash(
                        User(
                            row[UsersTable.id],
                            row[UsersTable.username],
                            row[UsersTable.email],
                            row[UsersTable.role]
                        ),
                        row[UsersTable.passwordHash]
                    )
                }
                .singleOrNull()
        }
    }
}

data class UserWithPasswordHash(val user: User, val passwordHash: String)