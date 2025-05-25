package com.services

import org.mindrot.jbcrypt.BCrypt

class AuthService {
    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            BCrypt.checkpw(password, storedHash)
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}