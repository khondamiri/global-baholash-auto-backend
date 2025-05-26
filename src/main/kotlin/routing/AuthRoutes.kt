package com.routing

import com.common.AuthResponse
import com.common.UserCredentialsRequest
import com.db.UserRepository
import com.plugins.generateToken
import com.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(userRepository: UserRepository, authService: AuthService) {

    route("/auth") {

        post("/register") {
            try {
                val request = call.receive<UserCredentialsRequest>()

                // basic input validation

                if (request.username.isBlank() || request.password.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Username or password cannot be empty.")
                    )
                    return@post
                }

                if (request.password.length < 6) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Password cannot be shorter than 6 characters."))
                    return@post
                }

                // existing user check

                val existingUser = userRepository.findUserByUsername(request.username)

                if (existingUser != null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Username already exists."))
                    return@post
                }

                // user creation

                val hashedPassword = authService.hashPassword(request.password)
                val newUser = userRepository.createUser(
                    request.username,
                    hashedPassword,
                    "ASSESSOR"
                )

                if (newUser != null) {
                    val token = generateToken(application, newUser)

                    call.respond(HttpStatusCode.Created, AuthResponse(newUser, token))
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Could not create user. Please, try again later.")
                    )
                }

            } catch (e: ContentTransformationException) {
                application.log.warn("Registration: Bad request data: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request format or data.")
                )
            } catch (e: Exception) {
                application.log.warn("Registration: Unexpected error.", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "An unexpected error ocurred.")
                )
            }
        }

        post("/login") {
            try {
                val request = call.receive<UserCredentialsRequest>()

                // basic input validation

                if (request.username.isBlank() || request.password.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Username or password cannot be empty.")
                    )
                    return@post
                }

                // searching for user

                val userWithHash = userRepository.findUserByUsername(request.username)

                if (userWithHash != null &&
                    authService.verifyPassword(
                        request.password,
                        userWithHash.passwordHash)
                ) {
                    val token = generateToken(application, userWithHash.user)

                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(userWithHash.user, token)
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid username or password")
                    )
                }
            } catch (e: ContentTransformationException) {
                application.log.warn("Registration: Bad request data: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request format or data.")
                )
            } catch (e: Exception) {
                application.log.warn("Registration: Unexpected error.", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "An unexpected error ocurred.")
                )
            }
        }
    }
}