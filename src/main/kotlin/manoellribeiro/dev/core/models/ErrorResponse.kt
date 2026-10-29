package manoellribeiro.dev.core.models

import kotlinx.serialization.Serializable

/** Body of every error response: `{ "error": "ValidationFailure", "message": "..." }`. */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)
