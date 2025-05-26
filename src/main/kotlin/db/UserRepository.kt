package com.db

import com.common.User
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.*
import java.util.UUID

class UserRepository {
    suspend fun createUser(username: String, passwordHash: String, role: String): User? {
        val userId = UUID.randomUUID().toString()

        return DatabaseFactory.dbQuery {
            val existingUser = UsersTable.selectAll().where { UsersTable.username eq username }.singleOrNull()

            if (existingUser != null) {
                return@dbQuery null
            }

            UsersTable.insert {
                it[UsersTable.id] = userId
                it[UsersTable.username] = username
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = role
            }

            User(userId, username, role)
        }
    }

    suspend fun findUserByUsername(username: String): UserWithPasswordHash? {
        return DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.username eq username }
                .map { row ->
                    UserWithPasswordHash(
                        User(
                            id = row[UsersTable.id],
                            username = row[UsersTable.username],
                            role = row[UsersTable.role]
                        ),
                        passwordHash = row[UsersTable.passwordHash]
                    )
                }
                .singleOrNull()
        }
    }
}

data class UserWithPasswordHash(val user: User, val passwordHash: String)