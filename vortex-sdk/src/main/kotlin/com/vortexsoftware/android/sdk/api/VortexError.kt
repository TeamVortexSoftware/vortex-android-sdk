package com.vortexsoftware.android.sdk.api

/**
 * Errors that can occur when using the Vortex SDK
 */
sealed class VortexError : Exception() {
    
    /**
     * Network-related errors (no connection, timeout, etc.)
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : VortexError()
    
    /**
     * Server returned an error response
     */
    data class ServerError(
        val statusCode: Int,
        override val message: String
    ) : VortexError()
    
    /**
     * Failed to parse the server response
     */
    data class DecodingError(
        override val message: String,
        override val cause: Throwable? = null
    ) : VortexError()
    
    /**
     * Invalid request parameters
     */
    data class InvalidRequest(
        override val message: String
    ) : VortexError()
    
    /**
     * Authentication/authorization error
     */
    data class Unauthorized(
        override val message: String = "Authentication required or token expired"
    ) : VortexError()
    
    /**
     * Resource not found
     */
    data class NotFound(
        override val message: String = "Resource not found"
    ) : VortexError()
    
    /**
     * Unknown or unexpected error
     */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : VortexError()
    
    companion object {
        /**
         * Create appropriate VortexError from HTTP status code
         */
        fun fromHttpStatus(statusCode: Int, message: String? = null): VortexError {
            return when (statusCode) {
                401 -> Unauthorized(message ?: "Authentication required")
                403 -> Unauthorized(message ?: "Access forbidden")
                404 -> NotFound(message ?: "Resource not found")
                in 400..499 -> InvalidRequest(message ?: "Invalid request (HTTP $statusCode)")
                in 500..599 -> ServerError(statusCode, message ?: "Server error (HTTP $statusCode)")
                else -> Unknown(message ?: "Unexpected error (HTTP $statusCode)")
            }
        }
        
        /**
         * Create VortexError from a generic exception
         */
        fun fromException(e: Throwable): VortexError {
            return when (e) {
                is VortexError -> e
                is java.net.UnknownHostException -> NetworkError("No internet connection", e)
                is java.net.SocketTimeoutException -> NetworkError("Request timed out", e)
                is java.io.IOException -> NetworkError("Network error: ${e.message}", e)
                is kotlinx.serialization.SerializationException -> DecodingError("Failed to parse response: ${e.message}", e)
                else -> Unknown(e.message ?: "Unknown error", e)
            }
        }
    }
}
