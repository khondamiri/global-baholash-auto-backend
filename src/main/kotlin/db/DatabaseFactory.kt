package com.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        Database.connect(hikari(config))
        transaction {
            SchemaUtils.create(
                UsersTable,
                AssessmentTypesTable,
                AssessmentFieldDefinitionsTable,
                AssessmentProjectsTable,
                AssessmentFieldValuesTable
            )
        }
    }

    private fun hikari(config: ApplicationConfig) : HikariDataSource {
        val hikariConfig = HikariConfig()

        hikariConfig.driverClassName = config.property("database.driverClassName").getString()
        hikariConfig.jdbcUrl = config.property("database.jdbcUrl").getString()
        hikariConfig.username = config.property("database.username").getString()
        hikariConfig.password = config.property("database.password").getString()
        hikariConfig.maximumPoolSize = config.property("database.maxPoolSize").getString().toInt()
        hikariConfig.isAutoCommit = config.property("database.autoCommit").getString().toBoolean()
        hikariConfig.transactionIsolation = config.property("database.transactionIsolation").getString()
        hikariConfig.validate()

        return HikariDataSource(hikariConfig)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            block()
        }
}