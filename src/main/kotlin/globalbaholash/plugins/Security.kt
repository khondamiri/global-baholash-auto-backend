package com.globalbaholash.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.globalbaholash.common.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.Date

fun Application.configureSecurity() {

    val audience = environment.config.property("jwt.audience").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val myRealm = environment.config.property("jwt.realm").getString()
    val secret = environment.config.property("jwt.secret").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = myRealm

            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val username = credential.payload.getClaim("username").asString()
                val role = credential.payload.getClaim("role").asString()

                if (userId != null && username != null && role != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

fun generateToken(application: Application, user: User): String {
    val secret = application.environment.config.property("jwt.secret").getString()
    val issuer = application.environment.config.property("jwt.issuer").getString()
    val audience = application.environment.config.property("jwt.audience").getString()
    val expirationTime = application.environment.config.property("jwt.expirationTimeMs").getString().toLong()

    return JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("userId", user.id)
        .withClaim("username", user.username)
        .withClaim("role", user.role)
        .withExpiresAt(Date(System.currentTimeMillis() + expirationTime))
        .sign(Algorithm.HMAC256(secret))
}
