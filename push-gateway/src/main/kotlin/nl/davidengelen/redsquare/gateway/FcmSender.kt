package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.GoogleCredentials

/** External data-only send. Acceptance is not a device receipt; caller owns durable retries. */
class FcmSender(project: String, credentials: GoogleCredentials, localOrigin: String? = null) {
    sealed interface Result {
        data class Accepted(val name: String) : Result
        data object Unregistered : Result
        data class Retry(val afterMillis: Long) : Result
        data class Rejected(val status: Int) : Result
    }
    fun send(fid: String, wake: String, expiresAt: Long): Result = Result.Rejected(501)
}
