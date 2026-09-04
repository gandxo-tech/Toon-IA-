package com.example.ai

/**
 * Exception spécifique pour les erreurs retournées par l'API Gemini.
 * Déclenche automatiquement le gestionnaire de limite de débit (Rate Limit).
 */
class GeminiApiException(val code: Int, override val message: String) : Exception(message) {
    init {
        ApiRateLimitManager.handleApiError(code, message)
    }
}
