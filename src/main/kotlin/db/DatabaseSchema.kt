package com.db

import org.jetbrains.exposed.sql.Table

object UsersTable: Table("users") {
    val id = varchar("id", 36)
    //       ^                 ^            ^
    //       defines a column| column name| input max len

    val username = varchar("username", 255)
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50)
    override val primaryKey = PrimaryKey(id)
}