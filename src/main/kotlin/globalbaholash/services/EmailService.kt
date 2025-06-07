package com.globalbaholash.services

import com.auth0.jwt.interfaces.Verification
import io.ktor.server.application.ApplicationEnvironment
import jakarta.mail.*
import jakarta.mail.Authenticator
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Date
import java.util.Properties

class EmailService(private val environment: ApplicationEnvironment) {
    private val smtpHost = environment.config.property("email.smtpHost").getString()
    private val smtpPort = environment.config.property("email.smtpPort").getString().toIntOrNull() ?: 587
    private val smtpUsername = environment.config.property("email.smtpUsername").getString()
    private val smtpPassword = environment.config.property("email.smtpPassword").getString()
    private val fromAddress = environment.config.property("email.fromAddress").getString()
    private val fromName = environment.config.property("email.fromName").getString()
    private val enableTls = environment.config.property("email.enableTls").getString().toBoolean()
    private val enableSsl = environment.config.property("email.enableSsl").getString().toBoolean()

    init { // Add an init block for checking
        environment.log.info("EmailService Initialized with SMTP Host: $smtpHost, Port: $smtpPort")
    }

    fun sendEmail(to: String, subject: String, bodyHtml: String): Boolean {
        environment.log.info("Attempting to send email via: $smtpHost:$smtpPort")
        val props = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort.toString())
            put("mail.smtp.auth", "true")

            if (enableTls) {
                put("mail.smtp.starttls.enable", "true")
            }
            if (enableSsl) {
                put("mail.smtp.ssl.enable", "true")
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(smtpUsername, smtpPassword)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromAddress, fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, "UTF-8")
                setContent(bodyHtml, "text/html; charset=utf-8")
                setSentDate(Date())
            }

            Transport.send(message)
            environment.log.info("Email sent successfully to $to, with subject '$subject'")

            return true
        } catch (e: MessagingException) {
            environment.log.info("Error sending email to $to: ${e.message}", e)
            return false
        } catch (e: Exception) {
            environment.log.info("Unexpected error during email sending to $to: ${e.message}", e)
            return false
        }
    }

    fun createEmailVerificationBody(username: String, verificationLink: String): String {
        return """
            <html>
            <body>
                <h2>Hello, $username!</h2>
                <p>Thank you for registering. Please verify your email address by clicking the link below:</p>
                <p><a href="$verificationLink"> Verify Email Address</a></p>
                <p>If you did not register for an account, please ignore this email</p>
                <br>
                <p>Thanks,<br>Global Baholash Team</p>
            </body>
            </html>
            """.trimIndent()
    }

    fun createPasswordResetBody(username: String, resetLink: String): String {
        return """
            <html>
            <body>
                <h2>Password Reset Request for Global Baholash account</h2>
                <p>Hello, $username</p>
                <p>We received a request to reset your password. If you made this request, please click the link below to set a new password:</p>
                <p><a href="$resetLink">Reset Password</a></p>
                <p>This link will expire in 10 minutes</p>
                <p>If you did not request a password reset, please ignore this email or contact support if you have concerns</p>
                <br>
                <p>Thanks,<br>Global Baholash Team</p>
            </body>
            </html>
            """.trimIndent()
    }
}