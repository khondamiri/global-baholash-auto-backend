package globalbaholash.services

import com.globalbaholash.db.AssessmentRepository
import com.globalbaholash.db.UserRepository
import io.ktor.server.application.Application

class ReportService (
    private val application: Application,
    private val assessmentRepository: AssessmentRepository,
    private val userRepository: UserRepository
) {

}