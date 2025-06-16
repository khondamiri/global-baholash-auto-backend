package com.globalbaholash.routing

import com.globalbaholash.common.AuthResponse
import com.globalbaholash.common.NewPasswordRequest
import com.globalbaholash.common.PasswordResetRequest
import com.globalbaholash.common.RegistrationResponse
import com.globalbaholash.common.UserCredentialsRequest
import com.globalbaholash.common.UserLoginCredentialsRequest
import com.globalbaholash.db.UserRepository
import com.globalbaholash.plugins.generateToken
import com.globalbaholash.services.AuthService
import com.globalbaholash.services.EmailService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.authRoutes(
    userRepository: UserRepository,
    authService: AuthService,
    emailService: EmailService
) {
    route("/auth") {

        // REGISTRATION AND VERIFICATION

        post("/register") {
            try {
                val request = call.receive<UserCredentialsRequest>()

                // basic input validation

                if (request.username.isBlank() || request.password.isBlank() || request.email.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Username or password cannot be empty.")
                    )
                    return@post
                }

                if (!request.email.contains("@") || !request.email.contains(".")) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid email format")
                    )
                    return@post
                }

                if (request.password.length < 6) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Password cannot be shorter than 6 characters."))
                    return@post
                }

                if (userRepository.findUserByUsername(request.username) != null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Username already  exists")
                    )
                    return@post
                }

                    if (userRepository.findUserByEmail(request.email) != null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Email already exist")
                    )
                    return@post
                }

                // user creation


                val hashedPassword = authService.hashPassword(request.password)
                val newUser = userRepository.createUser(
                    request.username,
                    request.email,
                    hashedPassword,
                    "ASSESSOR"
                )

                if (newUser != null) {
                    val verificationToken: String = UUID.randomUUID().toString()
                    val successStoringToken = userRepository
                        .storeEmailVerificationToken(newUser.id, verificationToken)

                    if (successStoringToken) {
                        val verificationBaseUrl =
                            application.environment.config.propertyOrNull("server.baseUrl")?.getString()
                        val verificationLink = "$verificationBaseUrl/auth/verify-email?token=$verificationToken"

                        val emailBody = emailService
                            .createEmailVerificationBody(newUser.username, verificationLink)
                        val emailSent = emailService
                            .sendEmail(newUser.email, "Verify Your Global Baholash Account", emailBody)

                        if (!emailSent) {
                            application.log.warn("Failed to send verification email to ${newUser.email} for user ${newUser.id}")
                        }
                    } else {
                        application.log.error("Failed to store verification token for user ${newUser.id}")
                    }

//                    val token = generateToken(application, newUser)

                    call.respond(
                        HttpStatusCode.Created,
                        RegistrationResponse(
                            "Registration successful. Please check your email...",
                            newUser
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Could not create user. Please try again.")
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
                    mapOf("error" to "An unexpected error occurred.")
                )
            }
        } // [*]

        get("/verify-email") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Token is missing.")
                )
                return@get
            }

            val user = userRepository.findUserByEmailVerificationToken(token)
            if (user == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or expired verification token")
                )
                return@get
            }

            if (user.isEmailVerified) {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Email already verified.")
                )
                return@get
            }

            val success = userRepository.verifyUserEmail(user.id)
            if (success) {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Email verified.")
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to verify email. Please contact support.")
                )
            }
        } // [*]

        // LOGIN AND PASSWORD RESET

        post("/login") {
            try {
                val request = call.receive<UserLoginCredentialsRequest>()

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
                    if (!userWithHash.user.isActive) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Account is deactivated.")
                        )
                        return@post
                    }

                    if (!userWithHash.user.isEmailVerified) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Email is not verified")
                        )
                        return@post
                    }


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
        } // [*]

        post("/request-password-reset") {
            try {
                val request = call.receive<PasswordResetRequest>()

                if (request.email.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Email cannot be empty.")
                    )
                    return@post
                }

                val user = userRepository.findUserByEmailToResetPassword(request.email)

                if (user != null && user.isActive && user.isEmailVerified) {
                    val resetToken = UUID.randomUUID().toString()
                    val expiryTimestamp = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour

                    userRepository.storePasswordResetToken(user.id, resetToken, expiryTimestamp)

                    val baseUrl = application.environment.config.propertyOrNull("server.baseUrl")?.getString()
                    // IMPORTANT: The link should ideally point to a page on FRONTEND application
                    // that will then allow the user to enter a new password and submit it to the /reset-password API.
                    // In a real app, this would be e.g., https://your-frontend-app.com/reset-password?token=THE_TOKEN

                    val resetLink = "${baseUrl}/auth/set-new-password-page?token=${resetToken}"

                    val emailBody = emailService.createPasswordResetBody(user.username, resetLink)
                    val emailSent = emailService.sendEmail(user.email, "Password Reset Request", emailBody)

                    if (!emailSent) {
                        application.log.warn("Failed to send password reset to ${user.email} for user.id ${user.id}")
                    }
                } else {
                    application.log.info("Password reset requested for non-existent, inactive, unverified email.")
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "If your email is registered, you will receive a password reset link shortly.")
                )
            } catch (e: ContentTransformationException) {
                application.log.error("Password reset request: Bad request data: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request data."))
            } catch (e: Exception) {
                application.log.error("Password reset request: Unexpected error.", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Unexpected error occurred."))
            }
        } // [*]

        post("/reset-password") {
            try {
                val request = call.receive<NewPasswordRequest>()

                if (request.token.isBlank() || request.newPassword.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Token or new password cannot be empty.")
                    )
                    return@post
                }

                if (request.newPassword.length < 6) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "New password must be at least 6 characters long.")
                    )
                    return@post
                }

                val user = userRepository.findUserByPasswordResetToken(request.token)

                if (user == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid or expired password reset token.")
                    )
                    return@post
                }

                val newHashedPassword = authService.hashPassword(request.newPassword)
                val success = userRepository.updatePassword(user.id, newHashedPassword)

                if (success) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("error" to "Password has been reset successfully.")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to update password.")
                    )
                }
            } catch (e: ContentTransformationException) {
                application.log.error("Password reset request: Bad request data: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request data."))
            } catch (e: Exception) {
                application.log.error("Password reset request: Unexpected error.", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Unexpected error occurred."))
            }
        } // [*]
    }
}